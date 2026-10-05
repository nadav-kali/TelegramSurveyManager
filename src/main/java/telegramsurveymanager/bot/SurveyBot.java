package telegramsurveymanager.bot;

import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.message.MaybeInaccessibleMessage;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import telegramsurveymanager.model.CommunityMember;
import telegramsurveymanager.model.Question;
import telegramsurveymanager.model.QuestionResult;
import telegramsurveymanager.model.Survey;
import telegramsurveymanager.model.SurveyParticipant;
import telegramsurveymanager.service.CommunityListener;
import telegramsurveymanager.service.CommunityService;
import telegramsurveymanager.service.SurveyListener;
import telegramsurveymanager.service.SurveyService;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure Telegram I/O translation layer: turns Updates into calls on
 * CommunityService/SurveyService, and turns service events back into Telegram
 * messages. Holds no business rules of its own.
 */
public class SurveyBot implements LongPollingSingleThreadUpdateConsumer, CommunityListener, SurveyListener {

    private static final String JOIN_TRIGGER_HE = "היי"; // "היי"

    private final TelegramClient telegramClient;
    private final CommunityService communityService;
    private final SurveyService surveyService;

    public SurveyBot(String botToken, CommunityService communityService, SurveyService surveyService) {
        this.telegramClient = new OkHttpTelegramClient(botToken);
        this.communityService = communityService;
        this.surveyService = surveyService;
    }

    @Override
    public void consume(Update update) {
        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        } else if (update.hasMessage() && update.getMessage().hasText()) {
            handleMessage(update.getMessage());
        }
    }

    private void handleMessage(Message message) {
        String text = message.getText().trim();
        if (!isJoinTrigger(text)) {
            return;
        }
        User from = message.getFrom();
        String displayName = buildDisplayName(from);
        CommunityService.JoinResult result = communityService.join(from.getId(), displayName, from.getUserName());

        String reply = result == CommunityService.JoinResult.NEWLY_JOINED
                ? BotMessages.welcome(displayName)
                : BotMessages.alreadyMember();
        send(message.getChatId(), reply);
    }

    private boolean isJoinTrigger(String text) {
        return text.equals("/start") || text.equals("Hi") || text.equals(JOIN_TRIGGER_HE);
    }

    private String buildDisplayName(User user) {
        String name = user.getFirstName() == null ? "" : user.getFirstName();
        if (user.getLastName() != null && !user.getLastName().isBlank()) {
            name = name.isBlank() ? user.getLastName() : name + " " + user.getLastName();
        }
        if (name.isBlank()) {
            name = user.getUserName() != null ? user.getUserName() : "משתמש";
        }
        return name;
    }

    private void handleCallback(CallbackQuery query) {
        String[] parts = query.getData().split(":");
        if (parts.length != 3) {
            return;
        }

        String surveyId = parts[0];
        int questionIndex = Integer.parseInt(parts[1]);
        int optionIndex = Integer.parseInt(parts[2]);
        long userId = query.getFrom().getId();

        SurveyService.AnswerResult result = surveyService.recordAnswer(surveyId, userId, questionIndex, optionIndex);

        String toast = switch (result) {
            case SUCCESS -> BotMessages.answerAccepted();
            case ALREADY_ANSWERED -> BotMessages.alreadyAnswered();
            case SURVEY_NOT_ACTIVE, SURVEY_NOT_FOUND, NOT_A_PARTICIPANT -> BotMessages.surveyNotActive();
        };
        answerCallback(query.getId(), toast);

        if (result == SurveyService.AnswerResult.SUCCESS) {
            Survey survey = surveyService.getCurrentSurvey();
            if (survey != null && survey.getId().equals(surveyId)) {
                Question question = survey.getQuestions().get(questionIndex);
                markAnswered(query.getMessage(), questionIndex, survey.getQuestions().size(),
                        question, question.options().get(optionIndex));

                SurveyParticipant participant = survey.getParticipant(userId);
                if (participant != null) {
                    advanceParticipant(survey, participant, userId);
                }
            }
        }
    }

    /** Removes the answered question's buttons and shows what was picked, so a user can never double-tap. */
    private void markAnswered(MaybeInaccessibleMessage message, int questionIndex, int totalQuestions,
                               Question question, String selectedOption) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(message.getChatId())
                .messageId(message.getMessageId())
                .text(BotMessages.questionAnswered(questionIndex + 1, totalQuestions, question.text(), selectedOption))
                .replyMarkup(InlineKeyboardMarkup.builder().keyboard(List.of()).build())
                .build();
        try {
            telegramClient.execute(edit);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void advanceParticipant(Survey survey, SurveyParticipant participant, long chatId) {
        int nextIndex = participant.getAnsweredCount();
        if (nextIndex < survey.getQuestions().size()) {
            sendQuestion(chatId, survey, nextIndex);
        } else {
            send(chatId, BotMessages.surveyCompleted());
        }
    }

    private void sendQuestion(long chatId, Survey survey, int questionIndex) {
        Question question = survey.getQuestions().get(questionIndex);
        List<String> options = question.options();

        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            String callbackData = survey.getId() + ":" + questionIndex + ":" + i;
            InlineKeyboardButton button = InlineKeyboardButton.builder()
                    .text(options.get(i))
                    .callbackData(callbackData)
                    .build();
            rows.add(new InlineKeyboardRow(button));
        }
        InlineKeyboardMarkup markup = InlineKeyboardMarkup.builder().keyboard(rows).build();

        SendMessage sendMessage = SendMessage.builder()
                .chatId(chatId)
                .text(BotMessages.questionMessage(questionIndex + 1, survey.getQuestions().size(), question.text()))
                .replyMarkup(markup)
                .build();
        execute(sendMessage);
    }

    private void answerCallback(String callbackQueryId, String text) {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .showAlert(false)
                .build();
        try {
            telegramClient.execute(answer);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void send(long chatId, String text) {
        execute(SendMessage.builder().chatId(chatId).text(text).build());
    }

    private void execute(SendMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // ---- CommunityListener ----

    @Override
    public void onMemberJoined(CommunityMember newMember, List<CommunityMember> allMembers) {
        String text = BotMessages.newMemberBroadcast(newMember.displayName(), allMembers.size());
        for (CommunityMember member : allMembers) {
            if (member.telegramUserId() != newMember.telegramUserId()) {
                send(member.telegramUserId(), text);
            }
        }
    }

    // ---- SurveyListener ----

    @Override
    public void onSurveyStarted(Survey survey) {
        for (SurveyParticipant participant : survey.getParticipants()) {
            sendQuestion(participant.getMember().telegramUserId(), survey, 0);
        }
    }

    @Override
    public void onReminderDue(Survey survey, List<SurveyParticipant> notCompleted) {
        for (SurveyParticipant participant : notCompleted) {
            send(participant.getMember().telegramUserId(),
                    BotMessages.reminder(participant.getAnsweredCount(), survey.getQuestions().size()));
        }
    }

    @Override
    public void onSurveyClosed(Survey survey, List<QuestionResult> results) {
        for (SurveyParticipant participant : survey.getParticipants()) {
            String text = participant.isCompleted()
                    ? BotMessages.surveyClosedThanksCompleted()
                    : BotMessages.surveyClosedNotCompleted();
            send(participant.getMember().telegramUserId(), text);
        }
    }
}

package telegramsurveymanager.service;

import telegramsurveymanager.model.CommunityMember;
import telegramsurveymanager.model.Question;
import telegramsurveymanager.model.QuestionResult;
import telegramsurveymanager.model.Survey;
import telegramsurveymanager.model.SurveyParticipant;
import telegramsurveymanager.model.SurveyStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Owns the lifecycle of the single active survey: creation, scheduling, answer
 * collection, reminders and auto-close. Has no Telegram or Swing imports - the
 * bot and the GUI both react through {@link SurveyListener}.
 */
public class SurveyService {

    public static final int MIN_COMMUNITY_SIZE = 3;
    public static final Duration SURVEY_DURATION = Duration.ofMinutes(5);
    public static final Duration REMINDER_DELAY = Duration.ofMinutes(3);

    public enum AnswerResult {
        SUCCESS,
        ALREADY_ANSWERED,
        SURVEY_NOT_FOUND,
        SURVEY_NOT_ACTIVE,
        NOT_A_PARTICIPANT
    }

    private final CommunityService communityService;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "survey-scheduler");
        t.setDaemon(true);
        return t;
    });
    private final AtomicInteger idGenerator = new AtomicInteger(1);
    private final List<SurveyListener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    private final Duration surveyDuration;
    private final Duration reminderDelay;

    private volatile Survey currentSurvey;
    private volatile ScheduledFuture<?> reminderTask;
    private volatile ScheduledFuture<?> closeTask;

    public SurveyService(CommunityService communityService) {
        this(communityService, SURVEY_DURATION, REMINDER_DELAY);
    }

    /**
     * Lets tests exercise the 3-minute-reminder / 5-minute-close lifecycle with
     * short durations instead of waiting on the real clock. Production code
     * always uses the single-argument constructor.
     */
    public SurveyService(CommunityService communityService, Duration surveyDuration, Duration reminderDelay) {
        this.communityService = communityService;
        this.surveyDuration = surveyDuration;
        this.reminderDelay = reminderDelay;
    }

    public void addListener(SurveyListener listener) {
        listeners.add(listener);
    }

    public Survey getCurrentSurvey() {
        return currentSurvey;
    }

    public boolean hasActiveOrScheduledSurvey() {
        Survey survey = currentSurvey;
        return survey != null && survey.getStatus() != SurveyStatus.CLOSED;
    }

    /**
     * @return null if a survey can be started now, otherwise a user-facing reason why not.
     */
    public String canStartSurvey() {
        if (hasActiveOrScheduledSurvey()) {
            return "קיים כבר סקר פעיל במערכת. יש להמתין לסיומו לפני התחלת סקר חדש.";
        }
        int size = communityService.getMemberCount();
        if (size < MIN_COMMUNITY_SIZE) {
            return "נדרשים לפחות " + MIN_COMMUNITY_SIZE + " חברי קהילה כדי להתחיל סקר (כרגע בקהילה: " + size + ").";
        }
        return null;
    }

    public Survey createSurvey(List<Question> questions, int delayMinutes) {
        String reason = canStartSurvey();
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
        if (questions.isEmpty() || questions.size() > 3) {
            throw new IllegalArgumentException("סקר חייב להכיל בין שאלה אחת לשלוש שאלות.");
        }
        for (Question question : questions) {
            int optionCount = question.options().size();
            if (optionCount < 2 || optionCount > 4) {
                throw new IllegalArgumentException("לכל שאלה חייבות להיות בין 2 ל-4 אפשרויות תשובה.");
            }
        }

        String id = String.valueOf(idGenerator.getAndIncrement());
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledStart = delayMinutes <= 0 ? now : now.plusMinutes(delayMinutes);
        Survey survey = new Survey(id, questions, now, scheduledStart);
        currentSurvey = survey;

        if (delayMinutes > 0) {
            for (SurveyListener listener : listeners) {
                listener.onSurveyScheduled(survey);
            }
        }
        // Always hand off to the scheduler thread - even for an immediate send - so the
        // caller (typically the Swing EDT, via the "start survey" button) never blocks
        // on the resulting burst of synchronous Telegram HTTP calls.
        scheduler.schedule(() -> startSurveyNow(survey), Math.max(delayMinutes, 0), TimeUnit.MINUTES);
        return survey;
    }

    /**
     * Convenience for synchronous callers (and tests): creates an immediate survey
     * and blocks until it has actually started - i.e. participants are snapshotted
     * and {@link SurveyListener#onSurveyStarted} has fired.
     */
    public Survey createSurveyAndAwaitStart(List<Question> questions) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        SurveyListener waiter = new SurveyListener() {
            @Override
            public void onSurveyStarted(Survey s) {
                latch.countDown();
            }
        };
        addListener(waiter);
        try {
            Survey survey = createSurvey(questions, 0);
            latch.await(5, TimeUnit.SECONDS);
            return survey;
        } finally {
            listeners.remove(waiter);
        }
    }

    private void startSurveyNow(Survey survey) {
        synchronized (lock) {
            if (survey.getStatus() != SurveyStatus.SCHEDULED) {
                return;
            }
            for (CommunityMember member : communityService.getMembers()) {
                survey.addParticipant(new SurveyParticipant(member, survey.getQuestions().size()));
            }
            LocalDateTime startedAt = LocalDateTime.now();
            survey.setStartedAt(startedAt);
            survey.setClosesAt(startedAt.plus(surveyDuration));
            survey.setStatus(SurveyStatus.ACTIVE);
        }

        for (SurveyListener listener : listeners) {
            listener.onSurveyStarted(survey);
        }

        reminderTask = scheduler.schedule(() -> sendReminders(survey),
                reminderDelay.toMillis(), TimeUnit.MILLISECONDS);
        closeTask = scheduler.schedule(() -> closeSurvey(survey, false),
                surveyDuration.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void sendReminders(Survey survey) {
        if (survey.getStatus() != SurveyStatus.ACTIVE) {
            return;
        }
        List<SurveyParticipant> notCompleted = new ArrayList<>();
        for (SurveyParticipant participant : survey.getParticipants()) {
            if (!participant.isCompleted()) {
                notCompleted.add(participant);
            }
        }
        if (!notCompleted.isEmpty()) {
            for (SurveyListener listener : listeners) {
                listener.onReminderDue(survey, notCompleted);
            }
        }
    }

    public AnswerResult recordAnswer(String surveyId, long telegramUserId, int questionIndex, int optionIndex) {
        Survey survey = currentSurvey;
        if (survey == null || !survey.getId().equals(surveyId)) {
            return AnswerResult.SURVEY_NOT_FOUND;
        }
        if (survey.getStatus() != SurveyStatus.ACTIVE) {
            return AnswerResult.SURVEY_NOT_ACTIVE;
        }
        SurveyParticipant participant = survey.getParticipant(telegramUserId);
        if (participant == null) {
            return AnswerResult.NOT_A_PARTICIPANT;
        }
        if (!participant.recordAnswer(questionIndex, optionIndex)) {
            return AnswerResult.ALREADY_ANSWERED;
        }

        for (SurveyListener listener : listeners) {
            listener.onProgressChanged(survey, participant);
        }

        if (survey.allCompleted()) {
            closeSurvey(survey, true);
        }
        return AnswerResult.SUCCESS;
    }

    private void closeSurvey(Survey survey, boolean early) {
        synchronized (lock) {
            if (survey.getStatus() == SurveyStatus.CLOSED) {
                return;
            }
            survey.setStatus(SurveyStatus.CLOSED);
            survey.setClosedAt(LocalDateTime.now());
        }

        if (reminderTask != null) {
            reminderTask.cancel(false);
        }
        if (early && closeTask != null) {
            closeTask.cancel(false);
        }

        List<QuestionResult> results = computeResults(survey);
        for (SurveyListener listener : listeners) {
            listener.onSurveyClosed(survey, results);
        }
    }

    private List<QuestionResult> computeResults(Survey survey) {
        List<QuestionResult> results = new ArrayList<>();
        List<Question> questions = survey.getQuestions();

        for (int questionIndex = 0; questionIndex < questions.size(); questionIndex++) {
            Question question = questions.get(questionIndex);
            int[] counts = new int[question.options().size()];

            for (SurveyParticipant participant : survey.getParticipants()) {
                Integer answer = participant.getAnswer(questionIndex);
                if (answer != null && answer >= 0 && answer < counts.length) {
                    counts[answer]++;
                }
            }

            int total = 0;
            for (int count : counts) {
                total += count;
            }

            List<QuestionResult.OptionResult> optionResults = new ArrayList<>();
            for (int optionIndex = 0; optionIndex < question.options().size(); optionIndex++) {
                double percentage = total == 0 ? 0.0 : (counts[optionIndex] * 100.0 / total);
                optionResults.add(new QuestionResult.OptionResult(
                        question.options().get(optionIndex), counts[optionIndex], percentage));
            }
            optionResults.sort((a, b) -> Integer.compare(b.votes(), a.votes()));

            results.add(new QuestionResult(question.text(), optionResults));
        }
        return results;
    }

    /** Stops the internal scheduler. Production code never needs this (daemon thread); tests use it to clean up. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}

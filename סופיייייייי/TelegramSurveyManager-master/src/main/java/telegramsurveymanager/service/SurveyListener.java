package telegramsurveymanager.service;

import telegramsurveymanager.model.QuestionResult;
import telegramsurveymanager.model.Survey;
import telegramsurveymanager.model.SurveyParticipant;

import java.util.List;

/**
 * Both the bot and the GUI implement this to react to survey lifecycle events
 * without knowing about each other.
 */
public interface SurveyListener {

    default void onSurveyScheduled(Survey survey) {
    }

    default void onSurveyStarted(Survey survey) {
    }

    default void onProgressChanged(Survey survey, SurveyParticipant participant) {
    }

    default void onReminderDue(Survey survey, List<SurveyParticipant> notCompleted) {
    }

    default void onSurveyClosed(Survey survey, List<QuestionResult> results) {
    }
}

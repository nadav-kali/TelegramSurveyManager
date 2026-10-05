package telegramsurveymanager.service.ai;

import telegramsurveymanager.model.Question;

import java.util.List;

public interface AiSurveyGenerator {

    List<Question> generate(String topic, int questionCount, int optionsPerQuestion) throws AiGenerationException;
}

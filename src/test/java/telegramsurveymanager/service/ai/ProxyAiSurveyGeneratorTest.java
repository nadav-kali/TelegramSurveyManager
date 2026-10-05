package telegramsurveymanager.service.ai;

import org.junit.jupiter.api.Test;
import telegramsurveymanager.model.Question;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the JSON parsing path with no network call - the real proxy call was
 * verified manually against the live endpoint during development.
 */
class ProxyAiSurveyGeneratorTest {

    private final ProxyAiSurveyGenerator generator = new ProxyAiSurveyGenerator("https://example.invalid", "token");

    @Test
    void parsesCleanJson() throws AiGenerationException {
        String raw = "{\"questions\":[{\"text\":\"Favorite language?\",\"options\":[\"Java\",\"Python\"]}]}";

        List<Question> questions = generator.parseQuestions(raw);

        assertEquals(1, questions.size());
        assertEquals("Favorite language?", questions.get(0).text());
        assertEquals(List.of("Java", "Python"), questions.get(0).options());
    }

    @Test
    void parsesJsonEvenWithSurroundingNoise() throws AiGenerationException {
        String raw = "Sure, here you go:\n```json\n" +
                "{\"questions\":[{\"text\":\"Q?\",\"options\":[\"A\",\"B\",\"C\"]}]}" +
                "\n```\nHope that helps!";

        List<Question> questions = generator.parseQuestions(raw);

        assertEquals(1, questions.size());
        assertEquals(3, questions.get(0).options().size());
    }

    @Test
    void parsesMultipleQuestions() throws AiGenerationException {
        String raw = "{\"questions\":[" +
                "{\"text\":\"Q1\",\"options\":[\"A\",\"B\"]}," +
                "{\"text\":\"Q2\",\"options\":[\"C\",\"D\",\"E\"]}" +
                "]}";

        List<Question> questions = generator.parseQuestions(raw);

        assertEquals(2, questions.size());
        assertEquals("Q1", questions.get(0).text());
        assertEquals("Q2", questions.get(1).text());
    }

    @Test
    void rejectsNonJsonText() {
        assertThrows(AiGenerationException.class, () -> generator.parseQuestions("sorry, I cannot help with that"));
    }

    @Test
    void rejectsTooManyQuestions() {
        String raw = "{\"questions\":[" +
                "{\"text\":\"Q1\",\"options\":[\"A\",\"B\"]}," +
                "{\"text\":\"Q2\",\"options\":[\"A\",\"B\"]}," +
                "{\"text\":\"Q3\",\"options\":[\"A\",\"B\"]}," +
                "{\"text\":\"Q4\",\"options\":[\"A\",\"B\"]}" +
                "]}";

        assertThrows(AiGenerationException.class, () -> generator.parseQuestions(raw));
    }

    @Test
    void rejectsInvalidOptionCount() {
        String tooFew = "{\"questions\":[{\"text\":\"Q1\",\"options\":[\"OnlyOne\"]}]}";
        String tooMany = "{\"questions\":[{\"text\":\"Q1\",\"options\":[\"A\",\"B\",\"C\",\"D\",\"E\"]}]}";

        assertThrows(AiGenerationException.class, () -> generator.parseQuestions(tooFew));
        assertThrows(AiGenerationException.class, () -> generator.parseQuestions(tooMany));
    }

    @Test
    void rejectsEmptyQuestionList() {
        assertThrows(AiGenerationException.class, () -> generator.parseQuestions("{\"questions\":[]}"));
    }
}

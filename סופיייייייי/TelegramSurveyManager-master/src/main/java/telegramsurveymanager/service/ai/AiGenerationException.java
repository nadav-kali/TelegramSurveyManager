package telegramsurveymanager.service.ai;

public class AiGenerationException extends Exception {

    public AiGenerationException(String message) {
        super(message);
    }

    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package telegramsurveymanager.model;

import java.util.List;

public record Question(String text, List<String> options) {
    public Question {
        options = List.copyOf(options);
    }
}

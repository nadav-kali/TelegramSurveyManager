package telegramsurveymanager.model;

import java.util.List;

public record QuestionResult(String questionText, List<OptionResult> options) {

    public record OptionResult(String text, int votes, double percentage) {
    }
}

package telegramsurveymanager.model;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SurveyParticipant {

    private final CommunityMember member;
    private final int questionCount;
    private final Map<Integer, Integer> answers = new ConcurrentHashMap<>();

    public SurveyParticipant(CommunityMember member, int questionCount) {
        this.member = member;
        this.questionCount = questionCount;
    }

    public CommunityMember getMember() {
        return member;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public synchronized boolean recordAnswer(int questionIndex, int optionIndex) {
        if (answers.containsKey(questionIndex)) {
            return false;
        }
        answers.put(questionIndex, optionIndex);
        return true;
    }

    public boolean hasAnswered(int questionIndex) {
        return answers.containsKey(questionIndex);
    }

    public Integer getAnswer(int questionIndex) {
        return answers.get(questionIndex);
    }

    public int getAnsweredCount() {
        return answers.size();
    }

    public boolean isCompleted() {
        return answers.size() >= questionCount;
    }
}

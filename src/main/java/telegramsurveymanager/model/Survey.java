package telegramsurveymanager.model;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Survey {

    private final String id;
    private final List<Question> questions;
    private final LocalDateTime createdAt;
    private final LocalDateTime scheduledStartAt;
    private final Map<Long, SurveyParticipant> participants = new ConcurrentHashMap<>();

    private volatile SurveyStatus status = SurveyStatus.SCHEDULED;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime closesAt;
    private volatile LocalDateTime closedAt;

    public Survey(String id, List<Question> questions, LocalDateTime createdAt, LocalDateTime scheduledStartAt) {
        this.id = id;
        this.questions = List.copyOf(questions);
        this.createdAt = createdAt;
        this.scheduledStartAt = scheduledStartAt;
    }

    public String getId() {
        return id;
    }

    public List<Question> getQuestions() {
        return questions;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getScheduledStartAt() {
        return scheduledStartAt;
    }

    public SurveyStatus getStatus() {
        return status;
    }

    public void setStatus(SurveyStatus status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getClosesAt() {
        return closesAt;
    }

    public void setClosesAt(LocalDateTime closesAt) {
        this.closesAt = closesAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public void addParticipant(SurveyParticipant participant) {
        participants.put(participant.getMember().telegramUserId(), participant);
    }

    public SurveyParticipant getParticipant(long telegramUserId) {
        return participants.get(telegramUserId);
    }

    public Collection<SurveyParticipant> getParticipants() {
        return participants.values();
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public boolean allCompleted() {
        if (participants.isEmpty()) {
            return false;
        }
        for (SurveyParticipant participant : participants.values()) {
            if (!participant.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    public int getCompletedCount() {
        int count = 0;
        for (SurveyParticipant participant : participants.values()) {
            if (participant.isCompleted()) {
                count++;
            }
        }
        return count;
    }
}

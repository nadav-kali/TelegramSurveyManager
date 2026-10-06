package telegramsurveymanager.model;

import java.time.LocalDateTime;

public record CommunityMember(long telegramUserId, String displayName, String telegramUsername,
                               LocalDateTime joinedAt) {
}

package telegramsurveymanager.service;

import telegramsurveymanager.model.CommunityMember;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Owns the global community roster. Knows nothing about Telegram or Swing -
 * callers (bot / GUI) subscribe via {@link CommunityListener} to react to changes.
 */
public class CommunityService {

    public enum JoinResult {
        NEWLY_JOINED,
        ALREADY_MEMBER
    }

    private final Map<Long, CommunityMember> membersById = new LinkedHashMap<>();
    private final List<CommunityListener> listeners = new CopyOnWriteArrayList<>();
    private final Object lock = new Object();

    public void addListener(CommunityListener listener) {
        listeners.add(listener);
    }

    public JoinResult join(long telegramUserId, String displayName, String telegramUsername) {
        CommunityMember newMember;
        List<CommunityMember> snapshot;
        synchronized (lock) {
            if (membersById.containsKey(telegramUserId)) {
                return JoinResult.ALREADY_MEMBER;
            }
            newMember = new CommunityMember(telegramUserId, displayName, telegramUsername, LocalDateTime.now());
            membersById.put(telegramUserId, newMember);
            snapshot = List.copyOf(membersById.values());
        }
        for (CommunityListener listener : listeners) {
            listener.onMemberJoined(newMember, snapshot);
        }
        return JoinResult.NEWLY_JOINED;
    }

    public List<CommunityMember> getMembers() {
        synchronized (lock) {
            return List.copyOf(membersById.values());
        }
    }

    public int getMemberCount() {
        synchronized (lock) {
            return membersById.size();
        }
    }
}

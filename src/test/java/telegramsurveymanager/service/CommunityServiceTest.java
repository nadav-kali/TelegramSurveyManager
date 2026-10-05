package telegramsurveymanager.service;

import org.junit.jupiter.api.Test;
import telegramsurveymanager.model.CommunityMember;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommunityServiceTest {

    @Test
    void newMemberIsAddedAndListenersAreNotified() {
        CommunityService service = new CommunityService();
        List<CommunityMember> notified = new ArrayList<>();
        service.addListener((newMember, allMembers) -> notified.add(newMember));

        CommunityService.JoinResult result = service.join(1L, "Danny", "danny_tg");

        assertEquals(CommunityService.JoinResult.NEWLY_JOINED, result);
        assertEquals(1, service.getMemberCount());
        assertEquals(1, notified.size());
        assertEquals("Danny", notified.get(0).displayName());
    }

    @Test
    void joiningTwiceDoesNotDuplicateOrRenotify() {
        CommunityService service = new CommunityService();
        List<CommunityMember> notified = new ArrayList<>();
        service.addListener((newMember, allMembers) -> notified.add(newMember));

        service.join(1L, "Danny", "danny_tg");
        CommunityService.JoinResult second = service.join(1L, "Danny", "danny_tg");

        assertEquals(CommunityService.JoinResult.ALREADY_MEMBER, second);
        assertEquals(1, service.getMemberCount());
        assertEquals(1, notified.size(), "listener must fire only for the real join, not the repeat");
    }

    @Test
    void joinBroadcastIncludesUpdatedCommunitySize() {
        CommunityService service = new CommunityService();
        List<Integer> sizesSeen = new ArrayList<>();
        service.addListener((newMember, allMembers) -> sizesSeen.add(allMembers.size()));

        service.join(1L, "Danny", "danny_tg");
        service.join(2L, "Yael", "yael_tg");
        service.join(3L, "Uri", null);

        assertEquals(List.of(1, 2, 3), sizesSeen);
    }

    @Test
    void membersArePreservedInJoinOrder() {
        CommunityService service = new CommunityService();
        service.join(1L, "Danny", "danny_tg");
        service.join(2L, "Yael", "yael_tg");
        service.join(3L, "Uri", null);

        List<CommunityMember> members = service.getMembers();
        assertEquals(3, members.size());
        assertEquals("Danny", members.get(0).displayName());
        assertEquals("Yael", members.get(1).displayName());
        assertEquals("Uri", members.get(2).displayName());
        assertTrue(members.get(2).telegramUsername() == null, "member joined without a telegram username");
    }
}

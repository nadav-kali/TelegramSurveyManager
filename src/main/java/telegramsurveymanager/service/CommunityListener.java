package telegramsurveymanager.service;

import telegramsurveymanager.model.CommunityMember;

import java.util.List;

public interface CommunityListener {
    void onMemberJoined(CommunityMember newMember, List<CommunityMember> allMembers);
}

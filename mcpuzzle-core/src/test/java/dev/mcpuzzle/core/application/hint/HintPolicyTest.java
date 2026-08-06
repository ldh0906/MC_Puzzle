package dev.mcpuzzle.core.application.hint;

import dev.mcpuzzle.core.domain.HintProgress;
import dev.mcpuzzle.core.domain.PartyRoster;
import dev.mcpuzzle.core.domain.SessionId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HintPolicyTest {
    @Test
    void memberRequestsNextTierAndOnlyLeaderCanConfirm() {
        HintPolicy policy = new HintPolicy();
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        PartyRoster roster = new PartyRoster(leader, List.of(leader, member));
        HintContextId context = new HintContextId(SessionId.random(), 3, 0);
        HintProgress progress = HintProgress.empty();

        HintOutcome request = policy.requestNext(context, member, roster, progress);
        assertEquals(HintOutcomeType.REQUESTED_LEADER_CONFIRMATION, request.type());
        assertEquals(1, request.tier().orElseThrow());
        assertEquals(HintOutcomeType.NOT_LEADER, policy.confirm(
                context,
                member,
                roster,
                progress,
                true
        ).type());

        HintOutcome confirmed = policy.confirm(context, leader, roster, progress, true);
        assertEquals(HintOutcomeType.UNLOCKED, confirmed.type());
        assertEquals(1, confirmed.progress().totalUnlocked());
        assertEquals(HintOutcomeType.REQUESTED_LEADER_CONFIRMATION, policy.requestNext(
                context,
                member,
                roster,
                confirmed.progress()
        ).type());
        assertEquals(2, policy.confirm(
                context,
                leader,
                roster,
                confirmed.progress(),
                true
        ).tier().orElseThrow());
    }

    @Test
    void repeatedViewingDoesNotChangeUniqueHintCountAndDeclineDoesNotUnlock() {
        HintPolicy policy = new HintPolicy();
        UUID leader = UUID.randomUUID();
        PartyRoster roster = new PartyRoster(leader, List.of(leader));
        HintContextId context = new HintContextId(SessionId.random(), 1, 0);
        HintProgress unlocked = HintProgress.empty().unlock(1, 1);

        HintOutcome firstView = policy.viewUnlocked(context, leader, roster, unlocked, 1);
        HintOutcome secondView = policy.viewUnlocked(context, leader, roster, firstView.progress(), 1);
        assertEquals(HintOutcomeType.VIEWED_UNLOCKED, firstView.type());
        assertEquals(HintOutcomeType.VIEWED_UNLOCKED, secondView.type());
        assertEquals(1, secondView.progress().totalUnlocked());

        policy.requestNext(context, leader, roster, unlocked);
        HintOutcome declined = policy.confirm(context, leader, roster, unlocked, false);
        assertEquals(HintOutcomeType.DECLINED, declined.type());
        assertEquals(1, declined.progress().totalUnlocked());
    }

    @Test
    void pendingRequestIsIdempotentAndOldAttemptIsClearedOnReset() {
        HintPolicy policy = new HintPolicy();
        UUID leader = UUID.randomUUID();
        PartyRoster roster = new PartyRoster(leader, List.of(leader));
        SessionId sessionId = SessionId.random();
        HintContextId oldAttempt = new HintContextId(sessionId, 2, 4);
        HintContextId newAttempt = new HintContextId(sessionId, 2, 5);

        policy.requestNext(oldAttempt, leader, roster, HintProgress.empty());
        assertEquals(HintOutcomeType.ALREADY_PENDING, policy.requestNext(
                oldAttempt,
                leader,
                roster,
                HintProgress.empty()
        ).type());
        policy.resetRoomAttempt(newAttempt);

        assertEquals(HintOutcomeType.NO_PENDING_REQUEST, policy.confirm(
                oldAttempt,
                leader,
                roster,
                HintProgress.empty(),
                true
        ).type());
        assertEquals(0, policy.pendingCount());
    }

    @Test
    void stopsAfterThreeTiersAndRejectsNonMembers() {
        HintPolicy policy = new HintPolicy();
        UUID leader = UUID.randomUUID();
        PartyRoster roster = new PartyRoster(leader, List.of(leader));
        HintContextId context = new HintContextId(SessionId.random(), 1, 0);
        HintProgress complete = HintProgress.empty().unlock(1, 1).unlock(1, 2).unlock(1, 3);

        assertEquals(HintOutcomeType.ALL_TIERS_UNLOCKED, policy.requestNext(
                context,
                leader,
                roster,
                complete
        ).type());
        assertEquals(HintOutcomeType.NOT_MEMBER, policy.requestNext(
                context,
                UUID.randomUUID(),
                roster,
                HintProgress.empty()
        ).type());
    }
}

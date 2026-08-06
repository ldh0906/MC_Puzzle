package dev.mcpuzzle.core.application.hint;

import dev.mcpuzzle.core.domain.HintProgress;
import dev.mcpuzzle.core.domain.PartyRoster;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Thread-safe pending-confirmation policy for staged party hints. */
public final class HintPolicy {
    private final Map<HintContextId, PendingHint> pending = new HashMap<>();

    public synchronized HintOutcome requestNext(
            HintContextId context,
            UUID requesterId,
            PartyRoster roster,
            HintProgress progress
    ) {
        requireInputs(context, requesterId, roster, progress);
        if (!roster.contains(requesterId)) {
            return HintOutcome.withoutTier(HintOutcomeType.NOT_MEMBER, progress);
        }
        int nextTier = nextTier(context.room(), progress);
        if (nextTier < 0) {
            return HintOutcome.withoutTier(HintOutcomeType.ALL_TIERS_UNLOCKED, progress);
        }
        PendingHint existing = pending.get(context);
        if (existing != null) {
            return HintOutcome.of(HintOutcomeType.ALREADY_PENDING, existing.tier(), progress);
        }
        pending.put(context, new PendingHint(nextTier, requesterId));
        return HintOutcome.of(HintOutcomeType.REQUESTED_LEADER_CONFIRMATION, nextTier, progress);
    }

    public synchronized HintOutcome confirm(
            HintContextId context,
            UUID actorId,
            PartyRoster roster,
            HintProgress progress,
            boolean approved
    ) {
        requireInputs(context, actorId, roster, progress);
        if (!roster.leaderId().equals(actorId)) {
            return HintOutcome.withoutTier(HintOutcomeType.NOT_LEADER, progress);
        }
        PendingHint request = pending.get(context);
        if (request == null) {
            return HintOutcome.withoutTier(HintOutcomeType.NO_PENDING_REQUEST, progress);
        }
        int currentNext = nextTier(context.room(), progress);
        if (currentNext != request.tier()) {
            pending.remove(context);
            return HintOutcome.of(HintOutcomeType.STALE_REQUEST, request.tier(), progress);
        }
        pending.remove(context);
        if (!approved) {
            return HintOutcome.of(HintOutcomeType.DECLINED, request.tier(), progress);
        }
        HintProgress updated = progress.unlock(context.room(), request.tier());
        return HintOutcome.of(HintOutcomeType.UNLOCKED, request.tier(), updated);
    }

    public synchronized HintOutcome viewUnlocked(
            HintContextId context,
            UUID viewerId,
            PartyRoster roster,
            HintProgress progress,
            int tier
    ) {
        requireInputs(context, viewerId, roster, progress);
        if (!roster.contains(viewerId)) {
            return HintOutcome.of(HintOutcomeType.NOT_MEMBER, tier, progress);
        }
        if (!progress.isUnlocked(context.room(), tier)) {
            return HintOutcome.of(HintOutcomeType.TIER_NOT_UNLOCKED, tier, progress);
        }
        return HintOutcome.of(HintOutcomeType.VIEWED_UNLOCKED, tier, progress);
    }

    public synchronized void resetRoomAttempt(HintContextId currentContext) {
        Objects.requireNonNull(currentContext, "currentContext");
        pending.keySet().removeIf(context ->
                context.sessionId().equals(currentContext.sessionId())
                        && context.room() == currentContext.room()
                        && !context.equals(currentContext)
        );
    }

    public synchronized void clearSession(dev.mcpuzzle.core.domain.SessionId sessionId) {
        Objects.requireNonNull(sessionId, "sessionId");
        pending.keySet().removeIf(context -> context.sessionId().equals(sessionId));
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    private static int nextTier(int room, HintProgress progress) {
        for (int tier = HintProgress.MIN_TIER; tier <= HintProgress.MAX_TIER; tier++) {
            if (!progress.isUnlocked(room, tier)) {
                return tier;
            }
        }
        return -1;
    }

    private static void requireInputs(
            HintContextId context,
            UUID playerId,
            PartyRoster roster,
            HintProgress progress
    ) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(progress, "progress");
    }

    private record PendingHint(int tier, UUID requesterId) {
    }
}

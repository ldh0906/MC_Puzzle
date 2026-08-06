package dev.mcpuzzle.core.application.hint;

public enum HintOutcomeType {
    REQUESTED_LEADER_CONFIRMATION,
    ALREADY_PENDING,
    UNLOCKED,
    DECLINED,
    VIEWED_UNLOCKED,
    TIER_NOT_UNLOCKED,
    ALL_TIERS_UNLOCKED,
    NOT_MEMBER,
    NOT_LEADER,
    NO_PENDING_REQUEST,
    STALE_REQUEST
}

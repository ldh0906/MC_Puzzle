package dev.mcpuzzle.core.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Immutable record of hint tiers unlocked in each room. */
public final class HintProgress {
    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 3;

    private static final HintProgress EMPTY = new HintProgress(Map.of());

    private final Map<Integer, Set<Integer>> unlockedByRoom;

    public HintProgress(Map<Integer, ? extends Set<Integer>> unlockedByRoom) {
        Objects.requireNonNull(unlockedByRoom, "unlockedByRoom");
        Map<Integer, Set<Integer>> copy = new TreeMap<>();
        unlockedByRoom.forEach((room, tiers) -> {
            validateRoom(room);
            Objects.requireNonNull(tiers, "hint tiers");
            Set<Integer> tierCopy = new TreeSet<>();
            for (Integer tier : tiers) {
                validateTier(tier);
                tierCopy.add(tier);
            }
            if (!tierCopy.isEmpty()) {
                copy.put(room, Collections.unmodifiableSet(tierCopy));
            }
        });
        this.unlockedByRoom = Collections.unmodifiableMap(copy);
    }

    public static HintProgress empty() {
        return EMPTY;
    }

    public HintProgress unlock(int room, int tier) {
        validateRoom(room);
        validateTier(tier);
        if (isUnlocked(room, tier)) {
            return this;
        }
        Map<Integer, Set<Integer>> updated = new TreeMap<>(unlockedByRoom);
        Set<Integer> tiers = new TreeSet<>(updated.getOrDefault(room, Set.of()));
        tiers.add(tier);
        updated.put(room, tiers);
        return new HintProgress(updated);
    }

    public boolean isUnlocked(int room, int tier) {
        validateRoom(room);
        validateTier(tier);
        return unlockedByRoom.getOrDefault(room, Set.of()).contains(tier);
    }

    public int totalUnlocked() {
        return unlockedByRoom.values().stream().mapToInt(Set::size).sum();
    }

    public Map<Integer, Set<Integer>> unlockedByRoom() {
        return unlockedByRoom;
    }

    public void validateAgainstRoomCount(int roomCount) {
        if (roomCount < 1) {
            throw new IllegalArgumentException("Room count must be positive");
        }
        if (unlockedByRoom.keySet().stream().anyMatch(room -> room > roomCount)) {
            throw new IllegalArgumentException("Hint progress references a room outside the maze");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof HintProgress progress
                && unlockedByRoom.equals(progress.unlockedByRoom);
    }

    @Override
    public int hashCode() {
        return unlockedByRoom.hashCode();
    }

    @Override
    public String toString() {
        return unlockedByRoom.toString();
    }

    private static void validateRoom(Integer room) {
        Objects.requireNonNull(room, "room");
        if (room < 1) {
            throw new IllegalArgumentException("Room number must be positive");
        }
    }

    private static void validateTier(Integer tier) {
        Objects.requireNonNull(tier, "tier");
        if (tier < MIN_TIER || tier > MAX_TIER) {
            throw new IllegalArgumentException("Hint tier must be between 1 and 3");
        }
    }
}

package dev.mcpuzzle.paper.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record MazeMenuAction(Type type, List<String> arguments) {
    private static final String SEPARATOR = "|";

    public MazeMenuAction {
        Objects.requireNonNull(type, "type");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        if (arguments.stream().anyMatch(value -> value == null || value.contains(SEPARATOR))) {
            throw new IllegalArgumentException("Menu arguments must not contain '|'");
        }
    }

    public static MazeMenuAction of(Type type, String... arguments) {
        return new MazeMenuAction(type, List.of(arguments));
    }

    public static MazeMenuAction confirmation(MazeMenuAction requested) {
        Objects.requireNonNull(requested, "requested");
        return MazeMenuAction.of(Type.CONFIRM,
                Stream.concat(Stream.of(requested.type.name()), requested.arguments.stream()).toArray(String[]::new));
    }

    public String encode() {
        return Stream.concat(Stream.of(type.name()), arguments.stream())
                .collect(Collectors.joining(SEPARATOR));
    }

    public static Optional<MazeMenuAction> decode(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String[] values = raw.split("\\|", -1);
        try {
            return Optional.of(new MazeMenuAction(Type.valueOf(values[0]),
                    Arrays.asList(values).subList(1, values.length)));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    public OptionalInt integer(int index, int min, int max) {
        if (index < 0 || index >= arguments.size()) return OptionalInt.empty();
        try {
            int value = Integer.parseInt(arguments.get(index));
            return value >= min && value <= max ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException failure) {
            return OptionalInt.empty();
        }
    }

    public int requireInteger(int index, int min, int max) {
        return integer(index, min, max).orElseThrow(
                () -> new IllegalArgumentException("Missing or invalid numeric menu argument"));
    }

    public Optional<UUID> uuid(int index) {
        if (index < 0 || index >= arguments.size()) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(arguments.get(index)));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    public Optional<MazeMenuAction> confirmedAction() {
        if (type != Type.CONFIRM || arguments.isEmpty()) return Optional.empty();
        try {
            return Optional.of(MazeMenuAction.of(Type.valueOf(arguments.get(0)),
                    arguments.subList(1, arguments.size()).toArray(String[]::new)));
        } catch (IllegalArgumentException failure) {
            return Optional.empty();
        }
    }

    public enum Type {
        MAIN, CLOSE, PARTY, PARTY_CREATE, PARTY_INVITE_LIST, PARTY_INVITE,
        PARTY_INVITATIONS, PARTY_ACCEPT, PARTY_DECLINE, PARTY_KICK,
        PARTY_LEAVE, PARTY_DISBAND, SAVES, SAVE_START, SAVE_RESUME,
        SAVE_DELETE, HINTS, HINT_REQUEST, HINT_VIEW, HINT_APPROVE,
        HINT_DECLINE, LEADERBOARD, ANSWER_PROMPT, RUN_LEAVE, QUEUE_CANCEL,
        ADMIN, ADMIN_RELOAD, ADMIN_VERIFY_WORLD, ADMIN_WAND,
        ADMIN_SELECTION, ADMIN_PLAYERS, ADMIN_SAVES, ADMIN_DELETE,
        ADMIN_TRANSFER_PICK, ADMIN_TRANSFER, CONFIRM, CANCEL, PAGE
    }
}

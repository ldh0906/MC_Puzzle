package dev.mcpuzzle.paper.gui;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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

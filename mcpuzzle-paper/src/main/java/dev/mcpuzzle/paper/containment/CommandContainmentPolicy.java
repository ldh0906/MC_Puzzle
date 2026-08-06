package dev.mcpuzzle.paper.containment;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class CommandContainmentPolicy {
    private final Set<String> allowedCommands;
    private final boolean operatorBypass;

    public CommandContainmentPolicy(Set<String> allowedCommands, boolean operatorBypass) {
        Objects.requireNonNull(allowedCommands, "allowedCommands");
        this.allowedCommands = allowedCommands.stream()
                .map(CommandContainmentPolicy::normalizeLabel)
                .collect(Collectors.toUnmodifiableSet());
        this.operatorBypass = operatorBypass;
    }

    public static CommandContainmentPolicy mazeOnly() {
        return new CommandContainmentPolicy(Set.of("maze", "미궁"), false);
    }

    public boolean isAllowed(String commandLine, boolean operator) {
        if (operatorBypass && operator) {
            return true;
        }
        String trimmed = commandLine == null ? "" : commandLine.trim();
        if (trimmed.startsWith("/")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.isBlank()) {
            return false;
        }
        String label = trimmed.split("\\s+", 2)[0];
        return allowedCommands.contains(normalizeLabel(label));
    }

    public boolean isVisibleLabel(String label, boolean operator) {
        return operatorBypass && operator || allowedCommands.contains(normalizeLabel(label));
    }

    private static String normalizeLabel(String label) {
        String normalized = label.toLowerCase(Locale.ROOT).trim();
        int namespaceSeparator = normalized.indexOf(':');
        return namespaceSeparator < 0 ? normalized : normalized.substring(namespaceSeparator + 1);
    }
}

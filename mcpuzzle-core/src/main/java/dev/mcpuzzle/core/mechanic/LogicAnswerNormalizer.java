package dev.mcpuzzle.core.mechanic;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;

/** Stable answer normalization shared by map validation, runtime, and tests. */
public final class LogicAnswerNormalizer {
    private LogicAnswerNormalizer() { }

    public static String normalize(String value) {
        String compatible = Normalizer.normalize(Objects.requireNonNull(value, "value"), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(compatible.length());
        compatible.codePoints().filter(Character::isLetterOrDigit).forEach(result::appendCodePoint);
        return result.toString();
    }
}

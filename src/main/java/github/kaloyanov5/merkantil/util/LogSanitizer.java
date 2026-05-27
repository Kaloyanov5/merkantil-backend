package github.kaloyanov5.merkantil.util;

/**
 * Strips CR / LF (and stray control bytes) from values that may be reflected
 * into log lines. Prevents log injection / forging via user-supplied strings.
 */
public final class LogSanitizer {

    private LogSanitizer() {}

    /**
     * Returns {@code null} unchanged. Otherwise replaces CR, LF and other
     * ASCII control bytes with {@code _} so a multi-line input cannot forge
     * extra log entries.
     */
    public static String safe(String value) {
        if (value == null) return null;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(c < 0x20 || c == 0x7F ? '_' : c);
        }
        return out.toString();
    }
}

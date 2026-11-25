package com.benggri.example.core.common;

/**
 * Rejects text that must never reach SQL: PostgreSQL cannot store U+0000 (the driver throws, which would
 * surface as a server error), and other C0 control characters are never legitimate in these fields.
 * Tab, newline and carriage return are allowed. Messages are generic and never contain the value.
 */
public final class TextGuard {

    private static final String MESSAGE = "Invalid input";

    private TextGuard() {
    }

    /** No-op for null. */
    public static void requireClean(String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < 0x20 && c != '\t' && c != '\n' && c != '\r') {
                throw new InvalidRequestException(MESSAGE);
            }
        }
    }

    /** Length in UTF-16 chars; no-op for null. */
    public static void requireMaxLength(String value, int max) {
        if (value != null && value.length() > max) {
            throw new InvalidRequestException(MESSAGE);
        }
    }
}

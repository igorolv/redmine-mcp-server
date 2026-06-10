package ru.it_spectrum.ai.redmine.mcp.focus;

import java.util.Locale;

public enum ResponseFocus {
    DEFAULT,
    IMPLEMENTATION,
    TIMELINE,
    FULL,
    CHANGESETS;

    public static ResponseFocus from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "default" -> DEFAULT;
            case "implementation" -> IMPLEMENTATION;
            case "timeline" -> TIMELINE;
            case "full" -> FULL;
            case "changesets" -> CHANGESETS;
            default -> throw new IllegalArgumentException(
                    "Unsupported focus '%s'. Supported values: default, implementation, timeline, full, changesets."
                            .formatted(value));
        };
    }
}

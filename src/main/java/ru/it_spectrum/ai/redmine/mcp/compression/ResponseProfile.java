package ru.it_spectrum.ai.redmine.mcp.compression;

import java.util.Locale;

public enum ResponseProfile {
    DEFAULT,
    REVIEW,
    FULL;

    public static ResponseProfile from(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "default" -> DEFAULT;
            case "review" -> REVIEW;
            case "full" -> FULL;
            default -> throw new IllegalArgumentException(
                    "Unsupported responseProfile '%s'. Supported values: default, review, full."
                            .formatted(value));
        };
    }

    public boolean appliesProfileSteps() {
        return this == REVIEW;
    }
}

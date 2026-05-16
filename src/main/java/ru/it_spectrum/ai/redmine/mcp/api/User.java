package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;

@Schema(description = "Redmine user, trimmed to fields relevant for LLM context (no API keys, group memberships, or audit timestamps).")
public record User(
        @Schema(description = "User identifier.", requiredMode = Schema.RequiredMode.REQUIRED, example = "42")
        int id,
        @Schema(description = "Login name.", example = "ivan.petrov")
        String login,
        @Schema(description = "Display name (firstname + lastname).", example = "Ivan Petrov")
        String name,
        @Schema(description = "Email address, may be absent if not visible to the API caller.", example = "ivan.petrov@example.com")
        String mail
) {
    public static User from(RedmineUser source) {
        if (source == null) {
            return null;
        }
        return new User(
                source.id(),
                source.login(),
                joinName(source.firstname(), source.lastname()),
                source.mail()
        );
    }

    private static String joinName(String first, String last) {
        String f = first == null ? "" : first.trim();
        String l = last == null ? "" : last.trim();
        if (f.isEmpty() && l.isEmpty()) {
            return null;
        }
        if (f.isEmpty()) {
            return l;
        }
        if (l.isEmpty()) {
            return f;
        }
        return f + " " + l;
    }
}

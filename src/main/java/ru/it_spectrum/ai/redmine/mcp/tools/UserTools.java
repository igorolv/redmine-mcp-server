package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;

@Service
public class UserTools {
    private final RedmineClient client;

    public UserTools(RedmineClient client) {
        this.client = client;
    }

    @McpTool(description = "Get information about the currently authenticated Redmine user. " +
            "Returns user ID, login, name, email, groups, and project memberships. " +
            "Useful to find your own user ID for filtering (e.g. assigned_to_id in listIssues).")
    public String getCurrentUser() {
        var user = client.getCurrentUser();
        if (user == null) {
            return "Could not retrieve current user";
        }

        var sb = new StringBuilder();
        sb.append("Current user: %s %s\n".formatted(user.firstname(), user.lastname()));
        sb.append("ID: %d | Login: %s\n".formatted(user.id(), user.login()));
        if (user.mail() != null) sb.append("Email: %s\n".formatted(user.mail()));
        sb.append("Last login: %s\n".formatted(user.lastLoginOn()));

        if (user.groups() != null && !user.groups().isEmpty()) {
            sb.append("\nGroups:\n");
            for (var g : user.groups()) {
                sb.append("  - %s\n".formatted(g.name()));
            }
        }

        if (user.memberships() != null && !user.memberships().isEmpty()) {
            sb.append("\nProject memberships:\n");
            for (var m : user.memberships()) {
                if (m.project() != null) {
                    String roles = m.roles() != null
                            ? m.roles().stream().map(IdName::name).reduce((a, b) -> a + ", " + b).orElse("")
                            : "";
                    sb.append("  - %s — %s\n".formatted(m.project().name(), roles));
                }
            }
        }

        return sb.toString();
    }
}

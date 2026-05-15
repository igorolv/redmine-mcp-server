package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

@Service
public class UserTools {
    private final RedmineClient client;
    private final JsonResponses json;
    private final ToolErrors errors;

    public UserTools(RedmineClient client, JsonResponses json, ToolErrors errors) {
        this.client = client;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get information about the currently authenticated Redmine user. " +
            "Returns user ID, login, name, email, groups, and project memberships. " +
            "Useful to find your own user ID for filtering (e.g. assigned_to_id in listIssues).")
    public String getCurrentUser() {
        var user = client.getCurrentUser();
        if (user == null) {
            return errors.unavailable("current user");
        }
        return json.write(user);
    }
}

package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.UserService;

@Service
public class UserTools {
    private final UserService userService;
    private final JsonResponses json;
    private final ToolErrors errors;

    public UserTools(UserService userService, JsonResponses json, ToolErrors errors) {
        this.userService = userService;
        this.json = json;
        this.errors = errors;
    }

    @McpTool(description = "Get information about the currently authenticated Redmine user. " +
            "Returns user ID, login, name, email, groups, and project memberships. " +
            "Useful to find your own user ID for filtering (e.g. assigned_to_id in listIssues).")
    public String getCurrentUser() {
        var user = userService.getCurrentUser();
        if (user.isEmpty()) {
            return errors.unavailable("current user");
        }
        return json.write(user.get());
    }
}

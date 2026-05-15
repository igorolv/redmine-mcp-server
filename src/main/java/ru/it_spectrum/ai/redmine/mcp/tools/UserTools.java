package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.service.UserService;

@Service
public class UserTools {

    private static final Logger log = LoggerFactory.getLogger(UserTools.class);

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
        log.info("Tool call: getCurrentUser");
        long start = System.nanoTime();
        var user = userService.getCurrentUser();
        ToolLogger.completed(log, "getCurrentUser", start);
        if (user.isEmpty()) {
            return errors.unavailable("current user");
        }
        return json.write(user.get());
    }
}

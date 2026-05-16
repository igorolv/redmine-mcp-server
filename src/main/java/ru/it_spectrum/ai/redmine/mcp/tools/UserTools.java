package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;
import ru.it_spectrum.ai.redmine.mcp.service.ResourceUnavailableException;
import ru.it_spectrum.ai.redmine.mcp.service.UserService;

@Service
public class UserTools {

    private static final Logger log = LoggerFactory.getLogger(UserTools.class);

    private final UserService userService;

    public UserTools(UserService userService) {
        this.userService = userService;
    }

    @McpTool(
            description = "Get information about the currently authenticated Redmine user. " +
            "Returns user ID, login, name, email, groups, and project memberships. " +
            "Useful to find your own user ID for filtering (e.g. assigned_to_id in listIssues).",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public RedmineUser getCurrentUser() {
        log.info("Tool call: getCurrentUser");
        long start = System.nanoTime();
        var user = userService.getCurrentUser();
        if (user.isEmpty()) {
            var e = new ResourceUnavailableException("current user");
            ToolLogger.failed(log, "getCurrentUser", start, e.getMessage());
            throw e;
        }
        ToolLogger.completed(log, "getCurrentUser", start);
        return user.get();
    }
}

package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.List;

public record IssueTreeView(
        RedmineIssue root,
        List<RedmineIssue> ancestors,
        TreeNode subtree,
        List<RedmineIssue.Relation> relations,
        int fetchedCount,
        boolean limitReached
) {

    public record TreeNode(
            int id,
            String subject,
            IdName status,
            IdName tracker,
            IdName assignedTo,
            List<TreeNode> children,
            boolean stub
    ) {
    }
}

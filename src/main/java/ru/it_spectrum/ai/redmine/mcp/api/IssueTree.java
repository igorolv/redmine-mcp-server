package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Hierarchical view around a target issue: the issue itself, its ancestor chain up to the root, and its subtree.")
public record IssueTree(
        @Schema(description = "The issue the tree is centred around. Inspect its `related` field for cross-issue relations.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Opaque<Issue> root,
        @Schema(description = "Parent chain in order parent → grandparent → ... up to the root-most ancestor.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Opaque<List<Issue>> ancestors,
        @Schema(description = "The root issue plus its descendant subtree, expanded up to the requested depth.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Opaque<Node> subtree,
        @Schema(description = "Total issues fetched while building the tree (visibility into rate-limited traversal).", requiredMode = Schema.RequiredMode.REQUIRED)
        int fetchedCount,
        @Schema(description = "True when traversal stopped at the safety limit and additional branches/depths exist.", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean limitReached
) {

    @Schema(description = "Single node of the subtree.")
    public record Node(
            @Schema(description = "Issue identifier.", requiredMode = Schema.RequiredMode.REQUIRED)
            int id,
            @Schema(description = "Subject of the issue.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            String subject,
            @Schema(description = "Workflow status; null when this node is a stub (only the child reference was available).", nullable = true)
            Ref status,
            @Schema(description = "Tracker type.", nullable = true)
            Ref tracker,
            @Schema(description = "Assigned user; null when unassigned or stub.", nullable = true)
            Ref assignedTo,
            @Schema(description = "Child nodes; empty when this node is a leaf or a stub.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            List<Node> children,
            @Schema(description = "True when the node could not be fully resolved (depth limit or fetch budget exhausted); status/assignee may be null.", requiredMode = Schema.RequiredMode.REQUIRED)
            boolean stub
    ) {
    }
}

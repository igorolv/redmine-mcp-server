package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Blocking-dependency chain centred on an issue. `blockedBy` is the upstream chain (must be resolved first), `blocks` is the downstream chain (waits for this issue).")
public record BlockerChain(
        @Schema(description = "The issue the chain is centred on.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Opaque<Issue> root,
        @Schema(description = "Issues that block the root (recursively), each annotated with its depth from the root.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Opaque<Node>> blockedBy,
        @Schema(description = "Issues that depend on the root (recursively), each annotated with its depth from the root.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Opaque<Node>> blocks,
        @Schema(description = "End-to-end depth of the chain (upstream + 1 + downstream).", requiredMode = Schema.RequiredMode.REQUIRED)
        int chainDepth,
        @Schema(description = "Total number of distinct issues in the chain (including the root).", requiredMode = Schema.RequiredMode.REQUIRED)
        int totalIssues
) {

    @Schema(description = "Single issue in the blocking chain, annotated with its depth from the root (0 = direct neighbour).")
    public record Node(
            @Schema(description = "Issue at this position in the chain.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
            Issue issue,
            @Schema(description = "Distance from the root issue. 0 for direct upstream/downstream neighbours.", requiredMode = Schema.RequiredMode.REQUIRED)
            int depth
    ) {
    }
}

package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Describes how a context issue is connected to the issue under analysis.")
public record ContextRole(
        @Schema(description = "Kind of relationship.", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"parent", "sibling", "child", "related"}, nullable = true)
        String role,
        @Schema(description = "When role=related, the specific relation type (relates, blocks, blocked_by, duplicates, ...). Null for parent/sibling/child.",
                allowableValues = {"relates", "duplicates", "duplicated_by", "blocks", "blocked_by", "precedes", "follows", "copied_to", "copied_from"}, nullable = true)
        String relationType,
        @Schema(description = "Relation identifier when role=related.", nullable = true)
        Integer relationId,
        @Schema(description = "Source issue id in the relation: the issue that owns the relation entry.", nullable = true)
        Integer sourceIssueId,
        @Schema(description = "Target issue id in the relation: the context issue itself.", nullable = true)
        Integer targetIssueId,
        @Schema(description = "Delay in days for precedes/follows relations.", nullable = true)
        Integer delay
) {
}

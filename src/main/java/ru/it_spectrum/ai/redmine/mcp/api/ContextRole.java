package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Describes how a related issue is connected to the issue under analysis.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContextRole(
        @Schema(description = "Kind of relationship.", requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                allowableValues = {"parent", "sibling", "child", "related"}, nullable = true)
        Kind role,
        @Schema(description = "When role=related, the specific relation type (relates, blocks, blocked_by, duplicates, ...). Null for parent/sibling/child.",
                requiredMode = Schema.RequiredMode.NOT_REQUIRED,
                allowableValues = {"relates", "duplicates", "duplicated_by", "blocks", "blocked_by", "precedes", "follows", "copied_to", "copied_from"}, nullable = true)
        String relationType,
        @Schema(description = "Relation identifier when role=related.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Integer relationId,
        @Schema(description = "Source issue id in the relation: the issue that owns the relation entry.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Integer sourceIssueId,
        @Schema(description = "Target issue id in the relation: the related issue itself.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Integer targetIssueId,
        @Schema(description = "Delay in days for precedes/follows relations.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Integer delay
) {
    @Schema(description = "Kind of relationship between the related issue and the issue under analysis.")
    public enum Kind {
        @JsonProperty("parent") PARENT,
        @JsonProperty("sibling") SIBLING,
        @JsonProperty("child") CHILD,
        @JsonProperty("related") RELATED
    }
}

package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Aggregated effort statistics across a set of issues.")
public record HoursSummary(
        @Schema(description = "Sum of estimated hours across all issues that have an estimate.", requiredMode = Schema.RequiredMode.REQUIRED)
        double estimated,
        @Schema(description = "Sum of logged (spent) hours across all issues.", requiredMode = Schema.RequiredMode.REQUIRED)
        double spent
) {
}

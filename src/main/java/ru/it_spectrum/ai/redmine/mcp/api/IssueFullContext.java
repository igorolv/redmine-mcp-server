package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aggregated context for understanding an issue: the issue itself, interpreted history, related issues with their roles, inlined document excerpts, recent discussion notes and truncation flags.")
public record IssueFullContext(
        @Schema(description = "The issue under analysis.", requiredMode = Schema.RequiredMode.REQUIRED)
        Issue issue,
        @Schema(description = "Interpreted change history with status durations.", requiredMode = Schema.RequiredMode.REQUIRED)
        IssueHistory history,
        @Schema(description = "Nearby issues with the role they play (parent, sibling, child, related).", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ContextIssue> contextIssues,
        @Schema(description = "Text extracted from issue/parent attachments, inlined to reduce extra tool calls.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<DocumentExcerpt> documents,
        @Schema(description = "Most recent discussion notes (free-text comments). Older notes may be omitted; long notes are truncated.", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Issue.Journal> recentNotes,
        @Schema(description = "Flags indicating which context sets were truncated.", requiredMode = Schema.RequiredMode.REQUIRED)
        ContextStats stats
) {
}

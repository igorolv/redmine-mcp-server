package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Aggregated context for understanding an issue: the issue itself, interpreted history, related issues with their roles, materialized attachments, recent discussion notes and truncation flags.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IssueFullContext(
        @Schema(description = "The issue under analysis.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        Issue issue,
        @Schema(description = "Interpreted change history with status durations.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        IssueHistory history,
        @Schema(description = "Nearby issues with the role they play (parent, sibling, child, related).", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<ContextIssue> contextIssues,
        @Schema(description = "Issue/parent attachments materialized like getAttachment, with text constrained by full-context inline budgets and image attachments included as file links.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<ContextAttachment> attachments,
        @Schema(description = "Most recent discussion notes (free-text comments). Older notes may be omitted; long notes are truncated.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<Issue.Journal> recentNotes,
        @Schema(description = "Flags indicating which context sets were truncated.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        ContextStats stats,
        @Schema(description = "Human-readable notes describing how this response was compressed to fit the response size budget. Null/empty when no compression was applied.", requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true)
        List<String> compressionNotes
) {
    public IssueFullContext withIssue(Issue newIssue) {
        return new IssueFullContext(newIssue, history, contextIssues, attachments, recentNotes, stats, compressionNotes);
    }

    public IssueFullContext withAttachments(List<ContextAttachment> newAttachments) {
        return new IssueFullContext(issue, history, contextIssues, newAttachments, recentNotes, stats, compressionNotes);
    }

    public IssueFullContext withContextIssues(List<ContextIssue> newContextIssues) {
        return new IssueFullContext(issue, history, newContextIssues, attachments, recentNotes, stats, compressionNotes);
    }

    public IssueFullContext withRecentNotes(List<Issue.Journal> newRecentNotes) {
        return new IssueFullContext(issue, history, contextIssues, attachments, newRecentNotes, stats, compressionNotes);
    }

    public IssueFullContext withCompressionNotes(List<String> newCompressionNotes) {
        return new IssueFullContext(issue, history, contextIssues, attachments, recentNotes, stats, newCompressionNotes);
    }
}

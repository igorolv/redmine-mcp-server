package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

@Service
public class IncidentPrompts {

    private static final Logger log = LoggerFactory.getLogger(IncidentPrompts.class);

    @McpPrompt(
            name = "incident-brief",
            title = "Brief incident overview",
            description = "Quick reconnaissance of a Redmine incident: issue metadata plus all attachments " +
                    "downloaded locally with short text previews. Use this when you need a fast situational read."
    )
    public String incidentBrief(
            @McpArg(name = "issueId", description = "Redmine issue ID number", required = true) int issueId
    ) {
        log.info("Prompt requested: incident-brief (issueId={})", issueId);
        return """
                You are investigating Redmine incident #%d. Perform a quick reconnaissance using the available Redmine MCP tools.

                All tool names below (getIssue, getAttachment) refer to the Redmine MCP server's tools — your tool list shows them with a server-specific prefix (e.g. mcp__<server>__getIssue); map the short names to that prefixed form.

                Steps (perform in this exact order):
                1. Call getIssue with issueId=%d, focus="default" to fetch issue metadata, description, journals, and the attachments list.
                2. For EACH attachment in the issue's attachments list, call getAttachment with:
                       issueId=%d, attachmentId=<attachment.id>, maxChars=300, partLimit=300, focus="default"
                   This downloads the file locally AND returns a short text preview plus localPath/fileUri.
                3. Do not call any tools other than getIssue and getAttachment.

                After all calls, produce a Markdown report in EXACTLY this structure:

                ## Incident #%d: <subject>
                **Status:** <status> | **Assignee:** <assignee or "unassigned"> | **Priority:** <priority>
                **Created:** <created_on> | **Updated:** <updated_on>

                ### Summary
                <2-3 sentence distillation of the issue description in the issue's original language>

                ### Attachments (downloaded locally)
                - `<localPath>` — <one-line gist from the 300-char preview, or "image" / "binary" if no text was extracted>
                ...

                Rules for the attachments list:
                - Show one bullet per attachment, with the local filesystem path in backticks.
                - If the issue has more than 15 attachments, list ALL paths but write content gists only for the first 10 (by attachment id ascending). Mark the remaining lines with "— (preview skipped)".
                - Do not paste the raw preview text — distill it to one short line.
                - If extracting text failed or the file is binary/image, just write the file type, not an error.

                Do not add any other sections. Do not speculate about causes. This is a brief, not an analysis.
                """.formatted(issueId, issueId, issueId, issueId);
    }

    @McpPrompt(
            name = "incident-implementation",
            title = "Incident implementation context",
            description = "Review the implementation context of a Redmine incident: required behaviour, human notes, " +
                    "attachment context, and linked changeset revisions."
    )
    public String incidentImplementation(
            @McpArg(name = "issueId", description = "Redmine issue ID number", required = true) int issueId
    ) {
        log.info("Prompt requested: incident-implementation (issueId={})", issueId);
        return """
                You are reviewing the implementation context for Redmine incident #%d.

                All tool names below (getIssue, getAttachment) refer to the Redmine MCP server's tools — your tool list shows them with a server-specific prefix (e.g. mcp__<server>__getIssue); map the short names to that prefixed form.

                Steps:
                1. Call getIssue with issueId=%d, focus="implementation".
                   This focus keeps the issue description, human journal notes, attachment metadata, and all changeset revisions while omitting verbose field-change history.
                2. Inspect the attachments list. For each attachment that is likely to contain requirements, logs, screenshots, traces, specifications, patches, or test evidence, call getAttachment with:
                       issueId=%d, attachmentId=<attachment.id>, maxChars=3000, partLimit=3000, focus="implementation"
                   If there are more than 10 attachments, load the first 10 by attachment id and any additional attachments whose filename clearly looks implementation-relevant.
                3. Do not call getIssueJournal. The implementation focus already keeps human journal notes.
                4. Do not call write/mutation tools.

                After the calls, produce a Markdown report in EXACTLY this structure:

                ## Incident #%d: <subject>
                **Status:** <status> | **Assignee:** <assignee or "unassigned"> | **Priority:** <priority>

                ### Required Behaviour
                <what the issue description and human notes say must be implemented or fixed>

                ### Implementation Evidence
                - **Revisions:** <comma-separated changeset revisions, or "none visible">
                - **Relevant attachments:** <local paths and one-line purpose; write "none loaded" if none were loaded>

                ### Review Checklist
                - <specific code or behaviour check derived from the issue>
                - <specific test or regression check derived from the issue>
                - <specific data/configuration/log check if applicable>

                ### Gaps And Questions
                - <missing requirement, ambiguous note, missing attachment, missing revision, or "none">

                Rules:
                - Do not claim that a revision fixes the issue unless the issue text or notes support that.
                - Keep the report grounded in returned issue fields, journal notes, attachment text, and changeset revisions.
                - Preserve the issue's original language for user-facing summaries unless the user asked otherwise.
                """.formatted(issueId, issueId, issueId, issueId);
    }

    @McpPrompt(
            name = "incident-timeline",
            title = "Incident timeline",
            description = "Build a chronological account of who did what and when for a Redmine incident."
    )
    public String incidentTimeline(
            @McpArg(name = "issueId", description = "Redmine issue ID number", required = true) int issueId
    ) {
        log.info("Prompt requested: incident-timeline (issueId={})", issueId);
        return """
                You are reconstructing the timeline for Redmine incident #%d.

                All tool names below (getIssue, getIssueJournal) refer to the Redmine MCP server's tools — your tool list shows them with a server-specific prefix (e.g. mcp__<server>__getIssue); map the short names to that prefixed form.

                Steps:
                1. Call getIssue with issueId=%d, focus="timeline".
                   This focus keeps issue core fields, journals, and changesets while omitting attachments, custom fields, and related issue context.
                2. If compressionNotes say that older journal entries were omitted or note bodies were truncated, call getIssueJournal only for the specific journal IDs needed to answer the timeline accurately.
                3. Do not call getAttachment unless the user explicitly asks for attachment content.
                4. Do not call write/mutation tools.

                After the calls, produce a Markdown report in EXACTLY this structure:

                ## Incident #%d Timeline
                **Issue:** <subject>
                **Current status:** <status> | **Assignee:** <assignee or "unassigned">

                ### Chronology
                | Time | Actor | Event |
                |---|---|---|
                | <created/updated/journal/change time> | <user or system> | <status/assignee/change/note/revision summary> |

                ### Changesets
                - `<revision>` — <timestamp/author if available, and how it relates to the incident if known>

                ### Unclear Or Missing Points
                - <missing timestamp, truncated history, ambiguous action, or "none">

                Rules:
                - Sort chronology ascending by timestamp.
                - Separate facts from inference. Prefix inferred statements with "Inference:".
                - Do not include attachment summaries unless the user explicitly asked for them.
                - Preserve original note wording where short; summarize long notes rather than pasting them wholesale.
                """.formatted(issueId, issueId, issueId);
    }

}

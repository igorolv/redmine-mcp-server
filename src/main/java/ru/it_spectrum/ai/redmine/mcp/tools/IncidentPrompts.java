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
                1. Call getIssue with issueId=%d to fetch issue metadata, description, journals, and the attachments list.
                2. For EACH attachment in the issue's attachments list, call getAttachment with:
                       issueId=%d, attachmentId=<attachment.id>, maxChars=300, partLimit=300
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

}

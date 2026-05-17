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

    @McpPrompt(
            name = "investigate-incident",
            title = "Investigate incident in depth",
            description = "Deep investigation of a Redmine incident: full context (history, related issues, " +
                    "parent attachments), full attachment text, blocker chain, and a structured analysis with " +
                    "hypotheses and open questions."
    )
    public String investigateIncident(
            @McpArg(name = "issueId", description = "Redmine issue ID number", required = true) int issueId
    ) {
        log.info("Prompt requested: investigate-incident (issueId={})", issueId);
        return """
                You are performing a thorough investigation of Redmine incident #%d. Use the available Redmine MCP tools to gather full context and produce an analytical report.

                All tool names below (getIssueFullContext, getAttachment, getBlockerChain, getIssueTree) refer to the Redmine MCP server's tools — your tool list shows them with a server-specific prefix (e.g. mcp__<server>__getIssueFullContext); map the short names to that prefixed form.

                Steps (perform in this order, skip a step only if its precondition is not met):

                1. Call getIssueFullContext with issueId=%d. This returns the issue itself, an interpreted history timeline, nearby context issues (parent / siblings / children / related), inline-budgeted attachment text for both the issue and its parent, and recent discussion notes. This is your primary information source.

                2. For each attachment that looked relevant in step 1 but was truncated (truncated=true) or whose preview suggests it contains key technical details (logs, stack traces, configs, error reports), call getAttachment with the full text budget:
                       issueId=%d, attachmentId=<attachment.id>
                   (omit maxChars and partLimit to use server defaults).

                3. If the issue has any "blocks" or "blocked_by" relations (visible in step 1's relations), call getBlockerChain with issueId=%d to map the critical path.

                4. If the issue is part of a multi-level breakdown (has parent or subtasks visible in step 1), call getIssueTree with issueId=%d to see the full hierarchy at a glance.

                5. Do NOT call getIssue separately — getIssueFullContext already returns the issue.
                   Do NOT call listIssues, searchIssues, or analysis tools — stay focused on this incident.

                After all calls, produce a Markdown report in EXACTLY this structure:

                ## Incident #%d: <subject>
                **Status:** <status> | **Assignee:** <assignee> | **Priority:** <priority> | **Tracker:** <tracker>
                **Created:** <created_on> | **Updated:** <updated_on> | **Due:** <due_date or "—">

                ### What happened
                <3-5 sentences describing the problem, written from the original reporter's perspective, in the issue's original language>

                ### Timeline
                <Bullet list of key events from the interpreted history: status changes with durations, assignee changes, important comments. Newest last. Skip noise.>

                ### Key facts from attachments
                <For each attachment that contributed information: "**<filename>** (`<localPath>`): <2-4 sentences of distilled facts, quoting short verbatim fragments where they matter, e.g. error messages>". Skip attachments that added nothing.>

                ### Related issues
                <Brief notes on parent / siblings / children / related issues that change the picture. One bullet per related issue with its id, subject, and why it matters here.>

                ### Blockers and dependencies
                <If getBlockerChain was called: describe what blocks this issue and what it blocks. Otherwise write "No declared blockers.">

                ### Hypotheses
                <1-3 plausible explanations for the issue, ranked by likelihood, each with the supporting evidence from the data above.>

                ### Open questions
                <Specific concrete questions that the available data cannot answer. Be specific — e.g. "Was the database failover at 14:32 related to the first error spike at 14:35?", not "Need more info".>

                Rules:
                - Quote verbatim only where the exact wording matters (error messages, log lines). Otherwise distill.
                - Do not invent facts. If something is unknown, say so in "Open questions".
                - All local file paths must come from getAttachment / getIssueFullContext responses, not be guessed.
                - Write hypotheses as hypotheses, not conclusions.
                """.formatted(issueId, issueId, issueId, issueId, issueId, issueId);
    }
}

package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class IncidentPrompts {

    private static final Logger log = LoggerFactory.getLogger(IncidentPrompts.class);

    private static final String ISSUE_ID_DESCRIPTION = "Redmine issue number";

    /**
     * Command-template placeholders that clients substitute after fetching the prompt.
     * opencode 1.x calls prompts/get with "$1" for every argument while building its command
     * list and replaces the placeholder with the user's value later, client-side.
     */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$(\\d+|ARGUMENTS)");

    private static final Pattern ISSUE_NUMBER = Pattern.compile("#?([1-9]\\d{0,9})");

    private static final String PREAMBLE = """
            Tool names below are this Redmine MCP server's short names. Your client may expose them with a server prefix (mcp__redmine__getIssue, redmine_getIssue) or as tools.<server>.getIssue({...}) in code mode: use the exact name and parameter schema your client lists. If a named tool is not available, skip that step and say so in the report.
            If the issue number above is not a positive integer, stop and ask the user for it.
            Do not call tools that create or modify Redmine data.""";

    private static final String COMPRESSION_RULES = """
            Reading getIssue responses:
            - focusNotes describe what the chosen focus omits by design. compressionNotes describe what the server dropped to fit the response budget; retrying with focus="full" is compressed the same way.
            - "kept N most recent of M journal entries" or "omitted field-change details": that history is not in the response and the omitted journal IDs are unknown. Call getIssueHistory(issueId=%1$s) when it matters for the answer.
            - When getIssueHistory returns nextOffset, call getIssueHistory(issueId=%1$s, offset=<nextOffset>) and repeat until nextOffset is absent. Compare sourceUpdatedOn across pages; restart if it changes. Use each event's journalId with getIssueJournal for full text when needed.
            - A note ending in "(truncated by response compressor; total: N chars)": call getIssueJournal(issueId=%1$s, journalId=<that journal's id>) only when the full wording matters.
            - If your client saved an oversized tool response to a file, the file holds that whole response, but not data the server had already omitted.""";

    private static final String ATTACHMENT_RULES = """
            Reading getAttachment responses: check truncated and compressionNotes. Extracted text may be cut at about 10000 characters per part even when you ask for more; the complete file is at localPath. Images and unsupported binaries have no extracted text.""";

    @McpPrompt(
            name = "incident-brief",
            title = "Brief incident overview",
            description = "Quick issue overview: metadata plus every attachment downloaded locally with a short preview."
    )
    public String incidentBrief(
            @McpArg(name = "issueId", description = ISSUE_ID_DESCRIPTION, required = true) String issueId
    ) {
        log.info("Prompt requested: incident-brief (issueId={})", issueId);
        String id = issueIdForTemplate(issueId);
        return """
                You are investigating Redmine incident #%1$s. Perform a quick reconnaissance.

                %2$s

                Steps (perform in this exact order):
                1. Call getIssue(issueId=%1$s) to fetch issue metadata, description, journals, and the attachments list.
                2. For EACH attachment in the attachments list, call getAttachment(issueId=%1$s, attachmentId=<attachment.id>, maxChars=300, partLimit=300).
                   This downloads the file locally and returns a short text preview plus localPath/fileUri.
                3. Do not call any tools other than getIssue and getAttachment.

                After all calls, produce a Markdown report in EXACTLY this structure:

                ## Incident #%1$s: <subject>
                **Status:** <status> | **Assignee:** <assignee or "unassigned"> | **Priority:** <priority>
                **Created:** <createdOn> | **Updated:** <updatedOn>

                ### Summary
                <2-3 sentence distillation of the issue description in the issue's original language>
                <if compressionNotes say journal entries were omitted or truncated, add one sentence saying so>

                ### Attachments (downloaded locally)
                - `<localPath>` — <one-line gist from the preview, or "image" / "binary" if no text was extracted>
                ...

                Rules for the attachments list:
                - Show one bullet per attachment, with the local filesystem path in backticks.
                - If the issue has more than 15 attachments, list ALL paths but write content gists only for the first 10 (by attachment id ascending). Mark the remaining lines with "— (preview skipped)".
                - Do not paste the raw preview text — distill it to one short line.
                - If extracting text failed or the file is binary/image, just write the file type, not an error.

                Do not add any other sections. Do not speculate about causes. This is a brief, not an analysis.
                """.formatted(id, PREAMBLE);
    }

    @McpPrompt(
            name = "incident-implementation",
            title = "Incident implementation context",
            description = "Implementation context of an issue: required behaviour, human notes, relevant attachments " +
                    "and linked changeset revisions."
    )
    public String incidentImplementation(
            @McpArg(name = "issueId", description = ISSUE_ID_DESCRIPTION, required = true) String issueId
    ) {
        log.info("Prompt requested: incident-implementation (issueId={})", issueId);
        String id = issueIdForTemplate(issueId);
        return """
                You are reviewing the implementation context for Redmine incident #%1$s.

                %2$s

                Steps:
                1. Call getIssue(issueId=%1$s, focus="implementation").
                   This focus keeps the description, human journal notes, attachment metadata, related issues, and all changeset revisions, and drops field-change history by design.
                2. Choose attachments likely to hold requirements, specifications, logs, screenshots, traces, patches, or test evidence, judging by filename, description, author, and createdOn. When several files look like revisions of one document, prefer the newest. Load at most 10, each with getAttachment(issueId=%1$s, attachmentId=<attachment.id>, maxChars=3000, partLimit=3000).
                3. Call getIssueTree(issueId=%1$s) only if the scope depends on the parent issue or subtasks.
                4. Recover omitted journal data only when it could change the requirements (rules below).

                %3$s

                %4$s

                After the calls, produce a Markdown report in EXACTLY this structure:

                ## Incident #%1$s: <subject>
                **Status:** <status> | **Assignee:** <assignee or "unassigned"> | **Priority:** <priority>

                ### Required Behaviour
                <what the issue description and human notes say must be implemented or fixed; later notes override earlier ones>

                ### Implementation Evidence
                - **Revisions:** <comma-separated changeset revisions, or "none visible">
                - **Relevant attachments:** <local paths and one-line purpose; write "none loaded" if none were loaded>

                ### Review Checklist
                - <specific code or behaviour check derived from the issue>
                - <specific test or regression check derived from the issue>
                - <specific data/configuration/log check if applicable>

                ### Gaps And Questions
                - <missing requirement, ambiguous note, missing attachment, missing revision, omitted history, or "none">

                Rules:
                - A revision is evidence of work, not of completion. Do not claim that a revision fixes the issue unless the issue text or notes support that, and do not trust the status alone.
                - Keep the report grounded in returned issue fields, journal notes, attachment text, and changeset revisions.
                - Preserve the issue's original language for user-facing summaries unless the user asked otherwise.
                """.formatted(id, PREAMBLE, COMPRESSION_RULES.formatted(id), ATTACHMENT_RULES);
    }

    @McpPrompt(
            name = "incident-timeline",
            title = "Incident timeline",
            description = "Chronological account of who did what and when on an issue."
    )
    public String incidentTimeline(
            @McpArg(name = "issueId", description = ISSUE_ID_DESCRIPTION, required = true) String issueId
    ) {
        log.info("Prompt requested: incident-timeline (issueId={})", issueId);
        String id = issueIdForTemplate(issueId);
        return """
                You are reconstructing the timeline for Redmine incident #%1$s.

                %2$s

                Steps:
                1. Call getIssue(issueId=%1$s, focus="timeline").
                   This focus keeps core fields, journals with their field changes, and changesets, and omits attachments, custom fields, and related issues.
                2. Call getIssueHistory(issueId=%1$s) for every journal event and status interval; follow nextOffset as described below.
                3. Do not call getAttachment unless the user explicitly asks for attachment content.

                %3$s

                After the calls, produce a Markdown report in EXACTLY this structure:

                ## Incident #%1$s Timeline
                **Issue:** <subject>
                **Current status:** <status> | **Assignee:** <assignee or "unassigned">

                ### Chronology
                | Time | Actor | Event |
                |---|---|---|
                | <created/updated/journal/change time> | <user or system> | <status/assignee/field change, note, or revision summary> |

                ### Time In Status
                <one line per status interval from all getIssueHistory pages>

                ### Changesets
                - `<revision>` — <timestamp/author if available, and how it relates to the incident if known>

                ### Unclear Or Missing Points
                - <missing timestamp, omitted or truncated history, ambiguous action, or "none">

                Rules:
                - Sort chronology ascending by timestamp.
                - Separate facts from inference. Prefix inferred statements with "Inference:".
                - Do not include attachment summaries unless the user explicitly asked for them.
                - Preserve original note wording where short; summarize long notes rather than pasting them wholesale.
                """.formatted(id, PREAMBLE, COMPRESSION_RULES.formatted(id));
    }

    @McpPrompt(
            name = "issue-remaining-work",
            title = "Remaining work on an issue",
            description = "What is still unfinished on an issue: open subtasks and siblings, blockers, agreements " +
                    "in notes and implementation evidence."
    )
    public String issueRemainingWork(
            @McpArg(name = "issueId", description = ISSUE_ID_DESCRIPTION, required = true) String issueId
    ) {
        log.info("Prompt requested: issue-remaining-work (issueId={})", issueId);
        String id = issueIdForTemplate(issueId);
        return """
                You are determining what is still unfinished on Redmine issue #%1$s.

                %2$s

                Steps (stop as soon as the answer is clear; do not call tools mechanically):
                1. Call getIssue(issueId=%1$s, focus="implementation") for the scope, human notes, related issues, and changeset revisions.
                2. Call getIssueTree(issueId=%1$s) for the parent chain and subtasks. Check limitReached and stub nodes before treating the subtree as complete.
                3. If completion depends on sibling issues, call getIssueTree(issueId=<direct parent id, the first entry of ancestors>, depth=1).
                4. Call getBlockerChain(issueId=%1$s) for issues blocking this one and issues it blocks.
                5. Call getIssueHistory(issueId=%1$s) only if status changes, reopenings, or time in status matter, or if journal entries were omitted (rules below).
                6. Load an attachment with getAttachment only when a note points to it as the acceptance criteria or the current specification.

                %3$s

                After the calls, produce a Markdown report in EXACTLY this structure:

                ## Issue #%1$s: <subject>
                **Status:** <status> | **Assignee:** <assignee or "unassigned"> | **Done:** <done ratio if present>

                ### Verdict
                <one or two sentences: done, not done, or unclear, and the main reason>

                ### Open Items
                - <open subtask, open sibling, open blocker, unfulfilled agreement from a note, or missing evidence; with issue id and status>

                ### Evidence Of Completed Work
                - <closed subtasks, revisions, notes confirming acceptance>

                ### Unclear Or Missing Points
                - <tree limit reached, omitted history, contradictory notes, or "none">

                Rules:
                - Do not trust status alone: a closed status with open subtasks or blockers, or an open status whose work is confirmed by notes and revisions, must be called out.
                - Later notes override earlier agreements; mention reversals explicitly.
                - A revision is evidence of work, not of completion.
                - Preserve the issue's original language for user-facing summaries unless the user asked otherwise.
                """.formatted(id, PREAMBLE, COMPRESSION_RULES.formatted(id));
    }

    /**
     * Returns the value to embed in a prompt template. Client placeholders such as {@code $1} or
     * {@code $ARGUMENTS} are passed through verbatim so that client-side substitution keeps working;
     * a real value must be a positive issue number, optionally prefixed with {@code #}.
     */
    static String issueIdForTemplate(String issueId) {
        if (issueId != null && PLACEHOLDER.matcher(issueId).matches()) {
            return issueId;
        }
        var matcher = ISSUE_NUMBER.matcher(issueId == null ? "" : issueId.strip());
        if (matcher.matches()) {
            long number = Long.parseLong(matcher.group(1));
            if (number <= Integer.MAX_VALUE) {
                return Long.toString(number);
            }
        }
        throw new IllegalArgumentException(
                "issueId must be a Redmine issue number such as 12345, got '%s'".formatted(issueId));
    }

}

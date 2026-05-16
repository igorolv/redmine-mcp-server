# Redmine MCP Server — Setup Guide for AI Agents

This is a local MCP server that provides read-only access to a corporate Redmine instance.
It exposes 37 read-only tools for searching and reading issues, projects, members, versions, wiki pages, attachments, time entries, reference data, project analytics, and task context.

Step-by-step setup guides: [Claude Code](CLAUDE_CODE_SETUP.md) | [Qwen Code](QWEN_CODE_SETUP.md)

## Prerequisites

- JDK 25+ installed (check with `java -version`)
- On Windows, the default JDK may be older. Look for JDK 25+ in `~/.jdks/` or `$JAVA_HOME`

## Step 1: Get Redmine credentials from the user

Before building, ask the user for:
1. **Redmine URL** — the base URL of their Redmine instance (e.g. `https://redmine.example.com`)
2. **Redmine API Key** — found in Redmine under "My account" → "API access key" (right sidebar)

## Step 2: Build

```bash
# If the default JDK is < 25, set JAVA_HOME explicitly, e.g.:
# export JAVA_HOME="$HOME/.jdks/jdk-25.0.2"

cd <path-to-this-project>
./gradlew build
```

The resulting jar: `build/libs/redmine-mcp-server.jar`

## Step 3: Verify the server starts

```bash
REDMINE_URL=<url-from-user> REDMINE_API_KEY=<key-from-user> \
  java -jar build/libs/redmine-mcp-server.jar
```

The server communicates via stdio (stdin/stdout) by default. It will not open any HTTP ports.
If it starts without errors, it is ready to be connected to an MCP client.

## Step 4: Connect to an MCP client

Add the server to the client's MCP configuration. The format varies by client, but the content is the same:

**Stdio configuration (JSON):**
```json
{
  "command": "java",
  "args": ["-jar", "<absolute-path-to>/redmine-mcp-server.jar"],
  "env": {
    "REDMINE_URL": "<url-from-user>",
    "REDMINE_API_KEY": "<key-from-user>"
  }
}
```

**Where to put it:**

| Client | Config file | Server key |
|---|---|---|
| Claude Code | `~/.claude/settings.json` → `"mcpServers"` | `"redmine"` |
| Qwen Code | `~/.qwen/settings.json` → `"mcpServers"` | `"redmine"` |
| VS Code (Copilot/Continue) | `.vscode/mcp.json` → `"servers"` | `"redmine"` |
| Cursor | `.cursor/mcp.json` → `"mcpServers"` | `"redmine"` |
| Claude Desktop | `claude_desktop_config.json` → `"mcpServers"` | `"redmine"` |

**Example for Claude Code (`~/.claude/settings.json`):**
```json
{
  "mcpServers": {
    "redmine": {
      "command": "java",
      "args": ["-jar", "<absolute-path-to>/redmine-mcp-server.jar"],
      "env": {
        "REDMINE_URL": "<url-from-user>",
        "REDMINE_API_KEY": "<key-from-user>"
      }
    }
  }
}
```

After adding the configuration, restart the client so it picks up the new MCP server.

## Available tools

The current implementation exposes **37 read-only MCP tools** across user, project, issue, attachment, wiki, time-entry, reference-data, analytics, and task-context domains.

| Tool | Description |
|---|---|
| `getCurrentUser` | Get current authenticated user info (ID, login, groups, memberships). Useful for self-filtering |
| `listProjects` | List all accessible projects. Params: `limit`, `offset` |
| `getProject` | Get project details (trackers, modules). Params: `projectId` |
| `listProjectMembers` | List project members with roles. Params: `projectId`, `limit`, `offset` |
| `listVersions` | List project versions/milestones. Params: `projectId` |
| `listIssues` | List issues with filters (project, status, tracker, assignee, priority, version, saved query, custom field filters, sort). Params: `projectId`, `statusId`, `trackerId`, `assignedToId`, `priorityId`, `versionId`, `queryId`, `customFieldFilters`, `sort`, `limit`, `offset` |
| `searchIssues` | Full-text search across issues with detailed results. Params: `query`, `projectId`, `limit`, `offset` |
| `searchAll` | Global search across all content (issues, wiki, news, changesets). Params: `query`, `limit`, `offset` |
| `getIssue` | Get full issue details (description, notes, relations, custom fields, attachments, associated changesets/revisions). Params: `issueId` |
| `getMyIssues` | List issues assigned to the current user. Params: `projectId`, `statusId`, `sort`, `limit`, `offset` (all optional) |
| `getIssueTree` | Build full dependency tree: parent chain up, subtasks down, relations. Params: `issueId`, `depth` (optional, default 2, max 5) |
| `getIssueHistory` | Full change history with timeline of status/assignment/priority changes and status durations. Params: `issueId` |
| `getAttachmentContent` | Get content of an attachment. Supports text files (txt, log, xml, json, csv, etc.), PDF, Word (.docx), Excel (.xlsx), and PowerPoint (.pptx). Attachment IDs are returned by `getIssue.attachments`. For images use `getImageAttachment`. Params: `issueId`, `attachmentId` |
| `getImageAttachment` | Download an image attachment (PNG, JPEG, GIF, BMP, WebP) with automatic resizing for AI visual analysis. Attachment IDs are returned by `getIssue.attachments`. Params: `issueId`, `attachmentId`, `maxWidth` (optional, default 1024) |
| `searchAttachmentContent` | Search for text across attachments of an issue or project. Extracts text from PDF/DOCX/XLSX/PPTX/text, returns matching snippets. Params: `query`, `issueId`, `projectId`, `limit` |
| `getWikiPage` | Get wiki page content and attachments. Params: `projectId`, `pageTitle` |
| `listWikiPages` | List all wiki pages in a project. Params: `projectId` |
| `listTimeEntries` | List time entries with filters (project, issue, user, date range). Params: `projectId`, `issueId`, `userId`, `from`, `to`, `limit`, `offset` |
| `getMyTimeEntries` | List time entries for the current user. Params: `projectId`, `issueId`, `from`, `to`, `limit`, `offset` (all optional) |
| `listStatuses` | List all available issue statuses (ID + name). Useful for filtering in listIssues |
| `listTrackers` | List all available trackers (ID + name). Useful for filtering in listIssues |
| `listPriorities` | List all available issue priorities (ID + name). Useful for filtering in listIssues |
| `listIssueCategories` | List issue categories for a project (ID + name). Params: `projectId` |
| `listQueries` | List saved queries (custom filters) available in Redmine. Params: `limit`, `offset` |
| `listTimeEntryActivities` | List all time entry activity types (ID + name). Useful when logging time |
| `getProjectSummary` | Aggregated project stats: issue counts by status/tracker/priority/assignee, overdue count, hours. Params: `projectId`, `versionId` (optional) |
| `getUserWorkload` | User workload analysis: open issues by project and priority, overdue, top issues. Params: `userId` (optional), `projectId` (optional) |
| `getVersionChangelog` | All issues for a version grouped by tracker with open/closed stats. Params: `projectId`, `versionId` |
| `getBlockerChain` | Recursive traversal of blocks/blocked_by relations to show full dependency chain. Params: `issueId` |
| `getStaleIssues` | Open issues not updated for N days, sorted by staleness. Params: `projectId`, `daysSinceUpdate` (default 30), `limit` |
| `getReleaseRisks` | Risk assessment: open blockers, overdue, high-priority, unassigned issues for a version. Params: `projectId`, `versionId` |
| `compareVersions` | Diff between two versions: unique issues, shared issues, completion percentages. Params: `projectId`, `versionId1`, `versionId2` |
| `getIssueFullContext` | Full task context in one call: description, parent epic, siblings (feature scope), related issues with descriptions, document attachments extracted inline, recent notes. Replaces 10+ separate calls. Params: `issueId` |
| `getIssueSiblings` | All issues sharing the same parent: feature scope, progress, who's doing what. Params: `issueId` |
| `findRelatedClosedIssues` | Find closed reference issues: direct relations, siblings, similar in project. Useful for "how was this done before". Params: `issueId`, `limit` (optional) |
| `findLatestAttachment` | Find latest version of a document by filename pattern. Searches issue, parent, siblings, related issues. Params: `pattern`, `issueId`, `searchProject` (optional) |
| `getIssueNetwork` | Full relation network via BFS: all types (relates, blocks, precedes, duplicates, parent/child) up to specified depth. Params: `issueId`, `depth` (optional, default 2, max 3) |

All tools are **read-only**. No data in Redmine is modified.

## Troubleshooting

- **"Gradle requires JVM 17 or later"** — set `JAVA_HOME` to a JDK 25+ before running `./gradlew`
- **Connection refused / 401** — verify `REDMINE_URL` is reachable and `REDMINE_API_KEY` is valid. Test with: `curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json`
- **No search results** — Redmine's `/search.json` must be enabled by the admin. Verify manually in browser: `<url>/search?q=test`

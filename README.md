# Redmine MCP Server

[![CI](https://github.com/igorolv/redmine-mcp-server/actions/workflows/ci.yml/badge.svg)](https://github.com/igorolv/redmine-mcp-server/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/igorolv/redmine-mcp-server?include_prereleases)](https://github.com/igorolv/redmine-mcp-server/releases/latest)
[![License](https://img.shields.io/github/license/igorolv/redmine-mcp-server)](LICENSE)
[![Java 25](https://img.shields.io/badge/Java-25%2B-blue?logo=openjdk)](https://adoptium.net/)
[![MCP](https://img.shields.io/badge/MCP-server-8A2BE2)](https://modelcontextprotocol.io/)
[![Glama score](https://glama.ai/mcp/servers/igorolv/redmine-mcp-server/badges/score.svg)](https://glama.ai/mcp/servers/igorolv/redmine-mcp-server)

A local MCP server for accessing a corporate Redmine instance. By default the server is fully
read-only; an optional flag enables a limited set of write operations for issues and time entries.
It lets AI agents (Claude Code, Cursor, VS Code Copilot, etc.) work with issues, projects,
members, versions, wiki, attachments, time entries, and reference data.

## Quick Start

This documentation covers installing and connecting Redmine MCP Server itself. Installing and
configuring the AI clients themselves is out of scope here.

1. Install JDK 25+.
2. Download `redmine-mcp-server.jar` from the [latest release](https://github.com/igorolv/redmine-mcp-server/releases/latest),
   or build it yourself: `./gradlew bootJar` (see [Build](#build)). A [Docker image](#docker) is
   published as well.
3. Obtain `REDMINE_URL` and `REDMINE_API_KEY` (see [Configuration](#configuration)).
4. Verify that the JAR starts (see [Smoke Test](#smoke-test)).
5. Add the JAR to your client's MCP configuration (see [Connecting to an AI Client](#connecting-to-an-ai-client)).

For Claude Code that is one command:

```bash
claude mcp add --scope user -e REDMINE_URL=https://redmine.example.com -e REDMINE_API_KEY=your_key -- redmine java -jar /path/to/redmine-mcp-server.jar
```

## Architecture

The server supports only the `stdio` transport.

### Stdio

```
┌─────────────┐     stdio      ┌──────────────────┐    REST API    ┌──────────┐
│  AI agent   │ <------------> │  redmine-mcp-    │ -------------> │ Redmine  │
│ (Claude Code│   stdin/stdout │  server (Java)   │   HTTP + API   │ (corp.)  │
│  Cursor...) │                │                  │   Key          │          │
└─────────────┘                └──────────────────┘                └──────────┘
```

The AI client launches the server as a child process; communication follows the MCP protocol
over stdin/stdout.

## Tools

By default the server exports **32 read-only MCP tools**. When
`REDMINE_MCP_WRITE_ENABLED=true` is set, 7 more write tools are added.

### User

| Tool | Description |
|---|---|
| `getCurrentUser` | Current user: ID, login, groups, projects. Useful for "my issues" filtering |

### Projects

| Tool | Description |
|---|---|
| `listProjects` | List of all accessible projects |
| `getProject` | Project details: trackers, modules, description |
| `listProjectMembers` | Project members with roles |
| `listVersions` | Project versions (milestones) |

### Issues

| Tool | Description |
|---|---|
| `listIssues` | Issue list with filters: project, status, tracker, assignee, priority, version, saved query, custom fields (`customFieldFilters` in `cf_<id>=value` format), sorting |
| `searchIssues` | Full-text issue search with detailed results |
| `getIssue` | Issue details: description, status, assignee, dates, notes, relations, custom fields, attachments, linked revisions (`changesets`). Parameters: `issueId`, `focus` (`default`, `implementation`, `timeline`, `full`; optional) |
| `getIssueJournal` | A single complete issue journal note/event without response compression. Parameters: `issueId`, `journalId` |
| `getMyIssues` | Issues of the current user. Parameters: `projectId`, `statusId`, `sort`, `limit`, `offset` |
| `getIssueTree` | Dependency tree: parent chain upward, subtasks downward, relations. Parameters: `issueId`, `depth` (default 2, max 5) |

### Issue, Time Entry, and Wiki Writes (optional)

These tools appear in `tools/list` only when `REDMINE_MCP_WRITE_ENABLED=true`:

| Tool | Description |
|---|---|
| `createIssue` | Creates an issue. Supports core Redmine fields and custom fields via `customFieldsJson` |
| `updateIssue` | Partially updates the specified fields of an existing issue |
| `addIssueNote` | Adds a note to the issue journal |
| `attachFileToIssue` | Uploads a file from any readable local path and attaches it to the issue |
| `createTimeEntry` | Creates a time entry for the user from `REDMINE_API_KEY`; accepts exactly one of `issueId`/`projectId`, hours, date, activity, comment, and custom fields |
| `createWikiPage` | Creates a wiki page and rejects the request if the page already exists |
| `updateWikiPage` | Fully replaces the wiki page text, requiring the optimistic-lock version returned by `getWikiPage` |

Redmine itself enforces the permissions, workflow, and required-field rules of the `REDMINE_API_KEY`
user. The MCP server introduces no additional model of "own" issues or notes. To make AI changes
recognizable, created-issue descriptions, updated descriptions, new notes, and time-entry comments
are prefixed with `AI_EDIT:`. For wiki pages this prefix goes into the revision comment,
without changing the page markup. Uploaded file names are prefixed with `AI_EDIT__`.

Editing and deleting existing notes is not implemented: the targeted Redmine 4.0.4
does not provide a compatible REST API for it. Editing and deleting time entries; deleting,
renaming, and protecting wiki pages; and creating time entries on behalf of another user are
outside the current operation set.

### Search

| Tool | Description |
|---|---|
| `searchAll` | Global Redmine search: issues, wiki, news, documents, commits, etc. Parameters: `searchQuery`, `projectId`, `types`, `limit`, `offset` |

### Attachments and Wiki

| Tool | Description |
|---|---|
| `getAttachment` | Downloads the original attachment file into a local snapshot directory, returns `localPath`/`fileUri`, and immediately adds text context to `parts[]` if the format is supported: txt/log/xml/json/csv, PDF, Word (.docx), Excel (.xlsx), PowerPoint (.pptx), ZIP. A ZIP may yield a separate part per entry. Parameters: `issueId`, `attachmentId`, `maxChars`, `partLimit` |
| `getWikiPage` | Content of a project wiki page |
| `listWikiPages` | List of all wiki pages of a project |
| `searchWikiPages` | Full-text search across wiki pages. Parameters: `searchQuery`, `projectId`, `limit`, `offset` |

### Time Entries

| Tool | Description |
|---|---|
| `listTimeEntries` | Logged time with filters: project, issue, user, period |
| `getMyTimeEntries` | Logged time of the current user. Parameters: `projectId`, `issueId`, `from`, `to`, `limit`, `offset` |

### Reference Data

| Tool | Description |
|---|---|
| `listQueries` | Saved queries (custom filters) — ID + name. Use the ID with `listIssues(queryId)` to apply the filter, including custom-field filters |
| `listStatuses` | All issue statuses (ID + name) — for filtering in `listIssues` |
| `listTrackers` | All trackers (ID + name) — for filtering in `listIssues` |
| `listPriorities` | All priorities (ID + name) — for filtering in `listIssues` |
| `listIssueCategories` | Issue categories of a project (ID + name) |
| `listTimeEntryActivities` | Activity types for interpreting existing time entries (ID + name) |

### Analytics

| Tool | Description |
|---|---|
| `getProjectSummary` | Aggregated project summary: overall open/closed count; analyzed open issues broken down by status, tracker, priority, assignee; overdue issues; estimated/spent hours. Analyzes up to 500 open issues and returns a truncation flag. Parameters: `projectId`, `versionId` (optional) |
| `getUserWorkload` | Workload analysis: open issues by project and priority, overdue issues, top issues. Analyzes up to 500 open issues and returns a truncation flag. Parameters: `userId` (optional, defaults to current user), `projectId` (optional) |
| `getVersionChangelog` | Version issues grouped by tracker, open/closed statistics. Analyzes up to 500 issues and returns a truncation flag. Parameters: `projectId`, `versionId` |
| `getBlockerChain` | Recursive traversal of the blocking chain (blocks/blocked_by) upward and downward, limited to depth 10 and 30 loaded issues. Parameters: `issueId` |
| `getStaleIssues` | Open issues not updated for N days, oldest first. Parameters: `projectId`, `daysSinceUpdate` (default 30), `limit` |
| `getReleaseRisks` | Release risk assessment: blockers, overdue items, high-priority issues, unassigned issues. Analyzes up to 500 open issues and returns a truncation flag. Parameters: `projectId`, `versionId` |
| `compareVersions` | Compares two versions: unique issues, shared issues, closure percentage. Analyzes up to 500 issues per version and returns a truncation flag. Parameters: `projectId`, `versionId1`, `versionId2` |

Without `REDMINE_MCP_WRITE_ENABLED=true` all tools are read-only and no data in Redmine is modified.

### Tool Groups (enable/disable)

Tools are grouped by domain, and each group can be disabled via an environment variable.
All groups are enabled by default — the out-of-the-box tool manifest is unchanged. Disabling
groups shrinks the MCP `tools/list` manifest that the client loads into model context at session
start. This helps small-context (local) models: disable unneeded groups so only the tools the
model actually needs remain.

| Variable | Group (tools) |
|---|---|
| `REDMINE_MCP_TOOLS_ISSUE` | Issues (core): `listIssues`, `searchIssues`, `getIssue`, `getMyIssues`, `getIssueJournal` |
| `REDMINE_MCP_TOOLS_ISSUE_STRUCTURE` | Issue structure/history: `getIssueTree`, `getIssueHistory` |
| `REDMINE_MCP_TOOLS_PROJECT` | Projects: `listProjects`, `getProject`, `listProjectMembers`, `listVersions` |
| `REDMINE_MCP_TOOLS_SEARCH` | Search: `searchAll` |
| `REDMINE_MCP_TOOLS_ATTACHMENT` | Attachments: `getAttachment` |
| `REDMINE_MCP_TOOLS_WIKI` | Wiki: `getWikiPage`, `listWikiPages`, `searchWikiPages` |
| `REDMINE_MCP_TOOLS_TIME_ENTRY` | Time entries: `listTimeEntries`, `getMyTimeEntries` |
| `REDMINE_MCP_TOOLS_REFERENCE_DATA` | Reference data: `listQueries`, `listStatuses`, `listTrackers`, `listPriorities`, `listIssueCategories`, `listTimeEntryActivities` |
| `REDMINE_MCP_TOOLS_USER` | User: `getCurrentUser` |
| `REDMINE_MCP_TOOLS_ISSUE_ANALYTICS` | Issue analytics: `getBlockerChain`, `getStaleIssues` |
| `REDMINE_MCP_TOOLS_RELEASE_ANALYTICS` | Release/project analytics: `getProjectSummary`, `getUserWorkload`, `getVersionChangelog`, `getReleaseRisks`, `compareVersions` |

Each variable accepts `true` (default) or `false`. Example: to keep only issue and project work,
disable the remaining groups — `REDMINE_MCP_TOOLS_RELEASE_ANALYTICS=false`,
`REDMINE_MCP_TOOLS_WIKI=false`, etc. MCP prompts (`incident-*`) are not affected by these flags.

## MCP Prompts

The server also exports **MCP prompts** for typical incident-handling scenarios:

| Prompt | Description |
|---|---|
| `incident-brief` | Quick incident overview: fetches the issue via `getIssue`, downloads all attachments via `getAttachment` with short previews, and produces a concise Markdown report |
| `incident-implementation` | Implementation context: fetches the issue with `focus=implementation`, loads relevant attachments, and produces requirements, evidence per revision, and a verification checklist |
| `incident-timeline` | Incident chronology: fetches the issue with `focus=timeline`, pulls complete journal entries via `getIssueJournal` when needed, and builds a timeline of who did what and when |

## Tech Stack

- Java 25, Spring Boot 4.0.0, Spring AI MCP 2.0.0-M6 (stdio transport)
- Apache PDFBox 3.0.5 — text extraction from PDFs
- Apache POI 5.4.0 — text extraction from Word, Excel, PowerPoint
- Apache Tika 3.2.0 (core + parsers-standard) — fallback parser and metadata extraction
- Pandoc (optional, external binary) — improved DOCX to text/markdown conversion when available on `PATH`; otherwise the server uses POI
- Gradle 9.3.1 with version catalog (`gradle/libs.versions.toml`)

## Build

Linux/macOS:

```bash
# Point to JDK 25+ if it is not the default JDK:
export JAVA_HOME="$HOME/.jdks/jdk-25.0.2"

./gradlew build
```

Windows PowerShell:

```powershell
# Point to JDK 25+ if it is not the default JDK:
$env:JAVA_HOME="C:\Program Files\Java\jdk-25"

.\gradlew.bat build
```

Result: `build/libs/redmine-mcp-server.jar`

## Configuration

The server needs `REDMINE_URL` and `REDMINE_API_KEY`; the remaining variables are optional:

| Variable | Description |
|---|---|
| `REDMINE_URL` | Base URL of the Redmine instance (e.g. `https://redmine.example.com`) |
| `REDMINE_API_KEY` | Redmine user's API key |
| `REDMINE_MCP_WRITE_ENABLED` | Adds the `createIssue`, `updateIssue`, `addIssueNote`, `attachFileToIssue`, `createTimeEntry`, `createWikiPage`, `updateWikiPage` tools to `tools/list`; defaults to `false` |
| `REDMINE_MCP_DATA_DIR` | Local data directory of the server; defaults to `~/.redmine-mcp-server` |
| `REDMINE_MCP_ATTACHMENT_PER_PART_CHARS` | Text limit per single `part` (e.g., one file inside a ZIP) for `getAttachment`; defaults to `30000` characters. The tool's `partLimit` parameter overrides this value. |
| `REDMINE_MCP_ATTACHMENT_PER_ATTACHMENT_CHARS` | Total limit of extracted text per attachment in `getAttachment`; defaults to `50000` characters. The tool's `maxChars` parameter overrides this value. |
| `REDMINE_MCP_RELATED_MAX_SIBLINGS` | Maximum sibling issues added to `related` when reading an issue; defaults to `20` |
| `REDMINE_MCP_RELATED_MAX_CHILDREN` | Maximum child issues added to `related` when reading an issue; defaults to `20` |
| `REDMINE_MCP_RELATED_MAX_RELATED` | Maximum related issues from relations added to `related` when reading an issue; defaults to `10` |
| `REDMINE_MCP_RESPONSE_MAX_CHARS` | Target response size limit before stepwise compression of `getIssue` and `getAttachment`; defaults to `50000` characters |
| `REDMINE_MCP_RESPONSE_JOURNAL_TAIL_KEEP` | How many most-recent journal entries the budget compression of `getIssue` keeps before more aggressive reduction; defaults to `30` |
| `REDMINE_MCP_RESPONSE_ATTACHMENT_TEXT_PART_CHARS` | Text limit per attachment part during response compression of `getAttachment`; defaults to `10000` characters |
| `REDMINE_MCP_RESPONSE_JOURNAL_NOTE_CHARS` | Text limit per journal note during response compression of `getIssue`; defaults to `5000` characters |
| `REDMINE_MCP_RESPONSE_IMAGE_PARTS_KEEP` | How many image parts the response compression of `getAttachment` keeps; defaults to `5` |
| `REDMINE_MCP_PAGINATION_DEFAULT_LIMIT` | Default page size for list/search tools; defaults to `25` |
| `REDMINE_MCP_PAGINATION_DEFAULT_OFFSET` | Default offset for list/search tools; defaults to `0` |
| `REDMINE_MCP_PAGINATION_MEMBERS_DEFAULT_LIMIT` | Default page size for `listProjectMembers`; defaults to `100` |
| `REDMINE_MCP_TREE_DEFAULT_DEPTH` | Default depth for `getIssueTree`; defaults to `2` |
| `REDMINE_MCP_TREE_MAX_DEPTH` | Maximum depth of `getIssueTree`; defaults to `5` |
| `REDMINE_MCP_TREE_MAX_ISSUES` | Maximum issues loaded by `getIssueTree`; defaults to `50` |
| `REDMINE_MCP_ANALYSIS_MAX_PAGES` | Maximum Redmine pages read by analytics tools; defaults to `5` |
| `REDMINE_MCP_ANALYSIS_PAGE_SIZE` | Redmine page size for analytics tools; defaults to `100` |
| `REDMINE_MCP_ANALYSIS_TOP_ISSUES_LIMIT` | Maximum issues in top lists of analytics responses; defaults to `10` |
| `REDMINE_MCP_ANALYSIS_MAX_BLOCKER_DEPTH` | Maximum traversal depth for `getBlockerChain`; defaults to `10` |
| `REDMINE_MCP_ANALYSIS_MAX_BLOCKER_ISSUES` | Maximum issues loaded by `getBlockerChain`; defaults to `30` |
| `REDMINE_MCP_ANALYSIS_STALE_ISSUES_DEFAULT_DAYS_SINCE_UPDATE` | Default `daysSinceUpdate` for `getStaleIssues`; defaults to `30` |
| `REDMINE_MCP_ANALYSIS_STALE_ISSUES_DEFAULT_LIMIT` | Default result limit for `getStaleIssues`; defaults to `25` |
| `REDMINE_MCP_ANALYSIS_STALE_ISSUES_MAX_LIMIT` | Maximum result limit for `getStaleIssues`; defaults to `100` |
| `REDMINE_MCP_EXTRACTION_PANDOC_ENABLED` | Enables the use of Pandoc for DOCX when the binary is found on `PATH`; defaults to `true` |
| `REDMINE_MCP_EXTRACTION_PANDOC_PROBE_TIMEOUT_SECONDS` | Timeout for probing Pandoc availability at startup; defaults to `2` seconds |
| `REDMINE_MCP_EXTRACTION_PANDOC_CONVERSION_TIMEOUT_SECONDS` | Timeout for a single DOCX conversion via Pandoc; defaults to `30` seconds |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_DEPTH` | Maximum recursion depth for processing nested documents and archives; defaults to `1` |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_TOTAL_PARTS` | Maximum text/metadata parts per extraction; defaults to `100` |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_TOTAL_BYTES` | Total bytes-read limit per extraction; defaults to `52428800` bytes |
| `REDMINE_MCP_EXTRACTION_LIMITS_MAX_ENTRY_BYTES` | Limit for a single entry inside an archive; defaults to `10485760` bytes |
| `REDMINE_MCP_EXTRACTION_ZIP_MAX_ENTRIES_PER_ARCHIVE` | Maximum entries per ZIP archive; defaults to `100` |
| `REDMINE_MCP_EXTRACTION_TIKA_BODY_LIMIT_BYTES` | Body size limit passed to the Tika fallback parser; defaults to `5242880` bytes |
| `REDMINE_MCP_EXTRACTION_TIKA_METADATA_MAX_FIELDS` | Maximum Tika metadata fields in the response; defaults to `40` |

### How to Get `REDMINE_URL`

Open Redmine in your browser and copy the address-bar value **without** the path — scheme and domain only.

Examples:

| In browser address bar | `REDMINE_URL` value |
|---|---|
| `https://redmine.example.com/projects/myproject` | `https://redmine.example.com` |
| `http://192.168.1.50:3000/issues/123` | `http://192.168.1.50:3000` |
| `http://10.0.0.5/redmine/projects` | `http://10.0.0.5/redmine` |

> If Redmine is reachable only by IP address (no domain name), use the IP as-is, including the port if it differs from the standard one (80/443). If Redmine is deployed under a subpath (e.g. `/redmine`), include it in the URL too.

### How to Get `REDMINE_API_KEY`

1. Log in to Redmine with your account
2. Click **"My account"** (top right corner)
3. In the right column find the **"API access key"** block
4. Click **"Show"** — your personal API key will be displayed
5. Copy the key and use it as the `REDMINE_API_KEY` value

> If the "API access key" block is not shown, contact the Redmine administrator — the REST API may be disabled in settings.

## Smoke Test

Before connecting an AI client it is worth verifying that the JAR starts correctly with the same
environment variables that will later go into the client configuration.

Linux/macOS:

```bash
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=your_key \
  java -jar build/libs/redmine-mcp-server.jar
```

Windows PowerShell:

```powershell
$env:REDMINE_URL="https://redmine.example.com"
$env:REDMINE_API_KEY="your_key"
java -jar .\build\libs\redmine-mcp-server.jar
```

The server runs over `stdio` and opens no HTTP port: after a successful start it silently waits
for MCP requests on `stdin/stdout`. A successful start shows as the absence of errors in the log
and no immediate process exit. Press `Ctrl+C` to stop.

### Docker

The image is published to GHCR with every release:

```bash
docker run -i --rm   -e REDMINE_URL=https://redmine.example.com   -e REDMINE_API_KEY=your_key   ghcr.io/igorolv/redmine-mcp-server:latest
```

The same command is what an MCP client should launch (`-i` keeps stdin open for the stdio
transport). Mount a host directory at `/data` to keep logs and issue snapshots between runs.
To build the image locally: `docker build -t redmine-mcp-server .`

### Logs

Logs are written to `${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/logs/redmine-mcp-server.log`.
The file rotates by date and size: `10MB`, retention `30` days, total cap `512MB`.

### Issue Snapshots

When loading an issue the server persists a snapshot to disk under
`${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/issues/<issue-id>/`: `issue.json`,
`snapshot.json` with snapshot metadata, `attachments.json`, and an `extracted/<attachment-id>/`
directory for derived files. Attachments are materialized under `attachments/` with names like
`<attachment-id>__<filename>` and can be reused across snapshots when the local file already
exists and its size matches the Redmine metadata.

## Connecting to an AI Client

Add to the client configuration:

```json
{
  "command": "java",
  "args": ["-jar", "<absolute-path>/redmine-mcp-server.jar"],
  "env": {
    "REDMINE_URL": "https://redmine.example.com",
    "REDMINE_API_KEY": "your_api_key"
  }
}
```

Where exactly:

| Client | How to connect |
|---|---|
| Claude Code | `claude mcp add --scope user -e REDMINE_URL=... -e REDMINE_API_KEY=... -- redmine java -jar /path/to/redmine-mcp-server.jar` |
| Qwen Code | `~/.qwen/settings.json` -> `"mcpServers"` -> `"redmine"` |
| VS Code | `.vscode/mcp.json` -> `"servers"` -> `"redmine"` |
| Cursor | `.cursor/mcp.json` -> `"mcpServers"` -> `"redmine"` |
| Claude Desktop | `claude_desktop_config.json` -> `"mcpServers"` -> `"redmine"` |

Restart the client afterwards.

## Operations and Security

This MCP server is designed to run locally alongside the AI client. It opens no HTTP port and
accepts no incoming network connections: the client launches the JAR as a child process and
communicates with it via `stdin/stdout`.

### Access Model

- The server uses the permissions of the Redmine user whose API key is set in `REDMINE_API_KEY`.
- By default all MCP tools are read-only. With `REDMINE_MCP_WRITE_ENABLED=true` seven explicitly
  listed write tools for issues, time entries, and wiki become available; the server neither extends
  the API user's rights nor bypasses the Redmine workflow.
- Accessible projects, issues, attachments, and time entries are determined by the user's permissions
  in Redmine. If a user cannot see an object in Redmine, the server must not gain access to it either.
- The `AI_EDIT` marker is a search label, not an authorization mechanism: authorship of a change is
  reliably established by the Redmine account that owns the API key.
- `attachFileToIssue` deliberately accepts any readable local path without an allow-list of directories.
  In write mode run the server only next to a trusted AI client, keeping in mind that the client will be able
  to pass the contents of any file readable by the process into Redmine.
- Treat the API key as a secret. Do not commit it to the repository, shell scripts, `.vscode/mcp.json`,
  `.cursor/mcp.json`, or other shared files of the project.

For development and verification of write operations use a separate test Redmine and a separate
API key. Do not run `integrationTest` with write enabled against a production installation.

### What Data Is Passed to the AI Client

The AI client receives exactly the data it requests through MCP tools:

- issue cards: subject, description, status, priority, assignee, author, dates, relations, subtasks, journals/comments, custom fields;
- information about projects, versions, members, reference data, and time entries;
- wiki pages;
- attachment metadata;
- local paths to the original attachment files and text extracted from PDF, DOCX, XLSX, PPTX, ZIP, and text files via `getAttachment`.

Before connecting an external or cloud AI client, check your company's internal policies: Redmine
data may contain trade secrets, personal data, logs, keys, error dumps, and document contents.

### Processing Limits

There are protective limits in the code so that a single large document or a related network of
issues cannot overload the MCP client:

| Area | Limit |
|---|---|
| Each text part of `getAttachment.parts[]` | up to 30,000 characters by default, beyond that the text is truncated |
| One attachment in `getAttachment` in total | up to 50,000 characters by default |
| ZIP depth | 1 level |
| ZIP archives | up to 100 entries |
| ZIP file inside an archive | up to 10 MB |
| ZIP archive in total | up to 50 MB of extracted data |
| `getIssueTree` | depth up to 5, max 50 issues |

`getIssue` supports the `focus` parameter. `default` keeps the usual
response shape and applies compression only when the response budget is exceeded. `implementation`
targets implementation work on the issue: the full issue is still persisted to disk, while the tool
response keeps the description, human notes, attachment metadata, and all changeset revisions;
verbose history and commit message bodies are omitted. `timeline` targets
"who did what and when" questions: it keeps journals and changesets but omits attachments,
custom fields, and related context. `full` is an explicit choice of the full form with protective
budget compression.

If the note you need was dropped from the `getIssue` response by budget compression or the note was
shortened, call `getIssueJournal(issueId, journalId)`: it re-takes the issue snapshot and
returns the selected journal entry without response compression.

Some regular list tools (`listIssues`, `listProjects`, `listTimeEntries`, `listQueries`) accept `limit`
and `offset` directly. For reliable operation avoid requesting excessively large pages; a practical
range is 25-100 items per call.

### Diagnostics

Environment check:

```bash
java -version
echo "$REDMINE_URL"
test -n "$REDMINE_API_KEY" && echo "REDMINE_API_KEY is set"
```

Redmine REST API access check:

```bash
curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json
```

Build check:

```bash
./gradlew test
./gradlew build
```

Integration tests against a live Redmine:

```bash
REDMINE_URL=<url> REDMINE_API_KEY=<key> ./gradlew integrationTest
```

Integration tests require a reachable Redmine and real test data. Unit tests exclude tests tagged
`integration` by default.

### Known Operational Limitations

- HTTP timeouts and the retry policy are currently not configurable separately. If Redmine is slow
  to respond, an MCP call may wait longer than is convenient for the AI client.
- Redmine errors (`401`, `403`, `404`, `5xx`) are currently handled mostly at the Spring `RestClient`
  level; the message seen by the AI-client user may be less friendly than a dedicated MCP tool error.
- A note or attachment may be written successfully, but its new `journalId`/`attachmentId` may stay
  undefined if Redmine or a concurrent user modifies the issue between confirmation reads. The operation
  itself is still considered completed.
- Search depends on Redmine settings. If `/search.json` is disabled by the administrator,
  `searchAll`, `searchIssues`, and `searchWikiPages` may not return the expected results.
- Text extraction from PDF works only for PDFs with a text layer. Scanned documents without OCR
  are detected as PDFs with no extractable text.
- Images are not re-encoded. `getAttachment` returns the path to the original file; text `parts[]`
  for images remain empty.

## Project Structure

```
├── src/main/java/ru/it_spectrum/ai/redmine/mcp/
│   ├── RedmineMcpServerApplication.java   — Spring Boot entry point
│   ├── api/                                — stable MCP wire format: records returned by tools/services
│   │   ├── Issue.java
│   │   ├── AttachmentContent.java
│   │   ├── Project.java
│   │   ├── IssueMutationResult.java        — stable result of issue write operations
│   │   ├── TimeEntryMutationResult.java    — stable result of time-entry creation
│   │   ├── WikiMutationResult.java         — stable result of wiki-page writes
│   │   └── ...                             — tool response DTOs and analytics DTOs
│   ├── client/
│   │   ├── RedmineClient.java              — read-only wrapper over the Redmine REST API
│   │   ├── RedmineMutationClient.java      — optional POST/PUT for issues, time entries, and wiki
│   │   └── model/                          — raw Redmine REST API DTOs, never exported directly to MCP
│   │       ├── RedmineIssue.java
│   │       ├── RedmineAttachment.java
│   │       ├── RedmineProject.java
│   │       └── ...
│   ├── config/
│   │   ├── RedmineClientProperties.java   — url + apiKey from env
│   │   ├── RedmineMcpProperties.java      — all redmine-mcp.* runtime settings
│   │   ├── RedmineConfig.java             — RestClient
│   │   ├── McpServerConfig.java           — stdio MCP customizer with immediateExecution(true)
│   │   └── JsonConfig.java                — ObjectMapper for MCP JSON
│   ├── extraction/
│   │   ├── ExtractionPipeline.java        — document-to-text pipeline
│   │   ├── DocumentParser.java            — parser interface
│   │   ├── FileTypeDetector.java          — file type detection
│   │   ├── PandocAvailability.java        — external pandoc probe at startup
│   │   └── parser/
│   │       ├── PlainTextParser.java       — txt/log/csv/json/xml
│   │       ├── PdfTextParser.java         — PDF via PDFBox
│   │       ├── DocxTextParser.java        — DOCX via POI
│   │       ├── DocxPandocParser.java      — DOCX via Pandoc when available
│   │       ├── XlsxTextParser.java        — XLSX via POI
│   │       ├── PptxTextParser.java        — PPTX via POI
│   │       ├── ZipParser.java             — ZIP with bounded recursion
│   │       ├── ImagePassthroughParser.java
│   │       ├── TikaTextFallbackParser.java
│   │       ├── TikaMetadataParser.java
│   │       └── BinaryFallbackParser.java
│   ├── service/
│   │   ├── IssueService.java              — issue business logic and mapping client.model -> api
│   │   ├── IssueMutationService.java      — optional issue writing and AI marking
│   │   ├── TimeEntryMutationService.java  — optional time-entry creation
│   │   ├── WikiMutationService.java       — safe wiki writing with optimistic locking
│   │   ├── AttachmentService.java         — attachment snapshot, download, and extraction
│   │   ├── IssueSnapshotService.java      — local issue and attachment snapshots
│   │   ├── AnalysisService.java           — analytics, risks, blocker chain
│   │   └── ...                            — services for projects, wiki, search, reference data, time entries
│   └── tools/
│       ├── AttachmentTools.java           — 1 MCP tool for files and attachment context
│       ├── IncidentPrompts.java           — MCP prompt for incident investigation
│       ├── IssueAnalyticsTools.java       — 2 issue-analytics MCP tools (blocker chain, stale)
│       ├── IssueStructureTools.java       — 2 MCP tools: issue tree and change history
│       ├── IssueTools.java                — 5 core issue MCP tools
│       ├── IssueWriteTools.java           — 4 optional issue-write MCP tools
│       ├── ProjectTools.java              — 4 project MCP tools
│       ├── ReferenceDataTools.java        — 6 reference-data MCP tools
│       ├── ReleaseAnalyticsTools.java     — 5 release/project analytics MCP tools
│       ├── SearchTools.java               — 1 global-search MCP tool
│       ├── TimeEntryTools.java            — 2 time-entry MCP tools
│       ├── TimeEntryWriteTools.java       — 1 optional time-entry creation MCP tool
│       ├── UserTools.java                 — 1 current-user MCP tool
│       ├── WikiTools.java                 — 3 wiki MCP tools
│       └── WikiWriteTools.java            — 2 optional wiki-write MCP tools
└── src/main/resources/
    ├── application.yml                    — MCP server configuration (stdio)
    └── logback-spring.xml                 — logging configuration
```

## Troubleshooting

- **"Gradle requires JVM 17 or later"** — point `JAVA_HOME` at JDK 25+
- **Connection refused / 401** — check `REDMINE_URL` and `REDMINE_API_KEY`. Test: `curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json`
- **No search results** — verify that `/search.json` is available in Redmine (it may be disabled by the administrator)

# Redmine MCP Server — Setup Guide for AI Agents

This is a local MCP server that provides read-only access to a corporate Redmine instance.
It exposes tools for searching, reading issues, projects, members, versions, wiki, and attachments.

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

The resulting jar: `build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar`

## Step 3: Verify the server starts

```bash
REDMINE_URL=<url-from-user> REDMINE_API_KEY=<key-from-user> \
  java -jar build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar
```

The server communicates via stdio (stdin/stdout). It will not open any HTTP ports.
If it starts without errors, it is ready to be connected to an MCP client.

## Step 4: Connect to an MCP client

Add the server to the client's MCP configuration. The format varies by client, but the content is the same:

**Common configuration (JSON):**
```json
{
  "command": "java",
  "args": ["-jar", "<absolute-path-to>/redmine-mcp-server-0.1.0-SNAPSHOT.jar"],
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
      "args": ["-jar", "<absolute-path-to>/redmine-mcp-server-0.1.0-SNAPSHOT.jar"],
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

| Tool | Description |
|---|---|
| `listProjects` | List all accessible projects. Params: `limit`, `offset` |
| `getProject` | Get project details (trackers, modules). Params: `projectId` |
| `listProjectMembers` | List project members with roles. Params: `projectId`, `limit`, `offset` |
| `listVersions` | List project versions/milestones. Params: `projectId` |
| `listIssues` | List issues with filters (project, status, tracker, assignee, priority, version, sort). Params: `projectId`, `statusId`, `trackerId`, `assignedToId`, `priorityId`, `versionId`, `sort`, `limit`, `offset` |
| `searchIssues` | Full-text search across issues with detailed results. Params: `query`, `projectId`, `limit`, `offset` |
| `searchAll` | Global search across all content (issues, wiki, news, changesets). Params: `query`, `limit`, `offset` |
| `getIssue` | Get full issue details (description, notes, relations, attachments). Params: `issueId` |
| `listAttachments` | List all attachments of an issue. Params: `issueId` |
| `getAttachmentContent` | Get content of a text-based attachment (txt, log, xml, json, csv, etc). Returns metadata only for binary files. Params: `attachmentId` |
| `getWikiPage` | Get wiki page content and attachments. Params: `projectId`, `pageTitle` |

All tools are **read-only**. No data in Redmine is modified.

## Troubleshooting

- **"Gradle requires JVM 17 or later"** — set `JAVA_HOME` to a JDK 25+ before running `./gradlew`
- **Connection refused / 401** — verify `REDMINE_URL` is reachable and `REDMINE_API_KEY` is valid. Test with: `curl -H "X-Redmine-API-Key: <key>" <url>/users/current.json`
- **No search results** — Redmine's `/search.json` must be enabled by the admin. Verify manually in browser: `<url>/search?q=test`

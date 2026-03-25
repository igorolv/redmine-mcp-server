# Prompt for AI Agent: Set Up Redmine MCP Server

Copy the text below and paste it to your AI agent (Claude Code, Cursor, Copilot, etc.) as a task.

---

## Prompt

I need you to build and connect the Redmine MCP server from the project at `<PATH_TO_PROJECT>`.

Follow these steps:

1. **Find JDK 25+.** Run `java -version`. If the version is below 25, search for a suitable JDK in `~/.jdks/`, `/usr/lib/jvm/`, or common install locations. Set `JAVA_HOME` to point to it.

2. **Build the project.**
   ```
   cd <PATH_TO_PROJECT>
   ./gradlew build -x test
   ```
   The jar will be at `build/libs/redmine-mcp-server-0.1.0-SNAPSHOT.jar`. Verify the file exists.

3. **Ask me for credentials.** I need to provide:
   - **REDMINE_URL** — the base URL of my Redmine (just IP or hostname is fine, you can add `http://` if missing)
   - **REDMINE_API_KEY** — my personal API key from Redmine (My Account -> API access key in the right sidebar)

4. **Verify connectivity.** Run:
   ```
   curl -s -o /dev/null -w "%{http_code}" -H "X-Redmine-API-Key: <MY_KEY>" http://<MY_URL>/users/current.json
   ```
   Expect HTTP 200. If 401 — the key is wrong. If connection refused — the URL is wrong.

5. **Register the MCP server in your config.** Determine which AI client we are in and add the server config:

   - **Claude Code**: edit `~/.claude/settings.json`, add to `mcpServers`:
     ```json
     "redmine": {
       "command": "java",
       "args": ["-jar", "<ABSOLUTE_PATH>/redmine-mcp-server-0.1.0-SNAPSHOT.jar"],
       "env": {
         "REDMINE_URL": "<MY_URL>",
         "REDMINE_API_KEY": "<MY_KEY>"
       }
     }
     ```

   - **Qwen Code**: edit `~/.qwen/settings.json`, add to `mcpServers` (same JSON format as Claude Code).
   - **VS Code (Copilot/Continue)**: edit `.vscode/mcp.json`, add to `servers`.
   - **Cursor**: edit `.cursor/mcp.json`, add to `mcpServers`.
   - **Claude Desktop**: edit `claude_desktop_config.json`, add to `mcpServers`.

   Use absolute paths. Replace placeholders with real values.

6. **Notify me** that the setup is complete and I should restart the client. After restart, confirm the server is connected by listing available tools — there should be 11 Redmine tools.

If any step fails, show me the error and suggest a fix. Do not skip steps.

---

## What the server provides

After setup, the agent gets 11 read-only tools for working with Redmine:

**Projects:** `listProjects`, `getProject`, `listProjectMembers`, `listVersions`
**Issues:** `listIssues`, `searchIssues`, `getIssue`
**Search:** `searchAll` (global search across issues, wiki, news, changesets)
**Files & Wiki:** `listAttachments`, `getAttachmentContent`, `getWikiPage`

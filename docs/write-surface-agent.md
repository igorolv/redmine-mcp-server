# docs/write-surface-agent.md — opt-in Redmine mutation boundary

Read this when changing write tools, mutation clients, AI markers, write settings or tests of mutations.

## Approved operations

The server is read-only by default. Without `REDMINE_MCP_WRITE_ENABLED=true`, no write tool bean
exists and no MCP tool may issue `POST`, `PUT`, `DELETE` or `PATCH`. With the flag enabled, only
`IssueWriteTools`, `TimeEntryWriteTools` and `WikiWriteTools` expose these operations:

| Tool | Scope |
|---|---|
| `createIssue` | Create an issue. The created-issue description may receive its configured AI prefix. |
| `updateIssue` | Update an issue; do not apply the created-issue description prefix. |
| `addIssueNote` | Add a note to an issue. |
| `attachFileToIssue` | Upload and attach a file to an issue. |
| `createTimeEntry` | Create an entry for the API-key user. |
| `createWikiPage` | Create a wiki page. |
| `updateWikiPage` | Replace the complete page body; require the current version from `getWikiPage`. |

These tools use `RedmineMutationClient`, only `POST` and `PUT`, and the permissions and workflow
of `REDMINE_API_KEY`. Do not add `DELETE`/`PATCH`, journal mutation, wiki deletion, rename,
protection or attachments, or time-entry updates or deletion without a separate design
conversation. The compatibility baseline is Redmine 4.0.4. Write tools are gated by
`redmine-mcp.write.enabled` / `REDMINE_MCP_WRITE_ENABLED`, which defaults to `false`; do not fold
them into a default-on `redmine-mcp.tools.*` group.

## AI content markers

All markers come from `AiContentMarker`, which reads one prefix per content type from
`redmine-mcp.write.*-prefix` in `RedmineMcpProperties.Write`. The five types are created-issue
description (default `AI_EDIT:`), issue note, time-entry comment, wiki revision comment and
attachment filename (the latter four default to empty). Empty means leave the content untouched.
Never hardcode a marker in a service or tool. Wiki markers belong in revision comments, not page
text.

## Verification

Unit tests for write paths use mocks and `MockRestServiceServer`. Never mutation-test against
production Redmine. Live write verification requires an explicitly designated disposable test
instance and key; normal `test` and `build` must not mutate any Redmine.

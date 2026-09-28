# redmine-mcp-server — instructions for agents developing the MCP server

This repository builds a local stdio MCP server for Redmine. These instructions govern agents
**changing or reviewing this repository's source, tests and engineering documentation**. An agent
using the published MCP tools to work on Redmine issues should follow the tool schemas and MCP
prompts supplied by the running server; this file is not a Redmine task workflow. `README.md` is
the user-facing catalogue, setup guide and security description.

## Instruction routing

Read this root file first. Then read only the documents whose `When` matches the work, before
changing the relevant code. The paths in this table are relative to the repository root.

| Document | When | What it holds |
|---|---|---|
| `docs/architecture-agent.md` | locating code, changing dependencies, transport, or Spring wiring | package map, layer direction, technology, stdio and execution context |
| `docs/tool-contracts-agent.md` | adding, editing, diagnosing or reviewing an MCP tool, prompt, wire record, schema or tool group | tool and prompt patterns, conditional registration, output schemas, logging, description and JSON test conventions |
| `docs/write-surface-agent.md` | adding, editing, diagnosing or reviewing issue, time-entry or wiki writes, mutation clients or AI markers | approved operations, opt-in registration, mutation limits and safe test boundary |
| `docs/retrieval-extraction-agent.md` | changing issue history, tree reads, snapshots, attachment responses, response budgets or parsers | recoverable truncation, snapshot layout, parser order and extraction limits |
| `docs/build-config-agent.md` | building, testing, running the jar or adding a configuration knob | wrapper commands, JDK/toolchain, integration-test boundary and property binding |

The table is the complete index of `docs/*-agent.md`. Register any new agent document here when
creating it. Keep durable engineering contracts in these documents; keep product usage and
connection instructions in `README.md`, and instructions to an MCP client in the server's
`@McpPrompt` text and tool descriptions. Do not put operational Redmine task recipes in this
instruction corpus.

## Project boundaries

- Java 25, Spring Boot 4, Spring AI MCP server, Gradle 9.x. Version aliases are in
  `gradle/libs.versions.toml`.
- Dependencies flow `tools/ -> service/ -> client/` (and `extraction/`). `api/` records are the
  stable MCP wire contract; `client/model/` records mirror Redmine REST responses and never
  cross the MCP boundary.
- `RedmineMcpServerApplication` stays an empty entry point. `config/` owns Spring wiring and
  `RedmineMcpProperties`; `tools/` contains thin adapters, while `service/` owns decisions and
  maps raw Redmine data to `api/`.

## Invariants that apply to every change

1. **Writes are opt-in and limited.** Without `REDMINE_MCP_WRITE_ENABLED=true`, no write tool
   bean exists and no tool issues `POST`, `PUT`, `DELETE` or `PATCH`. The only approved write
   tools and their exact semantics are in `docs/write-surface-agent.md`; do not enlarge this
   surface without an explicit design conversation.
2. **Stdio is the only transport.** `spring.main.web-application-type: none`; never open an
   HTTP port or write to `System.out`, the JSON-RPC channel. Logging goes to SLF4J and the
   stderr/file appenders.
3. **Tool calls execute immediately.** `McpServerConfig` sets `immediateExecution(true)` on
   `McpSyncServer` to avoid concurrent stdout writes. Do not switch to async/reactive without
   re-evaluating this race.
4. **Responses stay recoverable.** If a tool shortens or omits content, identify what is
   missing and provide a structured follow-up path: `nextOffset`, `journalId`, issue ID, or
   attachment `localPath`/`fileUri` as appropriate. Snapshots are local artifacts, not a general
   Redmine cache or a replacement for LLM retrieval. See `docs/retrieval-extraction-agent.md`.
5. **Live mutation tests need a disposable instance.** Normal `test` and `build` must not
   mutate Redmine. Unit tests for writes use mocks and `MockRestServiceServer`.

## Editing and verification

- Drive Gradle through the checked-in wrapper (`.\gradlew.bat` in PowerShell). `test` excludes
  JUnit's `integration` tag; `integrationTest` requires live `REDMINE_URL` and
  `REDMINE_API_KEY`. Use `docs/build-config-agent.md` for commands and configuration changes.
- Follow the surrounding Java style and keep `api/` records and schema annotations aligned
  with serialized JSON. Check the relevant focused tests for code changes. Documentation-only
  routing changes can be checked by path/index and diff review.
- Use `README.md` for user-facing tool catalogue, env vars and setup; use this instruction
  corpus for engineering decisions. Keep `CONTRIBUTING.md` consistent if contributor workflow
  changes.
- Write `AGENTS.md` and `docs/*-agent.md` in English. State current contracts and
  non-obvious reasons, avoid task-specific history, and keep each fact in one owning document.
  Make `When` describe a task trigger, and `What it holds` describe the destination content.

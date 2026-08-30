# Contributing to Redmine MCP Server

Thank you for considering a contribution! This document covers how to set up the project,
what conventions the codebase follows, and how to submit changes.

For the product overview, tool catalogue and configuration see [README.md](README.md).
For the full engineering guide (architecture, conventions, invariants) see [AGENTS.md](AGENTS.md).

## Prerequisites

- **JDK 25+** (`build.gradle.kts` pins the Java toolchain to 25). If your machine only has an
  older JDK, Gradle downloads a matching toolchain on the first build — that requires network
  access to `api.foojay.io`.
- Git. Gradle itself is wrapped (`gradlew`) — no local installation needed.

## Building and testing

```bash
./gradlew build              # compile + unit tests + bootJar
./gradlew test               # unit tests only (integration tests excluded)
./gradlew integrationTest    # smoke tests against a live Redmine
./gradlew bootJar            # build/libs/redmine-mcp-server.jar
```

Integration tests need a reachable Redmine instance (compatibility baseline 4.0.4):

```bash
REDMINE_URL=https://your-redmine.example.com REDMINE_API_KEY=your-key ./gradlew integrationTest
```

Unit tests are Mockito-based and do not boot Spring or touch the network.
Before opening a PR, make sure `./gradlew build` passes on your machine.

## Project layout

```
src/main/java/ru/it_spectrum/ai/redmine/mcp/
  tools/      @McpTool entry points (thin: log, delegate, log again)
  service/    business logic, raw -> api mapping orchestration
  api/        stable wire-format records returned by tools
  client/     RedmineClient / RedmineMutationClient (RestClient wrappers)
  client/model/  raw Redmine REST DTOs (never exposed on the wire)
  extraction/ document-to-text pipeline for attachments
  config/     Spring configuration, properties, Jackson setup
```

Dependencies flow one way only: `tools -> service -> client`. Tools never call the client
directly and never return `client.model.*` types — everything crossing the wire comes from `api/`.

## Non-negotiable invariants

A PR will be rejected if it breaks any of these (details in AGENTS.md):

1. **Write access is opt-in and narrowly scoped.** Without `REDMINE_MCP_WRITE_ENABLED=true`
   no write tool bean exists and no tool may issue `POST` / `PUT` / `DELETE` / `PATCH`.
   With the flag enabled, only the approved operations in `IssueWriteTools`,
   `TimeEntryWriteTools`, and `WikiWriteTools` are available — do not expand that surface
   and do not add `DELETE` / `PATCH` without a separate design conversation.
2. **Stdio transport only.** Never open an HTTP port, never write to `System.out`.
3. **Wire format is `api/*`.** Raw client DTOs stay inside the service/client layers.
4. **AI markers stay consistent.** New descriptions, notes, time-entry comments, and wiki
   revision comments use `AI_EDIT:`; uploaded filenames use `AI_EDIT__`.

## Coding conventions

- Records for DTOs; add fields as record components, not setters.
- Map all raw responses to `api/*` records at the service boundary; mark components that can be
  absent as `@Schema(nullable = true)` (Jackson is configured with `NON_NULL` globally).
- Throw the typed exceptions from `service/` (`IssueNotFoundException`, …) at the tool boundary;
  never return null or empty records as a substitute.
- Read pagination defaults from `properties.pagination()`; never hardcode limits.
- Log entry with `log.info("Tool call: <name> (...)")` and exit via `ToolLogger.completed/failed`.
- Keep `@McpTool` / `@McpToolParam` descriptions lean — every word is recurring model context.
- Add a unit test for every new mapping rule, parameter default or error path, asserting on the
  serialized JSON via `ToolJsonTestSupport.stringify(result)`.
- If you change a dependency version, update `gradle/libs.versions.toml`, not the build script.

## Commit messages

Use short conventional-style summaries, as in the existing history:

```
feat: add X
fix: correct Y
docs: sync Z with code
tools: ...       # tool-surface changes
api: ...         # wire-format changes
perf: ...
refactor: ...
```

## Pull requests

1. Fork / branch from `main`, keep the change focused — one logical topic per PR.
2. Run `./gradlew build` and confirm it passes.
3. Update documentation alongside behavior:
   user-facing changes → `README.md`; engineering conventions → `AGENTS.md`.
4. Describe *why* the change is needed in the PR description, not just what it does.
5. Do not commit secrets (`REDMINE_API_KEY`, local config) or IDE files.

## Reporting bugs

Open a GitHub issue and include:

- Server JAR version or commit the JAR was built from;
- AI client used (Claude Code, Cursor, VS Code Copilot, …);
- Redmine version and whether write mode was enabled;
- The failing tool name and arguments;
- Relevant excerpt from `${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/logs/`
  (redact API keys and private URLs first).

## Licensing

By contributing you agree that your contributions are licensed under the
[MIT License](LICENSE).

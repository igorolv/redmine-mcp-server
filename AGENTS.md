# AGENTS.md — Engineering Guide for AI Coding Agents

This file is for AI agents (and humans) **modifying the source code** of this repository.
It is **not** a setup guide for end users — for that, see:

- [README.md](README.md) — product description, MCP tool catalogue, env vars, security model,
  build, smoke-test, and client connection instructions.

Read this file before changing code. It documents non-obvious invariants, the layered architecture,
and the conventions that the existing code follows consistently.

---

## 1. What this project is

A local **MCP (Model Context Protocol) server** that exposes a Redmine instance to AI clients
(Claude Code, Cursor, VS Code Copilot, Qwen Code, …) over **stdio**. It is **read-only**: it never
creates, updates, or deletes anything in Redmine.

Core invariants — never break these without an explicit conversation:

1. **Read-only.** No MCP tool may issue `POST`, `PUT`, `DELETE`, or `PATCH` against Redmine.
   Every `@McpTool` is annotated with `readOnlyHint = true, destructiveHint = false, idempotentHint = true`.
2. **Stdio only.** The server has `spring.main.web-application-type: none`. It must never open
   an HTTP port, never write to `System.out` (stdout is the MCP transport channel — anything
   written there corrupts the JSON-RPC stream).
3. **Immediate execution for tool calls.** `McpServerConfig` sets `immediateExecution(true)` on
   the `McpSyncServer` builder. This avoids a stdout write race when boundedElastic tool
   completions finish concurrently. Do not switch to async/reactive without re-evaluating this.
4. **Wire format is the `api/*` package.** Tools return records from `ru.it_spectrum.ai.redmine.mcp.api`,
   never raw `client.model.*` types. The `api` types are the **stable MCP contract**;
   `client.model` types track Redmine's REST shape and may change when Redmine changes.

---

## 2. Tech stack and exact versions

- **Java 25** toolchain (`build.gradle.kts` pins `JavaLanguageVersion.of(25)`).
- **Spring Boot 4** + **Spring AI MCP server** (stdio transport) — version aliases in
  `gradle/libs.versions.toml`.
- **Apache PDFBox** — PDF text extraction.
- **Apache POI (ooxml)** — DOCX/XLSX/PPTX text extraction.
- **Apache Tika (core + parsers-standard)** — fallback parser and metadata extraction.
- **Pandoc** (optional, external binary) — improved DOCX → text/markdown conversion when
  available; probed at startup, gracefully skipped if missing.
- **Gradle 9.x** with version catalog (`libs.versions.toml`).
- Jackson Databind for JSON; ObjectMapper configured with `NON_NULL` inclusion (`JsonConfig`).

If you change a dependency, update `libs.versions.toml`, not the build script.

---

## 3. Build, run, test

The build tool is **Gradle** (wrapper checked in as `./gradlew` / `gradlew.bat`). Always drive
builds, tests, and the runnable jar through the Gradle wrapper — every command below is the
canonical invocation.

All commands assume `JAVA_HOME` points to JDK 25+. On Windows the default JDK is often older;
set `JAVA_HOME` explicitly (e.g. `$env:JAVA_HOME = "$HOME\.jdks\jdk-25.0.2"`).

```bash
./gradlew build              # compile + unit tests + bootJar
./gradlew compileJava        # compile main sources only
./gradlew bootJar            # just the runnable jar -> build/libs/redmine-mcp-server.jar
./gradlew test               # unit tests; the `integration` JUnit tag is EXCLUDED
./gradlew integrationTest    # tests tagged `integration` — require live REDMINE_URL + REDMINE_API_KEY
./gradlew check              # test + any other verification tasks
./gradlew clean              # wipe build/
```

On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.

The `test` task in `build.gradle.kts` uses `excludeTags("integration")`. The `integrationTest`
task uses `includeTags("integration")` and `shouldRunAfter(tasks.test)`. Tag a JUnit test with
`@Tag("integration")` if it needs a real Redmine.

To smoke-test the server locally without an MCP client:

```bash
REDMINE_URL=https://redmine.example.com REDMINE_API_KEY=xxx \
  java -jar build/libs/redmine-mcp-server.jar
```

It will block waiting for JSON-RPC on stdin. Log lines appear on stderr **and** in the rolling
file `${REDMINE_MCP_DATA_DIR:-~/.redmine-mcp-server}/logs/redmine-mcp-server.log`.

---

## 4. Source layout

The package root is `ru.it_spectrum.ai.redmine.mcp`. Strict layering — dependencies flow
**downward** only:

```
tools/        →  service/  →  client/         (and  extraction/)
                              client/model/
api/  ← returned by tools and services as the MCP wire format
config/       — Spring @ConfigurationProperties, beans, MCP customizer
```

| Package | Responsibility | What goes here |
|---|---|---|
| `RedmineMcpServerApplication` | Spring Boot entry point. Empty by design. | Nothing. |
| `tools/` | Thin `@McpTool` / `@McpPrompt` adapters. Spring `@Service` beans. | One class per logical domain (`IssueTools`, `ProjectTools`, `AnalysisTools`, `IncidentPrompts`, …). Plus the shared `ToolLogger`. |
| `service/` | Business logic. Calls `RedmineClient`, maps `client.model.*` → `api.*`. | Domain services (`IssueService`, `AnalysisService`, `AttachmentService`, `IssueSnapshotService`, …) and the typed exceptions tools throw (`IssueNotFoundException`, `ResourceUnavailableException`, `AttachmentNotFoundException`, …). |
| `client/` | `RedmineClient` — wrapper over Redmine REST API using `RestClient`. | HTTP/JSON glue only. No domain decisions. |
| `client/model/` | Raw Redmine DTOs (mirror Redmine REST shape). | Add fields here when Redmine adds a field you need. **Never expose these on the MCP wire.** |
| `api/` | Stable MCP response records. `@Schema`-annotated for output-schema generation. | Add a new record here when you add a new tool. |
| `extraction/` | Document-to-text pipeline. | `ExtractionPipeline`, `DocumentParser` impls under `extraction/parser/`, `ExtractionLimits`, `FileTypeDetector`, `PandocAvailability`. |
| `config/` | Configuration. | `RedmineMcpProperties` (all knobs), `RedmineClientProperties` (url+key), `RedmineConfig` (RestClient bean), `McpServerConfig` (the stdio `immediateExecution` customizer), `JsonConfig` (ObjectMapper). |

Resources: `src/main/resources/application.yml` (config defaults), `logback-spring.xml`
(file + stderr appenders only — **no stdout appender**, see invariant #2).

Tests live under `src/test/java/.../` mirroring the main package. Shared helpers:
`TestRedmineMcpProperties`, `tools/ToolJsonTestSupport`, `extraction/ExtractionTestPipelines`.

---

## 5. Adding a new MCP tool

Concrete walkthrough — follow the pattern of `IssueTools#getIssue`.

1. **Decide the wire shape.** Add a record under `api/` annotated with `@Schema` on the
   class and each component. Required fields use `requiredMode = Schema.RequiredMode.REQUIRED`;
   anything that can legitimately be absent must be `nullable = true` (Jackson is configured
   with `NON_NULL` inclusion — nulls are dropped from JSON, but the schema must still permit
   them so MCP clients with strict validators do not choke).
2. **Add the logic to a service.** Put a new method on the relevant `*Service` in `service/`.
   The service calls `RedmineClient`, then maps the result to your new `api.*` record (or to
   an existing one). Services never reference `tools/`.
3. **Expose the tool.** Add a method to the appropriate `*Tools` class:

    ```java
    @McpTool(
        description = "<one to three sentences, written for the model that will call this>",
        generateOutputSchema = true,
        annotations = @McpTool.McpAnnotations(
            readOnlyHint = true, destructiveHint = false, idempotentHint = true)
    )
    public MyResponseType myTool(
        @McpToolParam(description = "...") int requiredArg,
        @McpToolParam(description = "...", required = false) Integer optionalArg
    ) {
        log.info("Tool call: myTool (requiredArg={}, optionalArg={})", requiredArg, optionalArg);
        long start = System.nanoTime();
        try {
            var result = myService.doIt(requiredArg, optionalArg);
            ToolLogger.completed(log, "myTool", start);
            return result;
        } catch (SomeKnownException e) {
            ToolLogger.failed(log, "myTool", start, e.getMessage());
            throw e;
        }
    }
    ```

   - Read pagination defaults from `properties.pagination()`, never hardcode `25` / `0`.
   - For "not found" paths, throw the typed exception from `service/` (`IssueNotFoundException`,
     `AttachmentNotFoundException`, `ResourceUnavailableException`, …). Spring AI MCP maps
     these to error responses; do not return a null or empty record as a substitute.
   - Always log `Tool call: <name> (...)` on entry and call `ToolLogger.completed` / `failed`
     on exit. Log format is consistent across the codebase.
4. **Test.** Add a unit test under `src/test/java/.../tools/` that mocks `RedmineClient`
   (and any other services you depend on). Use `ToolJsonTestSupport.stringify(result)` to
   assert against the *serialized JSON* — this catches Jackson misconfiguration and wire-shape
   regressions, not just Java equality. See `IssueToolsTest` for the pattern.
5. **Document the tool in README.md.** The README table is the user-facing catalogue;
   keep it in sync. Bump the "31 read-only MCP tools" count when adding or removing tools.

---

## 6. Adding a new MCP prompt

Prompts live in `tools/IncidentPrompts.java` (or a sibling class in the same package).
Pattern:

```java
@McpPrompt(
    name = "my-prompt",
    title = "Human-readable title",
    description = "One sentence on what this prompt does for the model."
)
public String myPrompt(
    @McpArg(name = "issueId", description = "...", required = true) int issueId
) {
    log.info("Prompt requested: my-prompt (issueId={})", issueId);
    return """
        ...the actual prompt body, addressed to the model that will execute it...
        """.formatted(issueId);
}
```

A prompt returns a **string template** that the MCP client renders as the conversation seed.
Inside the template, refer to tool names by their short form (`getIssue`, `getAttachment`) and
explicitly note that the client may need to prefix them with a server identifier. See
`IncidentPrompts#incidentBrief` for a working example.

---

## 7. Configuration knobs

All tunables live in `RedmineMcpProperties` (`config/RedmineMcpProperties.java`) and are
bound from the `redmine-mcp.*` block of `application.yml`. Each yml value uses an
env-var override of the form `${REDMINE_MCP_*:default}`.

To add a new knob:

1. Add a component to the relevant nested record (e.g. `Pagination`, `Analysis`, `Extraction`),
   with a `@DefaultValue` annotation and a compact-constructor sanity check.
2. Declare a `DEFAULT_*` constant in `RedmineMcpProperties` and use it from both the
   `@DefaultValue` and the compact constructor.
3. Add a line to `application.yml` under `redmine-mcp.<section>` referencing a
   `REDMINE_MCP_<UPPER_SNAKE>` env var.
4. Read it from your service / tool via `properties.<section>().<component>()`.
5. Add the env var to the table in `README.md` (Настройка section). Users read README,
   not this file.

`RedmineClientProperties` is separate and holds only the Redmine connection (`REDMINE_URL`,
`REDMINE_API_KEY`). Do not stuff feature knobs there.

The data directory is resolved by `RedmineMcpProperties#resolvedDataDir()`. Logs and issue
snapshots both live under it; never hardcode `${user.home}/.redmine-mcp-server`.

---

## 8. Document extraction pipeline

Located in `extraction/`. Implementations of `DocumentParser` are registered into
`ExtractionPipeline` and tried in order based on content type detected by `FileTypeDetector`.
Existing parsers (under `extraction/parser/`):

| Parser | Purpose |
|---|---|
| `PlainTextParser` | txt, log, csv, json, xml — direct UTF-8 read. |
| `PdfTextParser` | PDF via PDFBox (text-layer only; scans without OCR yield empty text). |
| `DocxTextParser`, `DocxPandocParser`, `DocxMediaExtractor`, `DocxEmbeddedExtractor` | DOCX via POI; pandoc path used when `extraction.pandoc.enabled` and `pandoc` is on PATH. |
| `XlsxTextParser`, `PptxTextParser` | XLSX / PPTX via POI. |
| `ZipParser` | ZIP — recursive but **depth-bounded** by `extraction.limits.max-depth` (default 1). |
| `ImagePassthroughParser` | Images — no text extracted; only `localPath`/`fileUri` exposed. |
| `TikaTextFallbackParser`, `TikaMetadataParser` | Tika fallback when nothing else matched. |
| `BinaryFallbackParser` | Last resort — no text, metadata only. |

When you add a parser:

- Implement `DocumentParser` (typically extending `AbstractDocumentParser`).
- Register it in the parser list inside `ExtractionPipeline` (order matters — first
  `canParse(...) == true` wins).
- **Respect `ExtractionLimits`**: `maxTotalBytes`, `maxTotalParts`, `maxEntryBytes`,
  `maxDepth`. Use `ParseSink#shouldStop()` to bail out early; do not buffer entire archives
  into memory.
- Apply the per-part char budget (`AttachmentExtraction.perPartChars`) before returning text.
  This is the layer that protects MCP clients from being flooded by a single huge document.

`PandocAvailability` probes for pandoc once at startup with a short timeout and caches the
result. Don't call `pandoc` from a parser directly — route through `DocxPandocParser`.

---

## 9. Runtime invariants (what would silently break things)

- **Don't write to `System.out`.** Stdout is the MCP transport. Always use the SLF4J logger.
  `logback-spring.xml` defines only `FILE` and `STDERR` appenders for that reason.
- **Don't switch tool execution to async/reactive.** `McpServerConfig` deliberately enables
  `immediateExecution(true)` to avoid concurrent stdout writes. Removing it reintroduces a
  bug fixed in commit `3138a4a`.
- **Issue snapshots persist on disk.** `IssueSnapshotService` writes
  `${dataDir}/issues/<id>/issue.json`, `snapshot.json`, `attachments.json`, and
  `attachments/<id>__<filename>`. Treat the layout as a contract — other tools (especially
  `getAttachment`) return `localPath` values pointing into it. Don't rename directories
  without updating `IssueSnapshotService` and the affected services together.
- **Pagination defaults are configurable, not constants.** Always read from
  `properties.pagination().defaultLimit()` / `defaultOffset()`. Hardcoded 25/0 in tools
  will be wrong as soon as a user overrides them.
- **Attachment text is budget-bounded.** `getAttachment` uses
  `attachment.per-part-chars` / `per-attachment-chars`. New tools that surface attachment
  text must reuse this budget rather than inventing a parallel one.

---

## 10. Coding conventions

- **Records for DTOs.** Both `api/*` and most `client/model/*` are Java records. Add new
  fields as record components, not setters.
- **Jackson `NON_NULL` is global.** Configured in `JsonConfig#redmineMcpObjectMapper`.
  Null fields are dropped from JSON; design records to use `null` for "absent" rather than
  empty strings or sentinel zeros.
- **Schema annotations matter.** `generateOutputSchema = true` on `@McpTool` triggers
  JSON-Schema generation from your `api.*` record. Use `@Schema(nullable = true)` for any
  optional component, and `requiredMode = REQUIRED` for ones the consumer can always rely on.
  Get this wrong and strict MCP clients reject responses at runtime.
- **Exceptions over null returns at the tool boundary.** Services often return `Optional<T>`;
  tools unwrap and throw a typed exception (`IssueNotFoundException`, …) when empty. This
  produces a clean MCP error response.
- **Logging format.** `log.info("Tool call: <name> (k1={}, k2={})", ...)` on entry,
  `ToolLogger.completed/failed` on exit. Don't invent variants.
- **No abbreviations in tool / parameter descriptions.** They are read by language models
  picking which tool to call. Be explicit; the cost of a few extra words is paid once at
  authoring time, the benefit is paid every call.
- **Tests assert on JSON, not Java equality.** Use `ToolJsonTestSupport.stringify(result)`
  and `assertThat(json).contains(...)`. This catches Jackson misconfigurations that pure
  Java equality misses.

---

## 11. Things NOT to do

- **Do not add write/mutation tools.** No `createIssue`, `updateIssue`, `addNote`, etc.
  This is intentional — see the `Эксплуатация и безопасность` section of README.md for
  the security rationale. If a user genuinely needs write access, that is a design
  conversation, not a code change.
- **Do not leak `client/model/*` types onto the MCP wire.** They mirror Redmine's REST
  schema and change when Redmine changes. Map to an `api.*` record at the service boundary.
- **Do not put feature flags in `RedmineClientProperties`.** It is reserved for the
  Redmine connection. Put feature knobs in `RedmineMcpProperties`.
- **Do not hardcode the data directory or its subpaths.** Always go through
  `properties.resolvedDataDir()`.
- **Do not depend on the developer's home directory in tests.** Use `@TempDir` or
  `TestRedmineMcpProperties` overrides.
- **Do not edit README.md when you mean to update internal architecture notes.** README is
  user-facing (product description, tool catalogue, env vars). AGENTS.md is for engineering
  context. They serve different audiences.

---

## 12. Where to look for more

- **README.md** — product overview, full tool catalogue, env-var table, security model,
  build, smoke-test, client connection, troubleshooting. The first place to look when a
  user asks "what does this server do?" or "how do I install it?".
- **`build.gradle.kts` + `gradle/libs.versions.toml`** — the source of truth for dependency
  versions and the `integrationTest` task definition.
- **`application.yml`** — every knob the server has, with its env-var override name.
- **Recent commits** — many runtime decisions are non-obvious. Notable commits:
  - `3138a4a` — why stdio MCP uses `immediateExecution(true)` (stdout race).
  - `4890f73` — addition of the `incident-brief` MCP prompt.
  - `1410e66` — per-attachment / per-part char budgets for `getAttachment`.
  - `d2aaf37` — schema relaxation for optional fields (the reason `nullable = true` is
    important on `api/*` records).

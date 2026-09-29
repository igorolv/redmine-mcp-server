# docs/architecture-agent.md — source layout, technology and layer boundaries

Read this when locating code, changing a dependency or changing the server transport and Spring wiring.

## Transport and execution

`spring.main.web-application-type: none` keeps the server on stdio only. `System.out` is the
JSON-RPC channel; use SLF4J instead. `logback-spring.xml` has only `FILE` and `STDERR`
appenders. `McpServerConfig` sets `immediateExecution(true)` on the `McpSyncServer` builder to
avoid a stdout write race when boundedElastic tool completions finish concurrently (commit
`3138a4a`). Re-evaluate that race before changing execution to async/reactive.

## Tech stack and version sources

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

## Source layout

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
| `tools/` | Thin `@McpTool` / `@McpPrompt` adapters. Spring `@Service` beans. | One class per logical domain / toggle group (`IssueTools`, `IssueStructureTools`, `ProjectTools`, `IssueAnalyticsTools`, `ReleaseAnalyticsTools`, `IncidentPrompts`, …). Plus the shared `ToolLogger`. |
| `service/` | Business logic. Calls Redmine clients, maps `client.model.*` → `api.*`. | Domain services (`IssueService`, `IssueMutationService`, `TimeEntryMutationService`, `AnalysisService`, `AttachmentService`, `IssueSnapshotService`, …) and the typed exceptions tools throw (`IssueNotFoundException`, `ResourceUnavailableException`, `AttachmentNotFoundException`, …). |
| `client/` | `RedmineClient` for reads and write-mode `RedmineMutationClient` for approved writes, both using `RestClient`. | HTTP/JSON glue only. No domain decisions. |
| `client/model/` | Raw Redmine DTOs (mirror Redmine REST shape). | Add fields here when Redmine adds a field you need. **Never expose these on the MCP wire.** |
| `api/` | Stable MCP response records. `@Schema`-annotated for output-schema generation. | Add a new record here when you add a new tool. |
| `extraction/` | Document-to-text pipeline. | `ExtractionPipeline`, `DocumentParser` impls under `extraction/parser/`, `ExtractionLimits`, `FileTypeDetector`, `PandocAvailability`. |
| `config/` | Configuration. | `RedmineMcpProperties` (all knobs), `RedmineClientProperties` (url+key), `RedmineConfig` (RestClient bean), `McpServerConfig` (the stdio `immediateExecution` customizer), `JsonConfig` (ObjectMapper). |

Resources: `src/main/resources/application.yml` (config defaults), `logback-spring.xml`
(file + stderr appenders only — **no stdout appender**, as required by the stdio transport).

Tests live under `src/test/java/.../` mirroring the main package. Shared helpers:
`TestRedmineMcpProperties`, `tools/ToolJsonTestSupport`, `extraction/ExtractionTestPipelines`.

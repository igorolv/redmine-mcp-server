# docs/tool-contracts-agent.md — MCP tool and prompt authoring

Read this when adding, changing or reviewing a tool, prompt, API record, schema or tool group.

Pagination defaults come from `properties.pagination().defaultLimit()` and
`defaultOffset()`; never hardcode `25` or `0` in a tool.

## Adding a new MCP tool

Concrete walkthrough — follow the pattern of `IssueTools#getIssue`.

1. **Decide the wire shape.** Add a record under `api/` annotated with `@Schema` on the
   class and each component. Required fields use `requiredMode = Schema.RequiredMode.REQUIRED`;
   anything that can legitimately be absent must be `nullable = true` (Jackson is configured
   with `NON_NULL` inclusion — nulls are dropped from JSON, but the schema must still permit
   them so MCP clients with strict validators do not choke).
2. **Add the logic to a service.** Put a new method on the relevant `*Service` in `service/`.
   The service calls `RedmineClient` (or, only for an approved opt-in write, `RedmineMutationClient`),
   then maps the result to your new `api.*` record (or to an existing one). Services never reference
   `tools/`.
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
   - Read tools use `readOnlyHint = true`. Write annotations must accurately describe mutability,
     destructiveness, and idempotency; never copy the read-only annotation onto a mutation.
4. **Test.** Add a unit test under `src/test/java/.../tools/` that mocks `RedmineClient`
   (and any other services you depend on). Use `ToolJsonTestSupport.stringify(result)` to
   assert against the *serialized JSON* — this catches Jackson misconfiguration and wire-shape
   regressions, not just Java equality. See `IssueToolsTest` for the pattern.
5. **Document the tool in README.md.** The README table is the user-facing catalogue;
   keep it and the default/optional tool counts in sync.

### Tool group gating

Each `*Tools` `@Service` is gated by `@ConditionalOnProperty(prefix = "redmine-mcp.tools",
name = "<group>", havingValue = "true", matchIfMissing = true)`. All groups are **on by default**
(`matchIfMissing = true`), so the out-of-the-box manifest is unchanged; operators turn a group off
(e.g. `REDMINE_MCP_TOOLS_RELEASE_ANALYTICS=false`) to shrink the `tools/list` manifest for small-context
models. The group name is the kebab-case domain (`issue`, `issue-structure`, `project`, `search`,
`attachment`, `wiki`, `time-entry`, `reference-data`, `user`, `issue-analytics`,
`release-analytics`). `IncidentPrompts` is **not** gated — prompts stay always available.

`IssueWriteTools`, `TimeEntryWriteTools`, and `WikiWriteTools` are deliberate exceptions: they are gated by
`redmine-mcp.write.enabled` / `REDMINE_MCP_WRITE_ENABLED`, which defaults to `false`, and must not be
folded into a default-on `redmine-mcp.tools.*` group.

When you add a **new tool class** (not just a method on an existing one):

1. Annotate it with `@ConditionalOnProperty` using a new `redmine-mcp.tools.<group>` name.
2. Add the flag to `application.yml` under `redmine-mcp.tools` (default `true`, with a
   `REDMINE_MCP_TOOLS_<UPPER_SNAKE>` env override).
3. Add a row to the *Tool Groups (enable/disable)* table in `README.md`.
4. Extend `ToolGroupConditionTest` (an `ApplicationContextRunner` test, no live Redmine) to cover
   the new group's default-on and toggled-off paths.

## Adding a new MCP prompt

Prompts live in `tools/IncidentPrompts.java` (or a sibling class in the same package).
Pattern:

```java
@McpPrompt(
    name = "my-prompt",
    title = "Human-readable title",
    description = "One sentence on what this prompt does for the model."
)
public String myPrompt(
    @McpArg(name = "issueId", description = ISSUE_ID_DESCRIPTION, required = true) String issueId
) {
    log.info("Prompt requested: my-prompt (issueId={})", issueId);
    String id = issueIdForTemplate(issueId);
    return """
        ...the actual prompt body, addressed to the model that will execute it...

        %2$s
        """.formatted(id, PREAMBLE);
}
```

A prompt returns a **string template** that the MCP client renders as the conversation seed.
Inside the template, refer to tool names by their short form (`getIssue`, `getAttachment`) and
include the shared `PREAMBLE`, which covers server prefixes, code-mode `tools.<server>.<tool>` access,
disabled tool groups, and the no-writes rule. See `IncidentPrompts#incidentBrief` for a working example.

Prompt arguments are **always declared as `String`**. MCP sends prompt arguments as strings, and
Spring AI converts them with `Integer.parseInt` for an `int` parameter. Some clients request a prompt
with a placeholder instead of a value — opencode 1.x sends `$1` for every argument while building its
command list and substitutes the real value client-side later. `IncidentPrompts#issueIdForTemplate`
passes `$N` / `$ARGUMENTS` through verbatim, normalizes `#123` to `123`, and throws
`IllegalArgumentException` for anything else, which reaches the client as a `-32602` error with the
message. Do not change an argument back to `int`.

`IncidentPromptsTest` checks that every `tool(param=...)` call in a prompt text names an existing
`@McpTool` method and parameter and that every `focus="..."` value is valid. Write tool calls in the
templates in that `tool(param=value)` form so the check covers them.

## Coding conventions

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
- **Tool descriptions consume model context.** Every `@McpTool.description` and
  `@McpToolParam.description` is part of the `tools/list` manifest and repeatedly inflates the
  model context, even when that tool is not called. Treat every word as a recurring cost. Keep
  only facts needed to select the tool or form valid arguments: non-obvious constraints, accepted
  formats, lookup sources, and surprising semantics. Use an empty parameter description when its
  name, Java type, and `required` flag are sufficient. Never restate the parameter name/type,
  optionality, internal markers, enforced defaults/caps, or the same rule on multiple parameters.
  Avoid cryptic abbreviations, but do not expand text that adds no calling signal.
- **Tests assert on JSON, not Java equality.** Use `ToolJsonTestSupport.stringify(result)`
  and `assertThat(json).contains(...)`. This catches Jackson misconfigurations that pure
  Java equality misses.

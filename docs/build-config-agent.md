# docs/build-config-agent.md — Gradle verification and configuration contracts

Read this before building, running tests or adding a configuration property.

## Build, run, test

The build tool is **Gradle** (wrapper checked in as `./gradlew` / `gradlew.bat`). Always drive
builds, tests, and the runnable jar through the Gradle wrapper — every command below is the
canonical invocation.

The build needs a JDK 25 toolchain. Gradle first looks for one already installed; if none is
found it downloads one itself (the `foojay-resolver-convention` plugin in `settings.gradle.kts`
provides the toolchain repository, so `api.foojay.io` must be reachable for that fallback).
To use a specific local JDK instead, point `JAVA_HOME` at it. On Windows the default JDK is
often older; set `JAVA_HOME` explicitly (e.g. `$env:JAVA_HOME = "$HOME\.jdks\jdk-25.0.2"`).

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

## Configuration knobs

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
5. Add the env var to the table in `README.md` (Configuration section). Users read README,
   not this file.

`RedmineClientProperties` is separate and holds only the Redmine connection (`REDMINE_URL`,
`REDMINE_API_KEY`). Do not stuff feature knobs there.

The data directory is resolved by `RedmineMcpProperties#resolvedDataDir()`. Logs and issue
snapshots both live under it; never hardcode `${user.home}/.redmine-mcp-server`.

Tests must not depend on the developer's home directory. Use `@TempDir` or
`TestRedmineMcpProperties` overrides.

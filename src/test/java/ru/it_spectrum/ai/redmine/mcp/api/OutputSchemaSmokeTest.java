package ru.it_spectrum.ai.redmine.mcp.api;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sanity check that {@code @Schema} annotations on the api DTOs are picked up by
 * Spring AI's MCP schema generator and produce LLM-friendly output schemas
 * (per-field descriptions, camelCase property names, ISO-8601 formats, allowable
 * values for enum-like strings).
 */
class OutputSchemaSmokeTest {

    @Test
    void issueSchemaShouldExposeFieldDescriptionsAndDateTimeFormat() {
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(Issue.class);
        assertThat(schemaJson).contains("\"subject\"");
        assertThat(schemaJson).contains("Short title of the issue");
        assertThat(schemaJson).contains("\"createdOn\"");
        assertThat(schemaJson).contains("\"format\" : \"date-time\"");
        // Nested type descriptions are emitted into $defs.
        assertThat(schemaJson).contains("\"description\" : \"Reference to a Redmine entity");
    }

    @Test
    void issuePageSchemaShouldExposeCamelCaseProperties() {
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(IssuePage.class);
        assertThat(schemaJson).contains("\"totalCount\"");
        assertThat(schemaJson).doesNotContain("\"total_count\"");
        assertThat(schemaJson).contains("Total number of issues matching the query");
    }

    @Test
    void issueFullContextSchemaShouldAdvertiseContextRoleEnum() {
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(IssueFullContext.class);
        assertThat(schemaJson).contains("\"contextIssues\"");
        // The role @Schema(allowableValues = {...}) lands as JSON Schema "enum".
        assertThat(schemaJson).contains("\"parent\"");
        assertThat(schemaJson).contains("\"sibling\"");
        assertThat(schemaJson).contains("\"child\"");
        assertThat(schemaJson).contains("\"related\"");
    }

    @Test
    void userSchemaShouldNotLeakRedmineInternals() {
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(User.class);
        // None of these field names should appear as schema properties.
        assertThat(schemaJson).doesNotContain("\"apiKey\"");
        assertThat(schemaJson).doesNotContain("\"memberships\"");
        assertThat(schemaJson).doesNotContain("\"groups\"");
        assertThat(schemaJson).doesNotContain("\"lastLoginOn\"");
        assertThat(schemaJson).contains("\"login\"");
        assertThat(schemaJson).contains("\"name\"");
    }

    @Test
    void issueSchemaShouldHaveTopLevelDescription() {
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(Issue.class);
        assertThat(schemaJson).contains("Full Redmine issue with description");
    }
}

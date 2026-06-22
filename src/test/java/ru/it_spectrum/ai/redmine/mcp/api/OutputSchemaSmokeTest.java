package ru.it_spectrum.ai.redmine.mcp.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

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

    @Test
    void contextRoleKindSchemaShouldUseWireValues() {
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(Issue.class);

        assertThat(schemaJson).contains("\"parent\"");
        assertThat(schemaJson).contains("\"sibling\"");
        assertThat(schemaJson).contains("\"child\"");
        assertThat(schemaJson).contains("\"related\"");
        assertThat(schemaJson).doesNotContain("\"PARENT\"");
        assertThat(schemaJson).doesNotContain("\"SIBLING\"");
        assertThat(schemaJson).doesNotContain("\"CHILD\"");
        assertThat(schemaJson).doesNotContain("\"RELATED\"");
    }

    @Test
    void nonRequiredFieldsShouldAllowNull() {
        // After the mass `nullable = true` migration, optional fields like `category`,
        // `parent`, `dueDate`, `estimatedHours`, `children` must declare `null` in their
        // JSON Schema type so the MCP client accepts null values returned by Redmine.
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(Issue.class);
        // Scalar nullable types collapse to `[ "string", "null" ]` etc.
        assertThat(schemaJson).contains("[ \"string\", \"null\" ]");
        assertThat(schemaJson).contains("[ \"array\", \"null\" ]");
        assertThat(schemaJson).contains("[ \"number\", \"null\" ]");
        assertThat(schemaJson).contains("[ \"integer\", \"null\" ]");
        // Nullable Ref fields are emitted as a `$ref` to a `-nullable` variant.
        assertThat(schemaJson).contains("#/$defs/Ref-nullable");
    }

    @Test
    void omittedNullableIssueFieldsShouldNotBeRequired() throws Exception {
        var schemaJson = McpJsonSchemaGenerator.generateFromClass(Issue.class);
        var requiredFields = collectRequiredFields(new ObjectMapper().readTree(schemaJson));

        assertThat(requiredFields).contains("id", "doneRatio");
        assertThat(requiredFields).doesNotContain(
                "children",
                "comments",
                "committedOn",
                "details",
                "dueDate",
                "estimatedHours",
                "fixedVersion",
                "oldValue",
                "parent",
                "relations",
                "user"
        );
    }

    @Test
    void nullableSchemaFieldsShouldNotBeMarkedRequired() throws Exception {
        var forbidden = Pattern.compile(
                "requiredMode\\s*=\\s*Schema\\.RequiredMode\\.REQUIRED\\s*,\\s*nullable\\s*=\\s*true"
                        + "|nullable\\s*=\\s*true\\s*,\\s*requiredMode\\s*=\\s*Schema\\.RequiredMode\\.REQUIRED");

        try (var files = Files.walk(Path.of("src/main/java/ru/it_spectrum/ai/redmine/mcp/api"))) {
            var offenders = files
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return forbidden.matcher(Files.readString(path)).find();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(Path::toString)
                    .toList();

            assertThat(offenders).isEmpty();
        }
    }

    private static Set<String> collectRequiredFields(JsonNode node) {
        var fields = new LinkedHashSet<String>();
        collectRequiredFields(node, fields);
        return fields;
    }

    private static void collectRequiredFields(JsonNode node, Set<String> fields) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            var required = node.get("required");
            if (required != null && required.isArray()) {
                required.forEach(value -> fields.add(value.asText()));
            }
            node.properties().forEach(entry -> collectRequiredFields(entry.getValue(), fields));
        } else if (node.isArray()) {
            node.forEach(value -> collectRequiredFields(value, fields));
        }
    }
}

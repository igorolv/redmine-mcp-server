package ru.it_spectrum.ai.redmine.mcp.api;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import ru.it_spectrum.ai.redmine.mcp.config.JsonConfig;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the {@link Opaque} contract: an {@code Opaque<T>} field collapses to an unconstrained
 * schema (keeping its description) yet serializes its payload fully and unwrapped, is omitted
 * entirely when null under the NON_NULL server mapper, and round-trips back to a typed value
 * through the application mapper.
 */
class OpaqueTest {

    @Schema(description = "Rich payload that must NOT appear expanded in the schema.")
    record Rich(
            @Schema(description = "first") String a,
            @Schema(description = "second") int b) {}

    record Holder(
            @Schema(description = "opaque blob", nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Opaque<Rich> payload,
            @Schema(description = "kept", nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            String kept) {}

    record ListHolder(
            @Schema(description = "opaque list", nullable = true,
                    requiredMode = Schema.RequiredMode.NOT_REQUIRED)
            Opaque<List<Rich>> items) {}

    private static final ObjectMapper MAPPER = new JsonConfig().redmineMcpObjectMapper();

    @Test
    void schemaCollapsesButKeepsDescription() {
        String schema = McpJsonSchemaGenerator.generateFromClass(Holder.class);

        // The opaque field's description survives...
        assertThat(schema).contains("opaque blob");
        // ...but Rich's own fields never expand into the schema.
        assertThat(schema).doesNotContain("\"first\"").doesNotContain("\"second\"");
    }

    @Test
    void serializesPayloadFullyAndUnwrapped() {
        String json = MAPPER.writeValueAsString(new Holder(Opaque.of(new Rich("x", 7)), "k"));
        assertThat(json).isEqualTo("{\"payload\":{\"a\":\"x\",\"b\":7},\"kept\":\"k\"}");
    }

    @Test
    void nullPayloadIsOmitted() {
        assertThat(Opaque.of(null)).isNull();
        String json = MAPPER.writeValueAsString(new Holder(Opaque.of(null), "k"));
        assertThat(json).isEqualTo("{\"kept\":\"k\"}");
    }

    @Test
    void roundTripsToTypedValueThroughMapper() {
        Holder one = new Holder(Opaque.of(new Rich("x", 7)), "k");
        Holder backOne = MAPPER.readValue(MAPPER.writeValueAsString(one), Holder.class);
        // Reconstructed value must be a real typed Rich, not a raw Map.
        Rich rich = backOne.payload().unwrap();
        assertThat(rich).isEqualTo(new Rich("x", 7));

        ListHolder many = new ListHolder(Opaque.of(List.of(new Rich("y", 9), new Rich("z", 11))));
        ListHolder backMany = MAPPER.readValue(MAPPER.writeValueAsString(many), ListHolder.class);
        assertThat(backMany.items().unwrap()).containsExactly(new Rich("y", 9), new Rich("z", 11));
    }
}

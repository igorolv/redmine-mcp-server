package ru.it_spectrum.ai.redmine.mcp.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonConfigTest {

    @Test
    void objectMapperOmitsNullProperties() throws Exception {
        var mapper = new JsonConfig().redmineMcpObjectMapper();
        var json = mapper.writeValueAsString(new Payload(
                "value",
                null,
                List.of("first", "second"),
                List.of(new Nested("nested", null))
        ));

        assertThat(json).contains("\"required\":\"value\"");
        assertThat(json).doesNotContain("optional");
        assertThat(json).contains("\"values\":[\"first\",\"second\"]");
        assertThat(json).contains("\"items\":[{\"name\":\"nested\"}]");
        assertThat(json).doesNotContain(":null");
    }

    private record Payload(String required, String optional, List<String> values, List<Nested> items) {
    }

    private record Nested(String name, String note) {
    }
}

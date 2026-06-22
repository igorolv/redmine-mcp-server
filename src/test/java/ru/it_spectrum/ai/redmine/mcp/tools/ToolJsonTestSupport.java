package ru.it_spectrum.ai.redmine.mcp.tools;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import ru.it_spectrum.ai.redmine.mcp.config.JsonConfig;

final class ToolJsonTestSupport {

    private static final ObjectMapper MAPPER = new JsonConfig().redmineMcpObjectMapper();

    private ToolJsonTestSupport() {
    }

    static JsonNode parse(Object value) throws Exception {
        if (value instanceof String json) {
            return MAPPER.readTree(json);
        }
        return MAPPER.valueToTree(value);
    }

    static String stringify(Object value) {
        try {
            if (value instanceof String json) {
                return json;
            }
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new AssertionError("Invalid JSON", e);
        }
    }
}

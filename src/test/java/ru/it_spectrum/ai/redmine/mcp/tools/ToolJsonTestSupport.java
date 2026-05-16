package ru.it_spectrum.ai.redmine.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ToolJsonTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

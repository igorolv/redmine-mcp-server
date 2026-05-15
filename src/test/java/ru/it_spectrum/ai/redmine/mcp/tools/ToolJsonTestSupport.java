package ru.it_spectrum.ai.redmine.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

final class ToolJsonTestSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonResponses JSON = new JsonResponses(MAPPER);
    private static final ToolErrors ERRORS = new ToolErrors(JSON);

    private ToolJsonTestSupport() {
    }

    static JsonResponses json() {
        return JSON;
    }

    static ToolErrors errors() {
        return ERRORS;
    }

    static JsonNode parse(String value) throws Exception {
        return MAPPER.readTree(value);
    }
}

package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.stereotype.Component;

@Component
public class ToolErrors {

    private final JsonResponses json;

    public ToolErrors(JsonResponses json) {
        this.json = json;
    }

    public String argument(String message) {
        return error("argument", message);
    }

    public String notFound(String resource, Object id) {
        return error("not_found", "%s %s not found".formatted(resource, id));
    }

    public String unavailable(String resource) {
        return error("unavailable", "%s unavailable".formatted(resource));
    }

    private String error(String kind, String message) {
        return json.write(new ToolErrorResponse(kind, message));
    }

    private record ToolErrorResponse(String kind, String error) {
    }
}

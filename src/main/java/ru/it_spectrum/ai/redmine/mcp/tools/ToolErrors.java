package ru.it_spectrum.ai.redmine.mcp.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ToolErrors {

    private static final Logger log = LoggerFactory.getLogger(ToolErrors.class);

    private final JsonResponses json;

    public ToolErrors(JsonResponses json) {
        this.json = json;
    }

    public String argument(String message) {
        log.warn("Tool error [kind=argument]: {}", message);
        return error("argument", message);
    }

    public String notFound(String resource, Object id) {
        log.warn("Tool error [kind=not_found]: {} {} not found", resource, id);
        return error("not_found", "%s %s not found".formatted(resource, id));
    }

    public String unavailable(String resource) {
        log.warn("Tool error [kind=unavailable]: {} unavailable", resource);
        return error("unavailable", "%s unavailable".formatted(resource));
    }

    private String error(String kind, String message) {
        return json.write(new ToolErrorResponse(kind, message));
    }

    private record ToolErrorResponse(String kind, String error) {
    }
}

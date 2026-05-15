package ru.it_spectrum.ai.redmine.mcp.service;

public class ResourceNotFoundException extends RuntimeException {
    private final String resource;
    private final Object id;

    public ResourceNotFoundException(String resource, Object id) {
        super("%s %s not found".formatted(resource, id));
        this.resource = resource;
        this.id = id;
    }

    public String resource() {
        return resource;
    }

    public Object id() {
        return id;
    }
}

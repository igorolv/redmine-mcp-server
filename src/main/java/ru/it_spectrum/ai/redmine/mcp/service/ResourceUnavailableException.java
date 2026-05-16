package ru.it_spectrum.ai.redmine.mcp.service;

public class ResourceUnavailableException extends RuntimeException {
    private final String resource;

    public ResourceUnavailableException(String resource) {
        super("%s unavailable".formatted(resource));
        this.resource = resource;
    }

    public String resource() {
        return resource;
    }
}

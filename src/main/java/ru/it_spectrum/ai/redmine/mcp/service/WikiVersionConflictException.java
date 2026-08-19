package ru.it_spectrum.ai.redmine.mcp.service;

public class WikiVersionConflictException extends RuntimeException {

    public WikiVersionConflictException(String pageTitle, int expectedVersion) {
        super("wiki page %s is no longer at version %d; call getWikiPage and retry with its current version"
                .formatted(pageTitle, expectedVersion));
    }
}

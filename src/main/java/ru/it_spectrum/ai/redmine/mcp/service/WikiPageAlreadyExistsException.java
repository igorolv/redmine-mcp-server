package ru.it_spectrum.ai.redmine.mcp.service;

public class WikiPageAlreadyExistsException extends RuntimeException {

    public WikiPageAlreadyExistsException(String projectId, String pageTitle) {
        super("wiki page %s already exists in project %s".formatted(pageTitle, projectId));
    }
}

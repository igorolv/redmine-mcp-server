package ru.it_spectrum.ai.redmine.mcp.service;

public class WikiWriteVerificationException extends RuntimeException {

    public WikiWriteVerificationException(String projectId, String pageTitle) {
        super(message(projectId, pageTitle));
    }

    public WikiWriteVerificationException(String projectId, String pageTitle, Throwable cause) {
        super(message(projectId, pageTitle), cause);
    }

    private static String message(String projectId, String pageTitle) {
        return "Redmine accepted the write, but wiki page %s in project %s could not be read back with the expected text; verify it before retrying"
                .formatted(pageTitle, projectId);
    }
}

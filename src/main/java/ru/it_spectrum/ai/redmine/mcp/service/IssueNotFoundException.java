package ru.it_spectrum.ai.redmine.mcp.service;

public class IssueNotFoundException extends RuntimeException {

    private final int issueId;

    public IssueNotFoundException(int issueId) {
        super("Issue #%d not found".formatted(issueId));
        this.issueId = issueId;
    }

    public int issueId() {
        return issueId;
    }
}

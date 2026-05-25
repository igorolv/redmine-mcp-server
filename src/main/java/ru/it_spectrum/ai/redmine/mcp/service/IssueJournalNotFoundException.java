package ru.it_spectrum.ai.redmine.mcp.service;

public class IssueJournalNotFoundException extends RuntimeException {

    private final int issueId;
    private final int journalId;

    public IssueJournalNotFoundException(int issueId, int journalId) {
        super("Journal #%d not found in issue #%d".formatted(journalId, issueId));
        this.issueId = issueId;
        this.journalId = journalId;
    }

    public int issueId() {
        return issueId;
    }

    public int journalId() {
        return journalId;
    }
}

package ru.it_spectrum.ai.redmine.mcp.model;

public record IssueContextRole(
        String role,
        String relationType,
        Integer relationId,
        Integer sourceIssueId,
        Integer targetIssueId,
        Integer delay
) {
}

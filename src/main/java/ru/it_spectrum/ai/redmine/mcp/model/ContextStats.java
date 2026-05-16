package ru.it_spectrum.ai.redmine.mcp.model;

public record ContextStats(
        boolean siblingsTruncated,
        boolean childrenTruncated,
        boolean relatedTruncated
) {
}

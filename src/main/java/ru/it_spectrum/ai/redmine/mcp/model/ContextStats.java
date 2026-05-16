package ru.it_spectrum.ai.redmine.mcp.model;

public record ContextStats(
        int siblingsTotal,
        int siblingsFetched,
        int siblingsClosedFetched,
        int childrenTotal,
        int childrenFetched,
        int relatedTotal,
        int relatedFetched,
        boolean siblingsTruncated,
        boolean childrenTruncated,
        boolean relatedTruncated
) {
}

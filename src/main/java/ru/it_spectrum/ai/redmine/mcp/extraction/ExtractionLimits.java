package ru.it_spectrum.ai.redmine.mcp.extraction;

/**
 * Per-pipeline safety limits. Defaults mirror the legacy archive limits.
 */
public record ExtractionLimits(
        int maxDepth,
        int maxTotalParts,
        long maxTotalBytes,
        long maxEntryBytes
) {

    public static ExtractionLimits defaults() {
        return new ExtractionLimits(
                1,
                100,
                50L * 1024 * 1024,
                10L * 1024 * 1024
        );
    }
}

package ru.it_spectrum.ai.redmine.mcp.extraction;

import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

/**
 * Per-pipeline safety limits.
 */
public record ExtractionLimits(
        int maxDepth,
        int maxTotalParts,
        long maxTotalBytes,
        long maxEntryBytes
) {

    public static ExtractionLimits from(RedmineMcpProperties.Limits properties) {
        return new ExtractionLimits(
                properties.maxDepth(),
                properties.maxTotalParts(),
                properties.maxTotalBytes(),
                properties.maxEntryBytes()
        );
    }
}

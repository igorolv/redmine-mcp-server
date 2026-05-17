package ru.it_spectrum.ai.redmine.mcp.extraction;

import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

/**
 * Shared, per-extraction state: limits and (later) pandoc / tika availability.
 */
public record ParseContext(
        ExtractionLimits limits
) {

    public static ParseContext from(RedmineMcpProperties.Extraction properties) {
        return new ParseContext(ExtractionLimits.from(properties.limits()));
    }
}

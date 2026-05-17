package ru.it_spectrum.ai.redmine.mcp.extraction;

/**
 * Shared, per-extraction state: limits and (later) pandoc / tika availability.
 */
public record ParseContext(
        ExtractionLimits limits
) {

    public static ParseContext defaults() {
        return new ParseContext(ExtractionLimits.defaults());
    }
}

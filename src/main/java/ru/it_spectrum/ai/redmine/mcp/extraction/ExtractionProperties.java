package ru.it_spectrum.ai.redmine.mcp.extraction;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the extraction pipeline. Bound from {@code redmine-mcp.extraction.*}.
 */
@ConfigurationProperties(prefix = "redmine-mcp.extraction")
public record ExtractionProperties(
        @DefaultValue Pandoc pandoc
) {

    public record Pandoc(
            @DefaultValue("true") boolean enabled
    ) {
    }
}

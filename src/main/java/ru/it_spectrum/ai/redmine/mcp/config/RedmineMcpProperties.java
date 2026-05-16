package ru.it_spectrum.ai.redmine.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redmine-mcp")
public record RedmineMcpProperties(
        String dataDir
) {
}

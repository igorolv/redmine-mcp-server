package ru.it_spectrum.ai.redmine.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "redmine")
public record RedmineProperties(
        String url,
        String apiKey
) {
}

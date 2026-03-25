package ru.it_spectrum.ai.redmine.mcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(RedmineProperties.class)
public class RedmineConfig {

    @Bean
    public RestClient redmineRestClient(RedmineProperties properties) {
        String url = properties.url();
        if (url != null && !url.isBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        return RestClient.builder()
                .baseUrl(url)
                .defaultHeader("X-Redmine-API-Key", properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}

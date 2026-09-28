package ru.it_spectrum.ai.redmine.mcp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineHttpLoggingInterceptor;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties({RedmineClientProperties.class, RedmineMcpProperties.class})
public class RedmineConfig {

    @Bean
    public RestClient redmineRestClient(RedmineClientProperties properties, RedmineMcpProperties mcpProperties) {
        String url = properties.url();
        if (url != null && !url.isBlank() && !url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        var http = mcpProperties.http();
        var httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(http.connectTimeoutSeconds()))
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(http.readTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(requestFactory)
                .requestInterceptor(new RedmineHttpLoggingInterceptor(http.slowRequestWarnMillis()))
                .defaultHeader("X-Redmine-API-Key", properties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}

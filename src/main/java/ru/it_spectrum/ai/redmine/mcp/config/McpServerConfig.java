package ru.it_spectrum.ai.redmine.mcp.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.customizer.McpSyncServerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stdio MCP uses a single stdout stream. Keeping synchronous tool execution immediate prevents
 * concurrent boundedElastic tool completions from racing while enqueueing responses.
 */
@Configuration
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    @Bean
    McpSyncServerCustomizer stdioSyncServerCustomizer() {
        return serverBuilder -> {
            log.info("Applying MCP sync server customization: immediateExecution=true");
            serverBuilder.immediateExecution(true);
        };
    }
}

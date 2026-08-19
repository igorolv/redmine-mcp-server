package ru.it_spectrum.ai.redmine.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RedmineMcpPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void bindsRecordThroughCanonicalConstructor() {
        runner.withPropertyValues(
                        "redmine-mcp.data-dir=build/test-data",
                        "redmine-mcp.write.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    var properties = context.getBean(RedmineMcpProperties.class);
                    assertThat(properties.dataDir()).isEqualTo("build/test-data");
                    assertThat(properties.write().enabled()).isTrue();
                    assertThat(properties.pagination().defaultLimit())
                            .isEqualTo(RedmineMcpProperties.DEFAULT_PAGE_LIMIT);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RedmineMcpProperties.class)
    static class TestConfiguration {
    }
}

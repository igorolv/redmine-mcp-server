package ru.it_spectrum.ai.redmine.mcp.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
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

    @Test
    void writePrefixesDefaultToIssueDescriptionMarkerOnly() {
        runner.withPropertyValues("redmine-mcp.write.enabled=true")
                .run(context -> {
                    var write = context.getBean(RedmineMcpProperties.class).write();
                    assertThat(write.issueDescriptionPrefix()).isEqualTo("AI_EDIT:");
                    assertThat(write.issueNotePrefix()).isEmpty();
                    assertThat(write.timeEntryCommentPrefix()).isEmpty();
                    assertThat(write.wikiCommentPrefix()).isEmpty();
                    assertThat(write.attachmentFilenamePrefix()).isEmpty();
                });
        assertThat(new RedmineMcpProperties(null, null, null, null, null, null, null, null, null, null)
                .write().issueDescriptionPrefix()).isEqualTo("AI_EDIT:");
    }

    @Test
    void writePrefixesBindFromPlaceholdersAndEmptyValueDisablesMarker() {
        runner.withPropertyValues(
                        // Same shape as application.yml: the default itself contains a colon.
                        "redmine-mcp.write.issue-description-prefix=${REDMINE_MCP_TEST_UNSET_PREFIX:AI_EDIT:}",
                        "redmine-mcp.write.issue-note-prefix= [AI] ",
                        "redmine-mcp.write.attachment-filename-prefix=AI__")
                .run(context -> {
                    var write = context.getBean(RedmineMcpProperties.class).write();
                    assertThat(write.issueDescriptionPrefix()).isEqualTo("AI_EDIT:");
                    assertThat(write.issueNotePrefix()).isEqualTo("[AI]");
                    assertThat(write.attachmentFilenamePrefix()).isEqualTo("AI__");
                });
        runner.withPropertyValues("redmine-mcp.write.issue-description-prefix=")
                .run(context -> assertThat(context.getBean(RedmineMcpProperties.class)
                        .write().issueDescriptionPrefix()).isEmpty());
    }

    @Test
    void applicationYmlDefaultsEnableWritesAndMarkOnlyIssueDescriptions() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    var write = context.getBean(RedmineMcpProperties.class).write();
                    assertThat(write.enabled()).isTrue();
                    assertThat(write.issueDescriptionPrefix()).isEqualTo("AI_EDIT:");
                    assertThat(write.issueNotePrefix()).isEmpty();
                    assertThat(write.timeEntryCommentPrefix()).isEmpty();
                    assertThat(write.wikiCommentPrefix()).isEmpty();
                    assertThat(write.attachmentFilenamePrefix()).isEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RedmineMcpProperties.class)
    static class TestConfiguration {
    }
}

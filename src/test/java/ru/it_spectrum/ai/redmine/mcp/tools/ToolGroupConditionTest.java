package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import ru.it_spectrum.ai.redmine.mcp.TestRedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.compression.AttachmentContentCompression;
import ru.it_spectrum.ai.redmine.mcp.compression.IssueCompression;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.focus.AttachmentContentFocus;
import ru.it_spectrum.ai.redmine.mcp.focus.IssueFocus;
import ru.it_spectrum.ai.redmine.mcp.service.AnalysisService;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;
import ru.it_spectrum.ai.redmine.mcp.service.ProjectService;
import ru.it_spectrum.ai.redmine.mcp.service.ReferenceDataService;
import ru.it_spectrum.ai.redmine.mcp.service.SearchService;
import ru.it_spectrum.ai.redmine.mcp.service.TimeEntryService;
import ru.it_spectrum.ai.redmine.mcp.service.UserService;
import ru.it_spectrum.ai.redmine.mcp.service.WikiService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the {@code redmine-mcp.tools.*} group toggles: each tool {@code @Service} is gated by
 * {@code @ConditionalOnProperty}. All groups are on by default (so the manifest is unchanged out of
 * the box); turning a group off removes its tools from the MCP {@code tools/list} manifest.
 */
class ToolGroupConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Mocks.class, Tools.class);

    @Test
    void allGroupsAreExposedByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(IssueTools.class);
            assertThat(ctx).hasSingleBean(IssueStructureTools.class);
            assertThat(ctx).hasSingleBean(ProjectTools.class);
            assertThat(ctx).hasSingleBean(SearchTools.class);
            assertThat(ctx).hasSingleBean(AttachmentTools.class);
            assertThat(ctx).hasSingleBean(WikiTools.class);
            assertThat(ctx).hasSingleBean(TimeEntryTools.class);
            assertThat(ctx).hasSingleBean(ReferenceDataTools.class);
            assertThat(ctx).hasSingleBean(UserTools.class);
            assertThat(ctx).hasSingleBean(IssueAnalyticsTools.class);
            assertThat(ctx).hasSingleBean(ReleaseAnalyticsTools.class);
        });
    }

    @Test
    void disablingAGroupRemovesIt() {
        runner.withPropertyValues("redmine-mcp.tools.release-analytics=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(ReleaseAnalyticsTools.class);
                    assertThat(ctx).hasSingleBean(IssueTools.class); // others stay on
                });
    }

    @Test
    void disablingMultipleGroupsLeavesOnlyTheRest() {
        runner.withPropertyValues(
                        "redmine-mcp.tools.wiki=false",
                        "redmine-mcp.tools.time-entry=false",
                        "redmine-mcp.tools.issue-structure=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(WikiTools.class);
                    assertThat(ctx).doesNotHaveBean(TimeEntryTools.class);
                    assertThat(ctx).doesNotHaveBean(IssueStructureTools.class);
                    assertThat(ctx).hasSingleBean(IssueTools.class);
                    assertThat(ctx).hasSingleBean(ProjectTools.class);
                    assertThat(ctx).hasSingleBean(SearchTools.class);
                    assertThat(ctx).hasSingleBean(AttachmentTools.class);
                    assertThat(ctx).hasSingleBean(UserTools.class);
                    assertThat(ctx).hasSingleBean(IssueAnalyticsTools.class);
                    assertThat(ctx).hasSingleBean(ReleaseAnalyticsTools.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            IssueTools.class, IssueStructureTools.class, ProjectTools.class, SearchTools.class,
            AttachmentTools.class, WikiTools.class, TimeEntryTools.class, ReferenceDataTools.class,
            UserTools.class, IssueAnalyticsTools.class, ReleaseAnalyticsTools.class
    })
    static class Tools {
    }

    @Configuration(proxyBeanMethods = false)
    static class Mocks {
        @Bean RedmineMcpProperties properties() { return TestRedmineMcpProperties.defaults(); }
        @Bean IssueService issueService() { return mock(IssueService.class); }
        @Bean ProjectService projectService() { return mock(ProjectService.class); }
        @Bean SearchService searchService() { return mock(SearchService.class); }
        @Bean AttachmentService attachmentService() { return mock(AttachmentService.class); }
        @Bean WikiService wikiService() { return mock(WikiService.class); }
        @Bean TimeEntryService timeEntryService() { return mock(TimeEntryService.class); }
        @Bean ReferenceDataService referenceDataService() { return mock(ReferenceDataService.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean AnalysisService analysisService() { return mock(AnalysisService.class); }
        @Bean IssueFocus issueFocus() { return mock(IssueFocus.class); }
        @Bean IssueCompression issueCompression() { return mock(IssueCompression.class); }
        @Bean AttachmentContentFocus attachmentContentFocus() { return mock(AttachmentContentFocus.class); }
        @Bean AttachmentContentCompression attachmentContentCompression() { return mock(AttachmentContentCompression.class); }
    }
}

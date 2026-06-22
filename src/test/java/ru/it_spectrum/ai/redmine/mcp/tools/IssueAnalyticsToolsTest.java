package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.TestRedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.service.AnalysisService;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueAnalyticsToolsTest {

    @Mock
    private RedmineClient client;

    private IssueAnalyticsTools tools;

    @BeforeEach
    void setUp() {
        tools = new IssueAnalyticsTools(new AnalysisService(client, TestRedmineMcpProperties.defaults()));
    }

    // ── getBlockerChain ────────────────────────────────────────────────

    @Test
    void shouldTraceBlockerChain() {
        // #200 blocks #100, #100 blocks #300
        var issue100 = issueWithRelations(100, "Main task", "In Progress", List.of(
                new RedmineIssue.Relation(1, 200, 100, "blocks", null),
                new RedmineIssue.Relation(2, 100, 300, "blocks", null)
        ));
        var issue200 = issueWithRelations(200, "Prerequisite", "New", List.of(
                new RedmineIssue.Relation(1, 200, 100, "blocks", null)
        ));
        var issue300 = issueWithRelations(300, "Downstream", "New", List.of(
                new RedmineIssue.Relation(2, 100, 300, "blocks", null)
        ));

        when(client.getIssue(100)).thenReturn(issue100);
        when(client.getIssue(200)).thenReturn(issue200);
        when(client.getIssue(300)).thenReturn(issue300);

        var result = ToolJsonTestSupport.stringify(tools.getBlockerChain(100));

        assertThat(result).contains("\"root\"");
        assertThat(result).contains("\"id\":100");
        assertThat(result).contains("Main task");
        assertThat(result).contains("\"blockedBy\"");
        assertThat(result).contains("\"id\":200");
        assertThat(result).contains("Prerequisite");
        assertThat(result).contains("\"blocks\"");
        assertThat(result).contains("\"id\":300");
        assertThat(result).contains("Downstream");
    }

    @Test
    void shouldHandleNoBlockingRelations() {
        var issue = issueWithRelations(100, "Standalone", "Open", List.of());
        when(client.getIssue(100)).thenReturn(issue);

        var result = ToolJsonTestSupport.stringify(tools.getBlockerChain(100));

        assertThat(result).contains("\"blockedBy\":[]");
        assertThat(result).contains("\"blocks\":[]");
    }

    @Test
    void shouldHandleIssueNotFoundInBlockerChain() {
        when(client.getIssue(999)).thenReturn(null);

        assertThatThrownBy(() -> tools.getBlockerChain(999))
                .hasMessageContaining("issue #999 not found");
    }

    // ── getStaleIssues ─────────────────────────────────────────────────

    @Test
    void shouldFindStaleIssues() {
        String oldDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(60)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String recentDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        var issues = List.of(
                summaryWithUpdatedOn(1, "Old issue", "Open", "Normal", oldDate),
                summaryWithUpdatedOn(2, "Recent issue", "Open", "Normal", recentDate)
        );
        when(client.listIssues(eq("proj"), eq("open"), isNull(), isNull(),
                isNull(), isNull(), eq("updated_on:asc"), isNull(), eq(0), eq(25)))
                .thenReturn(new RedmineIssueSummary.Page(issues, 2, 0, 25));

        var result = ToolJsonTestSupport.stringify(tools.getStaleIssues("proj", 30, null));

        assertThat(result).contains("\"projectId\":\"proj\"");
        assertThat(result).contains("\"id\":1");
        assertThat(result).contains("Old issue");
        assertThat(result).doesNotContain("Recent issue");
        assertThat(result).contains("\"issues\"");
    }

    @Test
    void shouldReturnNoStaleIssues() {
        String recentDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        var issues = List.of(
                summaryWithUpdatedOn(1, "Recent", "Open", "Normal", recentDate)
        );
        when(client.listIssues(eq("proj"), eq("open"), isNull(), isNull(),
                isNull(), isNull(), eq("updated_on:asc"), isNull(), eq(0), eq(25)))
                .thenReturn(new RedmineIssueSummary.Page(issues, 1, 0, 25));

        var result = ToolJsonTestSupport.stringify(tools.getStaleIssues("proj", 30, null));

        assertThat(result).contains("\"issues\":[]");
    }

    // ── Test helpers ───────────────────────────────────────────────────

    private static RedmineIssue issueWithRelations(int id, String subject, String status,
                                                     List<RedmineIssue.Relation> relations) {
        return new RedmineIssue(
                id,
                new IdName(1, "test-project"),
                new IdName(1, "Task"),
                new IdName(1, status),
                new IdName(2, "Normal"),
                new IdName(42, "Author"),
                new IdName(42, "Assignee"),
                null, null, null,
                subject, null,
                null, null, 0,
                null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, relations, null
        );
    }

    private static RedmineIssueSummary summaryWithUpdatedOn(int id, String subject, String status,
                                                     String priority, String updatedOn) {
        return RedmineIssueSummary.fromIssue(new RedmineIssue(
                id,
                new IdName(1, "test-project"),
                new IdName(1, "Bug"),
                new IdName(1, status),
                new IdName(2, priority),
                new IdName(42, "Author"),
                new IdName(42, "Assignee"),
                null, null, null,
                subject, null,
                null, null, 0,
                null, null, false,
                "2025-01-01T00:00:00Z", updatedOn,
                null, null, null, null, null
        ));
    }
}

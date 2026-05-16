package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;
import ru.it_spectrum.ai.redmine.mcp.service.AnalysisService;

import java.time.LocalDate;
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
class AnalysisToolsTest {

    @Mock
    private RedmineClient client;

    private AnalysisTools tools;

    @BeforeEach
    void setUp() {
        tools = new AnalysisTools(new AnalysisService(client));
    }

    // ── getProjectSummary ──────────────────────────────────────────────

    @Test
    void shouldReturnProjectSummary() {
        var issues = List.of(
                summary(1, "Fix bug", "New", "Normal", "Bug", "Alice", null, null),
                summary(2, "Add feature", "In Progress", "High", "Feature", "Bob", null, null),
                summary(3, "Write docs", "New", "Low", "Task", null, null, null)
        );
        when(client.listIssues(eq("proj"), eq("open"), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(issues, 3, 0, 100));
        when(client.listIssues(eq("proj"), eq("closed"), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(new RedmineIssueSummary.Page(List.of(), 5, 0, 1));

        var result = ToolJsonTestSupport.stringify(tools.getProjectSummary("proj", null));

        assertThat(result).contains("\"projectId\":\"proj\"");
        assertThat(result).contains("\"open\":3");
        assertThat(result).contains("\"closed\":5");
        assertThat(result).contains("\"total\":8");
        assertThat(result).contains("\"New\":2");
        assertThat(result).contains("\"In Progress\":1");
        assertThat(result).contains("\"Bug\":1");
        assertThat(result).contains("\"Feature\":1");
        assertThat(result).contains("\"Normal\":1");
        assertThat(result).contains("\"assignee\":\"Alice\"");
        assertThat(result).contains("\"assignee\":\"Unassigned\"");
    }

    @Test
    void shouldCountOverdueIssuesInSummary() {
        String pastDate = LocalDate.now().minusDays(10).toString();
        var issues = List.of(
                summary(1, "Overdue task", "Open", "High", "Bug", "Alice", pastDate, null)
        );
        when(client.listIssues(eq("proj"), eq("open"), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(issues, 1, 0, 100));
        when(client.listIssues(eq("proj"), eq("closed"), isNull(), isNull(),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(1)))
                .thenReturn(new RedmineIssueSummary.Page(List.of(), 0, 0, 1));

        var result = ToolJsonTestSupport.stringify(tools.getProjectSummary("proj", null));

        assertThat(result).contains("\"overdue\":1");
        assertThat(result).contains("\"assignee\":\"Alice\"");
    }

    // ── getUserWorkload ────────────────────────────────────────────────

    @Test
    void shouldReturnCurrentUserWorkload() {
        var user = new RedmineUser(42, "alice", "Alice", "Smith",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);

        var issues = List.of(
                summary(1, "Fix bug", "Open", "High", "Bug", "Alice", null, "proj-a"),
                summary(2, "Add feature", "Open", "Normal", "Feature", "Alice", null, "proj-b")
        );
        when(client.listIssues(isNull(), eq("open"), isNull(), eq(42),
                isNull(), isNull(), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(issues, 2, 0, 100));
        when(client.getIssuePriorities()).thenReturn(List.of(
                new IdName(1, "Low"), new IdName(2, "Normal"), new IdName(3, "High")));

        var result = ToolJsonTestSupport.stringify(tools.getUserWorkload(null, null));

        assertThat(result).contains("\"userName\":\"Alice Smith\"");
        assertThat(result).contains("\"totalOpenIssues\":2");
        assertThat(result).contains("\"byProject\"");
        assertThat(result).contains("proj-a");
        assertThat(result).contains("proj-b");
        assertThat(result).contains("\"topIssues\"");
        assertThat(result).contains("\"id\":1");
    }

    @Test
    void shouldHandleCurrentUserNotAvailableForWorkload() {
        when(client.getCurrentUser()).thenReturn(null);

        assertThatThrownBy(() -> tools.getUserWorkload(null, null))
                .hasMessageContaining("current user unavailable");
    }

    // ── getVersionChangelog ────────────────────────────────────────────

    @Test
    void shouldReturnVersionChangelog() {
        var version = new RedmineVersion(10, new IdName(1, "proj"), "v2.0", "Release",
                "open", "2025-06-01", null, null, null, null);
        when(client.getProjectVersions("proj")).thenReturn(List.of(version));

        var issues = List.of(
                summary(1, "Fix crash", "Closed", "High", "Bug", "Alice", null, null),
                summary(2, "Add OAuth", "In Progress", "Normal", "Feature", "Bob", null, null),
                summary(3, "Fix leak", "Closed", "Normal", "Bug", "Alice", null, null)
        );
        when(client.listIssues(eq("proj"), eq("*"), isNull(), isNull(),
                isNull(), eq(10), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(issues, 3, 0, 100));

        var result = ToolJsonTestSupport.stringify(tools.getVersionChangelog("proj", 10));

        assertThat(result).contains("\"projectId\":\"proj\"");
        assertThat(result).contains("\"versionId\":10");
        assertThat(result).contains("\"name\":\"v2.0\"");
        assertThat(result).contains("\"status\":\"open\"");
        assertThat(result).contains("2025-06-01");
        assertThat(result).contains("\"Bug\"");
        assertThat(result).contains("\"id\":1");
        assertThat(result).contains("Fix crash");
        assertThat(result).contains("\"id\":3");
        assertThat(result).contains("Fix leak");
        assertThat(result).contains("\"Feature\"");
        assertThat(result).contains("\"id\":2");
        assertThat(result).contains("Add OAuth");
        assertThat(result).contains("\"closed\":2");
        assertThat(result).contains("\"open\":1");
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

    // ── getReleaseRisks ────────────────────────────────────────────────

    @Test
    void shouldIdentifyReleaseRisks() {
        var version = new RedmineVersion(10, new IdName(1, "proj"), "v2.0", null,
                "open", "2025-06-01", null, null, null, null);
        when(client.getProjectVersions("proj")).thenReturn(List.of(version));

        String pastDate = LocalDate.now().minusDays(10).toString();
        // Issue with blocking relation + overdue
        var issue1 = new RedmineIssue(
                1, new IdName(1, "proj"), new IdName(1, "Bug"),
                new IdName(1, "Open"), new IdName(3, "High"),
                new IdName(42, "John"), null,
                null, null, null,
                "Blocker bug", null,
                null, pastDate, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null,
                List.of(new RedmineIssue.Relation(1, 1, 2, "blocks", null)),
                null
        );
        // Unassigned issue
        var issue2 = new RedmineIssue(
                2, new IdName(1, "proj"), new IdName(2, "Task"),
                new IdName(1, "Open"), new IdName(2, "Normal"),
                new IdName(42, "John"), null,
                null, null, null,
                "Unassigned task", null,
                null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, null
        );

        when(client.listIssues(eq("proj"), eq("open"), isNull(), isNull(),
                isNull(), eq(10), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(
                        List.of(RedmineIssueSummary.fromIssue(issue1), RedmineIssueSummary.fromIssue(issue2)),
                        2, 0, 100));
        when(client.getIssue(1)).thenReturn(issue1);
        when(client.getIssue(2)).thenReturn(issue2);
        when(client.getIssuePriorities()).thenReturn(List.of(
                new IdName(1, "Low"), new IdName(2, "Normal"), new IdName(3, "High")));

        var result = ToolJsonTestSupport.stringify(tools.getReleaseRisks("proj", 10));

        assertThat(result).contains("\"versionId\":10");
        assertThat(result).contains("\"name\":\"v2.0\"");
        assertThat(result).contains("\"kind\":\"blockers\"");
        assertThat(result).contains("\"id\":1");
        assertThat(result).contains("Blocker bug");
        assertThat(result).contains("\"kind\":\"overdue\"");
        assertThat(result).contains("\"kind\":\"unassigned\"");
        assertThat(result).contains("\"id\":2");
        assertThat(result).contains("Unassigned task");
        assertThat(result).contains("\"score\"");
    }

    @Test
    void shouldReportNoRisks() {
        when(client.getProjectVersions("proj")).thenReturn(List.of());
        when(client.listIssues(eq("proj"), eq("open"), isNull(), isNull(),
                isNull(), eq(10), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(List.of(), 0, 0, 100));
        when(client.getIssuePriorities()).thenReturn(List.of(new IdName(2, "Normal")));

        var result = ToolJsonTestSupport.stringify(tools.getReleaseRisks("proj", 10));

        assertThat(result).contains("\"categories\":[]");
        assertThat(result).contains("\"items\":0");
    }

    // ── compareVersions ────────────────────────────────────────────────

    @Test
    void shouldCompareVersions() {
        var v1 = new RedmineVersion(10, new IdName(1, "proj"), "v1.0", null,
                "closed", null, null, null, null, null);
        var v2 = new RedmineVersion(20, new IdName(1, "proj"), "v2.0", null,
                "open", null, null, null, null, null);
        when(client.getProjectVersions("proj")).thenReturn(List.of(v1, v2));

        var v1Issues = List.of(
                summary(1, "Fix bug", "Closed", "Normal", "Bug", "Alice", null, null),
                summary(2, "Shared task", "Closed", "Normal", "Task", "Bob", null, null)
        );
        var v2Issues = List.of(
                summary(2, "Shared task", "Closed", "Normal", "Task", "Bob", null, null),
                summary(3, "New feature", "Open", "High", "Feature", "Alice", null, null)
        );

        when(client.listIssues(eq("proj"), eq("*"), isNull(), isNull(),
                isNull(), eq(10), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(v1Issues, 2, 0, 100));
        when(client.listIssues(eq("proj"), eq("*"), isNull(), isNull(),
                isNull(), eq(20), isNull(), isNull(), eq(0), eq(100)))
                .thenReturn(new RedmineIssueSummary.Page(v2Issues, 2, 0, 100));

        var result = ToolJsonTestSupport.stringify(tools.compareVersions("proj", 10, 20));

        assertThat(result).contains("\"projectId\":\"proj\"");
        assertThat(result).contains("\"name\":\"v1.0\"");
        assertThat(result).contains("\"name\":\"v2.0\"");
        assertThat(result).contains("\"onlyInFirst\"");
        assertThat(result).contains("\"id\":1");
        assertThat(result).contains("Fix bug");
        assertThat(result).contains("\"onlyInSecond\"");
        assertThat(result).contains("\"id\":3");
        assertThat(result).contains("New feature");
        assertThat(result).contains("\"inBoth\"");
        assertThat(result).contains("\"id\":2");
        assertThat(result).contains("Shared task");
        assertThat(result).contains("\"completionPercent\"");
    }

    // ── Test helpers ───────────────────────────────────────────────────

    private static RedmineIssueSummary summary(int id, String subject, String status, String priority,
                                       String tracker, String assignee, String dueDate, String project) {
        return RedmineIssueSummary.fromIssue(new RedmineIssue(
                id,
                new IdName(1, project != null ? project : "test-project"),
                new IdName(1, tracker),
                new IdName(1, status),
                new IdName(2, priority),
                new IdName(42, "Author"),
                assignee != null ? new IdName(42, assignee) : null,
                null, null, null,
                subject, null,
                null, dueDate, 0,
                null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, null
        ));
    }

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

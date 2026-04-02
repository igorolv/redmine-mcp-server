package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.AttachmentTextCache;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineQuery;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedmineToolsTest {

    @Mock
    private RedmineClient client;

    private RedmineTools tools;

    @BeforeEach
    void setUp() {
        tools = new RedmineTools(client, new AttachmentTextCache());
    }

    // --- getMyIssues ---

    @Test
    void shouldReturnMyIssues() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);

        var issues = List.of(
                issue(101, "Fix login bug", "In Progress", "my-project"),
                issue(102, "Update docs", "New", "my-project")
        );
        when(client.listIssues(null, null, null, 42, null, null, null, 0, 25))
                .thenReturn(new RedmineIssue.Page(issues, 2, 0, 25));

        String result = tools.getMyIssues(null, null, null, null, null);

        assertThat(result).contains("My issues (John Doe, 2 total");
        assertThat(result).contains("#101");
        assertThat(result).contains("Fix login bug");
        assertThat(result).contains("#102");
        assertThat(result).contains("Update docs");
    }

    @Test
    void shouldReturnMyIssuesFilteredByProject() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);

        var issues = List.of(issue(201, "Deploy service", "New", "backend"));
        when(client.listIssues("backend", "open", null, 42, null, null, "updated_on:desc", 0, 10))
                .thenReturn(new RedmineIssue.Page(issues, 1, 0, 10));

        String result = tools.getMyIssues("backend", "open", "updated_on:desc", 10, 0);

        assertThat(result).contains("My issues (John Doe, 1 total");
        assertThat(result).contains("#201");
        assertThat(result).contains("Deploy service");
    }

    @Test
    void shouldReturnEmptyMyIssues() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);
        when(client.listIssues(null, null, null, 42, null, null, null, 0, 25))
                .thenReturn(new RedmineIssue.Page(List.of(), 0, 0, 25));

        String result = tools.getMyIssues(null, null, null, null, null);

        assertThat(result).contains("My issues (John Doe, 0 total");
    }

    @Test
    void shouldHandleCurrentUserNotAvailable() {
        when(client.getCurrentUser()).thenReturn(null);

        String result = tools.getMyIssues(null, null, null, null, null);

        assertThat(result).isEqualTo("Could not retrieve current user");
    }

    // --- listIssueCategories ---

    @Test
    void shouldListIssueCategories() {
        when(client.getIssueCategories("my-project")).thenReturn(List.of(
                new IdName(1, "Backend"),
                new IdName(2, "Frontend"),
                new IdName(3, "DevOps")
        ));

        String result = tools.listIssueCategories("my-project");

        assertThat(result).contains("Issue categories for project my-project");
        assertThat(result).contains("- [1] Backend");
        assertThat(result).contains("- [2] Frontend");
        assertThat(result).contains("- [3] DevOps");
    }

    @Test
    void shouldReturnMessageWhenNoCategoriesFound() {
        when(client.getIssueCategories("empty-project")).thenReturn(List.of());

        String result = tools.listIssueCategories("empty-project");

        assertThat(result).isEqualTo("No issue categories found for project: empty-project");
    }

    // --- listTimeEntryActivities ---

    @Test
    void shouldListTimeEntryActivities() {
        when(client.getTimeEntryActivities()).thenReturn(List.of(
                new IdName(8, "Design"),
                new IdName(9, "Development"),
                new IdName(10, "Testing")
        ));

        String result = tools.listTimeEntryActivities();

        assertThat(result).contains("Time entry activities:");
        assertThat(result).contains("- [8] Design");
        assertThat(result).contains("- [9] Development");
        assertThat(result).contains("- [10] Testing");
    }

    @Test
    void shouldReturnMessageWhenNoActivitiesFound() {
        when(client.getTimeEntryActivities()).thenReturn(List.of());

        String result = tools.listTimeEntryActivities();

        assertThat(result).isEqualTo("No time entry activities found");
    }

    // --- listQueries ---

    @Test
    void shouldListQueries() {
        when(client.getQueries(0, 25)).thenReturn(new RedmineQuery.Page(List.of(
                new RedmineQuery(1, "My open bugs", false, 5),
                new RedmineQuery(2, "Sprint backlog", true, null),
                new RedmineQuery(3, "Overdue tasks", false, 10)
        ), 3, 0, 25));

        String result = tools.listQueries(null, null);

        assertThat(result).contains("Saved queries: 3 total");
        assertThat(result).contains("- [1] My open bugs (project #5)");
        assertThat(result).contains("- [2] Sprint backlog [public]");
        assertThat(result).contains("- [3] Overdue tasks (project #10)");
    }

    @Test
    void shouldReturnMessageWhenNoQueriesFound() {
        when(client.getQueries(0, 25)).thenReturn(new RedmineQuery.Page(List.of(), 0, 0, 25));

        String result = tools.listQueries(null, null);

        assertThat(result).isEqualTo("No saved queries found");
    }

    // --- listIssues with queryId ---

    @Test
    void shouldListIssuesWithQueryId() {
        var issues = List.of(issue(301, "Filtered issue", "Open", "test-project"));
        when(client.listIssues(null, null, null, null, null, null, null, 7, 0, 25))
                .thenReturn(new RedmineIssue.Page(issues, 1, 0, 25));

        String result = tools.listIssues(null, null, null, null, null, null, 7, null, null, null);

        assertThat(result).contains("#301");
        assertThat(result).contains("Filtered issue");
    }

    // --- getIssue ---

    @Test
    void shouldDisplayChildrenInGetIssue() {
        var children = List.of(
                new RedmineIssue.Child(501, new IdName(1, "Bug"), "Fix null pointer"),
                new RedmineIssue.Child(502, new IdName(2, "Task"), "Write tests")
        );
        var issue = new RedmineIssue(
                100,
                new IdName(1, "my-project"),
                new IdName(1, "Bug"),
                new IdName(1, "Open"),
                new IdName(2, "Normal"),
                new IdName(42, "John Doe"),
                new IdName(42, "John Doe"),
                null, null, null,
                "Parent issue", "Some description",
                null, null, 0,
                null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, children
        );
        when(client.getIssue(100)).thenReturn(issue);

        String result = tools.getIssue(100);

        assertThat(result).contains("Issue #100: Parent issue");
        assertThat(result).contains("Subtasks (2):");
        assertThat(result).contains("#501 Fix null pointer [Bug]");
        assertThat(result).contains("#502 Write tests [Task]");
    }

    @Test
    void shouldNotDisplaySubtasksSectionWhenNoChildren() {
        var issue = issue(200, "No children issue", "Open", "my-project");
        when(client.getIssue(200)).thenReturn(issue);

        String result = tools.getIssue(200);

        assertThat(result).contains("Issue #200: No children issue");
        assertThat(result).doesNotContain("Subtasks");
    }

    @Test
    void shouldHandleIssueNotFound() {
        when(client.getIssue(999)).thenReturn(null);

        String result = tools.getIssue(999);

        assertThat(result).isEqualTo("Issue #999 not found");
    }

    // --- helpers ---

    private static RedmineIssue issue(int id, String subject, String status, String project) {
        return new RedmineIssue(
                id,
                new IdName(1, project),
                new IdName(1, "Bug"),
                new IdName(1, status),
                new IdName(2, "Normal"),
                new IdName(42, "John Doe"),
                new IdName(42, "John Doe"),
                null, null, null,
                subject, null,
                null, null, 0,
                null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, null
        );
    }
}

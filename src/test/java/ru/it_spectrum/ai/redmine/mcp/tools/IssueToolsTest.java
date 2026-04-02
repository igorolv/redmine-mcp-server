package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchWithIssues;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineSearchResult;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueToolsTest {

    @Mock
    private RedmineClient client;

    private IssueTools tools;

    @BeforeEach
    void setUp() {
        tools = new IssueTools(client);
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

    // --- searchAll ---

    @Test
    void shouldSearchAll() {
        var results = List.of(
                new RedmineSearchResult.ResultItem(101, "Login bug", "issue",
                        "http://redmine/issues/101", "Cannot login with LDAP", "2025-03-01T10:00:00Z"),
                new RedmineSearchResult.ResultItem(5, "Auth Guide", "wiki-page",
                        "http://redmine/wiki/Auth_Guide", "Authentication setup guide", "2025-02-15T08:00:00Z")
        );
        when(client.search("auth", 0, 25))
                .thenReturn(new RedmineSearchResult(results, 2, 0, 25));

        String result = tools.searchAll("auth", null, null);

        assertThat(result).contains("Search results for 'auth': 2 total");
        assertThat(result).contains("[issue] #101 Login bug");
        assertThat(result).contains("Cannot login with LDAP");
        assertThat(result).contains("[wiki-page] #5 Auth Guide");
    }

    // --- searchIssues ---

    @Test
    void shouldSearchIssues() {
        var issues = List.of(issue(101, "Login bug", "Open", "backend"));
        when(client.searchIssues("login", null, 0, 25))
                .thenReturn(new SearchWithIssues(issues, 1, 0, 25));

        String result = tools.searchIssues("login", null, null, null);

        assertThat(result).contains("Found 1 total results");
        assertThat(result).contains("#101");
        assertThat(result).contains("Login bug");
    }

    @Test
    void shouldSearchIssuesInProject() {
        var issues = List.of(issue(201, "Deploy failure", "New", "infra"));
        when(client.searchIssues("deploy", "infra", 0, 10))
                .thenReturn(new SearchWithIssues(issues, 1, 0, 10));

        String result = tools.searchIssues("deploy", "infra", 10, 0);

        assertThat(result).contains("#201");
        assertThat(result).contains("Deploy failure");
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

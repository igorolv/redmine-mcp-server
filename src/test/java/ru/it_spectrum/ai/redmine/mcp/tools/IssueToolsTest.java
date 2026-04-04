package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchWithIssues;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineSearchResult;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineUser;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

    // --- getIssueTree ---

    @Test
    void shouldBuildIssueTreeWithParentChainAndChildren() {
        // Root (grandparent) → parent → current issue → child
        var child = new RedmineIssue.Child(400, new IdName(1, "Task"), "Child task");
        var currentIssue = treeIssue(300, "Current task", "In Progress", "proj",
                new IdName(200, "Parent task"), List.of(child), null);
        var parentIssue = treeIssue(200, "Parent task", "Open", "proj",
                new IdName(100, "Root task"), List.of(new RedmineIssue.Child(300, new IdName(1, "Task"), "Current task")), null);
        var rootIssue = treeIssue(100, "Root task", "Open", "proj",
                null, List.of(new RedmineIssue.Child(200, new IdName(1, "Task"), "Parent task")), null);
        var childIssue = treeIssue(400, "Child task", "New", "proj",
                new IdName(300, "Current task"), List.of(), null);

        when(client.getIssueForTree(300)).thenReturn(currentIssue);
        when(client.getIssueForTree(200)).thenReturn(parentIssue);
        when(client.getIssueForTree(100)).thenReturn(rootIssue);
        when(client.getIssueForTree(400)).thenReturn(childIssue);

        String result = tools.getIssueTree(300, null);

        assertThat(result).contains("Issue tree for #300: Current task");
        assertThat(result).contains("Parent chain:");
        assertThat(result).contains("#100 Root task");
        assertThat(result).contains("#200 Parent task");
        assertThat(result).contains("#300 Current task");
        assertThat(result).contains("current");
        assertThat(result).contains("Subtree");
        assertThat(result).contains("#400 Child task");
    }

    @Test
    void shouldLimitTreeDepth() {
        var currentIssue = treeIssue(1, "Root", "Open", "proj",
                null, List.of(new RedmineIssue.Child(2, new IdName(1, "Task"), "L1")), null);
        var l1 = treeIssue(2, "L1", "Open", "proj",
                new IdName(1, "Root"), List.of(new RedmineIssue.Child(3, new IdName(1, "Task"), "L2")), null);
        var l2 = treeIssue(3, "L2", "Open", "proj",
                new IdName(2, "L1"), List.of(new RedmineIssue.Child(4, new IdName(1, "Task"), "L3")), null);

        when(client.getIssueForTree(1)).thenReturn(currentIssue);
        when(client.getIssueForTree(2)).thenReturn(l1);
        // depth=1: should NOT fetch issue #3

        String result = tools.getIssueTree(1, 1);

        assertThat(result).contains("#1 Root");
        assertThat(result).contains("#2 L1");
        // L2 should appear as child stub (from Child record) but not be expanded
        assertThat(result).contains("#3 L2");
        assertThat(result).doesNotContain("#4 L3");
    }

    @Test
    void shouldHandleIssueNotFoundInTree() {
        when(client.getIssueForTree(999)).thenReturn(null);

        String result = tools.getIssueTree(999, null);

        assertThat(result).isEqualTo("Issue #999 not found");
    }

    @Test
    void shouldShowRelationsInTree() {
        var relations = List.of(
                new RedmineIssue.Relation(1, 100, 200, "blocks", null),
                new RedmineIssue.Relation(2, 300, 100, "relates", null)
        );
        var issue = treeIssue(100, "Main task", "Open", "proj", null, List.of(), relations);
        when(client.getIssueForTree(100)).thenReturn(issue);

        String result = tools.getIssueTree(100, null);

        assertThat(result).contains("Relations:");
        assertThat(result).contains("#100 blocks #200");
        assertThat(result).contains("#300 relates #100");
    }

    @Test
    void shouldReportProgressForIssueTree() {
        var child = new RedmineIssue.Child(400, new IdName(1, "Task"), "Child task");
        var currentIssue = treeIssue(300, "Current task", "In Progress", "proj",
                new IdName(200, "Parent task"), List.of(child), null);
        var parentIssue = treeIssue(200, "Parent task", "Open", "proj",
                new IdName(100, "Root task"), List.of(new RedmineIssue.Child(300, new IdName(1, "Task"), "Current task")), null);
        var rootIssue = treeIssue(100, "Root task", "Open", "proj",
                null, List.of(new RedmineIssue.Child(200, new IdName(1, "Task"), "Parent task")), null);
        var childIssue = treeIssue(400, "Child task", "New", "proj",
                new IdName(300, "Current task"), List.of(), null);
        var context = progressContext("token");

        when(client.getIssueForTree(300)).thenReturn(currentIssue);
        when(client.getIssueForTree(200)).thenReturn(parentIssue);
        when(client.getIssueForTree(100)).thenReturn(rootIssue);
        when(client.getIssueForTree(400)).thenReturn(childIssue);

        tools.getIssueTree(300, null, context);

        verify(context, atLeastOnce()).progress(any(java.util.function.Consumer.class));
    }

    // --- getIssueHistory ---

    @Test
    void shouldShowHistoryWithStatusChanges() {
        var details1 = List.of(
                new RedmineIssue.Detail("attr", "status_id", "1", "2")
        );
        var details2 = List.of(
                new RedmineIssue.Detail("attr", "assigned_to_id", "42", "43")
        );
        var journals = List.of(
                new RedmineIssue.Journal(1, new IdName(42, "John"), "Starting work",
                        "2025-01-12T14:30:00Z", details1),
                new RedmineIssue.Journal(2, new IdName(43, "Jane"), null,
                        "2025-01-15T09:00:00Z", details2)
        );
        var issue = new RedmineIssue(
                100, new IdName(1, "my-project"), new IdName(1, "Bug"),
                new IdName(2, "In Progress"), new IdName(2, "Normal"),
                new IdName(42, "John"), new IdName(43, "Jane"),
                null, null, null,
                "Test issue", null,
                null, null, 0, null, null, false,
                "2025-01-10T10:00:00Z", "2025-01-15T09:00:00Z",
                null, null, journals, null, null
        );

        when(client.getIssue(100)).thenReturn(issue);
        when(client.getIssueStatuses()).thenReturn(List.of(
                new IdName(1, "New"), new IdName(2, "In Progress"), new IdName(3, "Closed")));
        when(client.getIssuePriorities()).thenReturn(List.of(
                new IdName(2, "Normal"), new IdName(3, "High")));
        when(client.getTrackers()).thenReturn(List.of(new IdName(1, "Bug")));
        when(client.getProjectVersions("1")).thenReturn(List.of());

        String result = tools.getIssueHistory(100);

        assertThat(result).contains("History of #100: Test issue");
        assertThat(result).contains("[Created] by John");
        assertThat(result).contains("Status: New \u2192 In Progress");
        assertThat(result).contains("Starting work");
        assertThat(result).contains("Assigned to: John \u2192 Jane");
        assertThat(result).contains("Status durations:");
        assertThat(result).contains("New:");
        assertThat(result).contains("In Progress:");
    }

    @Test
    void shouldHandleIssueNotFoundInHistory() {
        when(client.getIssue(999)).thenReturn(null);

        String result = tools.getIssueHistory(999);

        assertThat(result).isEqualTo("Issue #999 not found");
    }

    @Test
    void shouldHandleEmptyJournals() {
        var issue = new RedmineIssue(
                100, new IdName(1, "my-project"), new IdName(1, "Bug"),
                new IdName(1, "New"), new IdName(2, "Normal"),
                new IdName(42, "John"), null,
                null, null, null,
                "Simple issue", null,
                null, null, 0, null, null, false,
                "2025-01-10T10:00:00Z", "2025-01-10T10:00:00Z",
                null, null, List.of(), null, null
        );

        when(client.getIssue(100)).thenReturn(issue);
        when(client.getIssueStatuses()).thenReturn(List.of(new IdName(1, "New")));
        when(client.getIssuePriorities()).thenReturn(List.of(new IdName(2, "Normal")));
        when(client.getTrackers()).thenReturn(List.of(new IdName(1, "Bug")));
        when(client.getProjectVersions("1")).thenReturn(List.of());

        String result = tools.getIssueHistory(100);

        assertThat(result).contains("History of #100: Simple issue");
        assertThat(result).contains("[Created] by John");
        assertThat(result).contains("Status durations:");
        assertThat(result).contains("New:");
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

    private static RedmineIssue treeIssue(int id, String subject, String status, String project,
                                           IdName parent, List<RedmineIssue.Child> children,
                                           List<RedmineIssue.Relation> relations) {
        return new RedmineIssue(
                id,
                new IdName(1, project),
                new IdName(1, "Task"),
                new IdName(1, status),
                new IdName(2, "Normal"),
                new IdName(42, "John Doe"),
                new IdName(42, "John Doe"),
                parent, null, null,
                subject, null,
                null, null, 0,
                null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, relations, children
        );
    }

    private static McpSyncRequestContext progressContext(Object token) {
        var context = mock(McpSyncRequestContext.class);
        var request = io.modelcontextprotocol.spec.McpSchema.CallToolRequest.builder()
                .name("issueTool")
                .arguments(Map.of())
                .progressToken(token)
                .build();
        when(context.request()).thenReturn(request);
        return context;
    }
}

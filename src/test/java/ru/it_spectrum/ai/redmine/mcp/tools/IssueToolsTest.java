package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchWithIssueSummaries;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineSearchResult;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueToolsTest {

    @Mock
    private RedmineClient client;

    private IssueTools tools;

    @BeforeEach
    void setUp() {
        tools = new IssueTools(new IssueService(client), ToolJsonTestSupport.json(), ToolJsonTestSupport.errors());
    }

    // --- getMyIssues ---

    @Test
    void shouldReturnMyIssues() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);

        var issues = List.of(
                summary(101, "Fix login bug", "In Progress", "my-project"),
                summary(102, "Update docs", "New", "my-project")
        );
        when(client.listIssues(null, null, null, 42, null, null, null, 0, 25))
                .thenReturn(new RedmineIssueSummary.Page(issues, 2, 0, 25));

        String result = tools.getMyIssues(null, null, null, null, null);

        assertThat(result).contains("\"user\"");
        assertThat(result).contains("\"firstname\":\"John\"");
        assertThat(result).contains("\"lastname\":\"Doe\"");
        assertThat(result).contains("\"total_count\":2");
        assertThat(result).contains("\"id\":101");
        assertThat(result).contains("Fix login bug");
        assertThat(result).contains("\"id\":102");
        assertThat(result).contains("Update docs");
    }

    @Test
    void shouldReturnMyIssuesFilteredByProject() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);

        var issues = List.of(summary(201, "Deploy service", "New", "backend"));
        when(client.listIssues("backend", "open", null, 42, null, null, "updated_on:desc", 0, 10))
                .thenReturn(new RedmineIssueSummary.Page(issues, 1, 0, 10));

        String result = tools.getMyIssues("backend", "open", "updated_on:desc", 10, 0);

        assertThat(result).contains("\"firstname\":\"John\"");
        assertThat(result).contains("\"total_count\":1");
        assertThat(result).contains("\"id\":201");
        assertThat(result).contains("Deploy service");
    }

    @Test
    void shouldReturnEmptyMyIssues() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);
        when(client.listIssues(null, null, null, 42, null, null, null, 0, 25))
                .thenReturn(new RedmineIssueSummary.Page(List.of(), 0, 0, 25));

        String result = tools.getMyIssues(null, null, null, null, null);

        assertThat(result).contains("\"total_count\":0");
    }

    @Test
    void shouldHandleCurrentUserNotAvailable() {
        when(client.getCurrentUser()).thenReturn(null);

        String result = tools.getMyIssues(null, null, null, null, null);

        assertThat(result).contains("\"kind\":\"unavailable\"");
        assertThat(result).contains("current user unavailable");
    }

    // --- listIssues with queryId ---

    @Test
    void shouldListIssuesWithQueryId() {
        var issues = List.of(summary(301, "Filtered issue", "Open", "test-project"));
        when(client.listIssues(null, null, null, null, null, null, null, 7, Map.of(), 0, 25))
                .thenReturn(new RedmineIssueSummary.Page(issues, 1, 0, 25));

        String result = tools.listIssues(null, null, null, null, null, null, 7, null, null, null);

        assertThat(result).contains("\"id\":301");
        assertThat(result).contains("Filtered issue");
    }

    @Test
    void shouldListIssuesWithCustomFieldFilters() {
        var issues = List.of(summary(302, "RTK issue", "Open", "test-project"));
        when(client.listIssues(null, null, null, null, null, null, null, null,
                Map.of("cf_10", "rtk", "cf_3", "502167"), 0, 25))
                .thenReturn(new RedmineIssueSummary.Page(issues, 1, 0, 25));

        String result = tools.listIssues(null, null, null, null, null, null,
                null, "cf_10=rtk&cf_3=502167", null, null, null);

        assertThat(result).contains("\"id\":302");
        assertThat(result).contains("RTK issue");
    }

    @Test
    void shouldRejectInvalidCustomFieldFilters() {
        String result = tools.listIssues(null, null, null, null, null, null,
                null, "applications=rtk", null, null, null);

        assertThat(result).contains("\"kind\":\"argument\"");
        assertThat(result).contains("Invalid custom field key");
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

        assertThat(result).contains("\"id\":100");
        assertThat(result).contains("Parent issue");
        assertThat(result).contains("\"children\"");
        assertThat(result).contains("\"id\":501");
        assertThat(result).contains("Fix null pointer");
        assertThat(result).contains("\"id\":502");
        assertThat(result).contains("Write tests");
    }

    @Test
    void shouldDisplayChangesetsInGetIssue() {
        var changesets = List.of(
                new RedmineIssue.Changeset(
                        "be561082833c6d4fbeba95228a253298a1cfa874",
                        new IdName(56, "Igor Olvovsky"),
                        "#4183. Текущие версии snapshot",
                        "2026-03-06T13:06:16Z")
        );
        var issue = new RedmineIssue(
                4183,
                new IdName(1, "my-project"),
                new IdName(1, "Bug"),
                new IdName(1, "Open"),
                new IdName(2, "Normal"),
                new IdName(42, "John Doe"),
                new IdName(42, "John Doe"),
                null, null, null,
                "Incident with changesets", "Some description",
                null, null, 0,
                null, null, false,
                "2026-03-01T00:00:00Z", "2026-03-02T00:00:00Z",
                null, null, null, null, null, changesets
        );
        when(client.getIssue(4183)).thenReturn(issue);

        String result = tools.getIssue(4183);

        assertThat(result).contains("\"changesets\"");
        assertThat(result).contains("be561082833c6d4fbeba95228a253298a1cfa874");
        assertThat(result).contains("Igor Olvovsky");
        assertThat(result).contains("#4183. Текущие версии snapshot");
        assertThat(result).contains("\"committed_on\":\"2026-03-06T13:06:16Z\"");
    }

    @Test
    void shouldFormatCustomFieldsInGetIssue() {
        var issue = new RedmineIssue(
                101,
                new IdName(1, "my-project"),
                new IdName(1, "Bug"),
                new IdName(1, "Open"),
                new IdName(2, "Normal"),
                new IdName(42, "John Doe"),
                new IdName(42, "John Doe"),
                null, null, null,
                "Issue with custom fields", "Some description",
                null, null, 0,
                null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                List.of(
                        new RedmineIssue.CustomField(3, "# в системе заказчика", false, "502167"),
                        new RedmineIssue.CustomField(10, "applications", true, List.of("rtk", "sskv")),
                        new RedmineIssue.CustomField(16, "Решена в версии", false, "")
                ),
                null, null, null, null
        );
        when(client.getIssue(101)).thenReturn(issue);

        String result = tools.getIssue(101);

        assertThat(result).contains("# в системе заказчика");
        assertThat(result).contains("502167");
        assertThat(result).contains("applications");
        assertThat(result).contains("rtk");
        assertThat(result).contains("sskv");
        assertThat(result).contains("Решена в версии");
    }

    @Test
    void shouldNotDisplaySubtasksSectionWhenNoChildren() {
        var issue = issue(200, "No children issue", "Open", "my-project");
        when(client.getIssue(200)).thenReturn(issue);

        String result = tools.getIssue(200);

        assertThat(result).contains("\"id\":200");
        assertThat(result).contains("No children issue");
    }

    @Test
    void shouldHandleIssueNotFound() {
        when(client.getIssue(999)).thenReturn(null);

        String result = tools.getIssue(999);

        assertThat(result).contains("\"kind\":\"not_found\"");
        assertThat(result).contains("issue #999 not found");
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

        assertThat(result).contains("\"total_count\":2");
        assertThat(result).contains("\"type\":\"issue\"");
        assertThat(result).contains("\"id\":101");
        assertThat(result).contains("Login bug");
        assertThat(result).contains("Cannot login with LDAP");
        assertThat(result).contains("\"type\":\"wiki-page\"");
        assertThat(result).contains("\"id\":5");
        assertThat(result).contains("Auth Guide");
    }

    // --- searchIssues ---

    @Test
    void shouldSearchIssues() {
        var issues = List.of(summary(101, "Login bug", "Open", "backend"));
        when(client.searchIssues("login", null, 0, 25))
                .thenReturn(new SearchWithIssueSummaries(issues, 1, 0, 25));

        String result = tools.searchIssues("login", null, null, null);

        assertThat(result).contains("\"totalCount\":1");
        assertThat(result).contains("\"id\":101");
        assertThat(result).contains("Login bug");
    }

    @Test
    void shouldSearchIssuesInProject() {
        var issues = List.of(summary(201, "Deploy failure", "New", "infra"));
        when(client.searchIssues("deploy", "infra", 0, 10))
                .thenReturn(new SearchWithIssueSummaries(issues, 1, 0, 10));

        String result = tools.searchIssues("deploy", "infra", 10, 0);

        assertThat(result).contains("\"id\":201");
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

        when(client.getIssue(300)).thenReturn(currentIssue);
        when(client.getIssue(200)).thenReturn(parentIssue);
        when(client.getIssue(100)).thenReturn(rootIssue);
        when(client.getIssue(400)).thenReturn(childIssue);

        String result = tools.getIssueTree(300, null);

        assertThat(result).contains("\"root\"");
        assertThat(result).contains("\"id\":300");
        assertThat(result).contains("Current task");
        assertThat(result).contains("\"ancestors\"");
        assertThat(result).contains("\"id\":100");
        assertThat(result).contains("Root task");
        assertThat(result).contains("\"id\":200");
        assertThat(result).contains("Parent task");
        assertThat(result).contains("\"subtree\"");
        assertThat(result).contains("\"id\":400");
        assertThat(result).contains("Child task");
    }

    @Test
    void shouldLimitTreeDepth() {
        var currentIssue = treeIssue(1, "Root", "Open", "proj",
                null, List.of(new RedmineIssue.Child(2, new IdName(1, "Task"), "L1")), null);
        var l1 = treeIssue(2, "L1", "Open", "proj",
                new IdName(1, "Root"), List.of(new RedmineIssue.Child(3, new IdName(1, "Task"), "L2")), null);
        var l2 = treeIssue(3, "L2", "Open", "proj",
                new IdName(2, "L1"), List.of(new RedmineIssue.Child(4, new IdName(1, "Task"), "L3")), null);

        when(client.getIssue(1)).thenReturn(currentIssue);
        when(client.getIssue(2)).thenReturn(l1);
        // depth=1: should NOT fetch issue #3

        String result = tools.getIssueTree(1, 1);

        assertThat(result).contains("\"id\":1");
        assertThat(result).contains("Root");
        assertThat(result).contains("\"id\":2");
        assertThat(result).contains("L1");
        // L2 should appear as child stub (from Child record) but not be expanded
        assertThat(result).contains("\"id\":3");
        assertThat(result).contains("L2");
        assertThat(result).doesNotContain("L3");
    }

    @Test
    void shouldHandleIssueNotFoundInTree() {
        when(client.getIssue(999)).thenReturn(null);

        String result = tools.getIssueTree(999, null);

        assertThat(result).contains("\"kind\":\"not_found\"");
        assertThat(result).contains("issue #999 not found");
    }

    @Test
    void shouldShowRelationsInTree() {
        var relations = List.of(
                new RedmineIssue.Relation(1, 100, 200, "blocks", null),
                new RedmineIssue.Relation(2, 300, 100, "relates", null)
        );
        var issue = treeIssue(100, "Main task", "Open", "proj", null, List.of(), relations);
        when(client.getIssue(100)).thenReturn(issue);

        String result = tools.getIssueTree(100, null);

        assertThat(result).contains("\"relations\"");
        assertThat(result).contains("\"issue_id\":100");
        assertThat(result).contains("\"issue_to_id\":200");
        assertThat(result).contains("\"relation_type\":\"blocks\"");
        assertThat(result).contains("\"issue_id\":300");
        assertThat(result).contains("\"relation_type\":\"relates\"");
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

    private static RedmineIssueSummary summary(int id, String subject, String status, String project) {
        return RedmineIssueSummary.fromIssue(issue(id, subject, status, project));
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

}

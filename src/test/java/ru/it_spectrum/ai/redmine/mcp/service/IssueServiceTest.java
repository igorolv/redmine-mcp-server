package ru.it_spectrum.ai.redmine.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.api.IssueHistory;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchWithIssueSummaries;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private RedmineClient client;

    private IssueService service;

    @BeforeEach
    void setUp() {
        service = new IssueService(client);
    }

    // --- find / findOrThrow ---

    @Test
    void findShouldReturnEmptyWhenIssueMissing() {
        when(client.getIssue(99)).thenReturn(null);
        assertThat(service.find(99)).isEmpty();
    }

    // --- list / searchIssues ---

    @Test
    void listShouldPassFiltersToClient() {
        var page = new RedmineIssueSummary.Page(List.of(summary(1, "Bug")), 1, 0, 25);
        when(client.listIssues("p", "open", 1, 2, 3, 4, "updated_on:desc", 5,
                Map.of("cf_1", "x"), 0, 25)).thenReturn(page);

        var result = service.list("p", "open", 1, 2, 3, 4, 5,
                Map.of("cf_1", "x"), "updated_on:desc", 0, 25);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.issues()).singleElement().satisfies(i -> assertThat(i.id()).isEqualTo(1));
    }

    @Test
    void listShouldDefaultNullCustomFieldFiltersToEmptyMap() {
        var page = new RedmineIssueSummary.Page(List.of(), 0, 0, 25);
        when(client.listIssues(null, null, null, null, null, null, null, null,
                Map.of(), 0, 25)).thenReturn(page);

        var result = service.list(null, null, null, null, null, null, null, null, null, 0, 25);

        assertThat(result.issues()).isEmpty();
    }

    @Test
    void searchIssuesShouldDelegateToClient() {
        var sw = new SearchWithIssueSummaries(List.of(summary(7, "Hit")), 1, 0, 25);
        when(client.searchIssues("q", "p", 0, 25)).thenReturn(sw);

        var result = service.searchIssues("q", "p", 0, 25);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.issues()).singleElement().satisfies(i -> assertThat(i.id()).isEqualTo(7));
    }

    // --- getMyIssues ---

    @Test
    void getMyIssuesShouldReturnEmptyWhenUserUnavailable() {
        when(client.getCurrentUser()).thenReturn(null);
        assertThat(service.getMyIssues(null, null, null, 0, 25)).isEmpty();
    }

    @Test
    void getMyIssuesShouldComposeUserAndIssuesPage() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        var page = new RedmineIssueSummary.Page(List.of(summary(1, "Bug")), 1, 0, 25);
        when(client.getCurrentUser()).thenReturn(user);
        when(client.listIssues("p", "open", null, 42, null, null, "updated_on:desc", 0, 25))
                .thenReturn(page);

        var result = service.getMyIssues("p", "open", "updated_on:desc", 0, 25);

        assertThat(result).isPresent();
        assertThat(result.get().user().id()).isEqualTo(42);
        assertThat(result.get().user().name()).isEqualTo("John Doe");
        assertThat(result.get().page().totalCount()).isEqualTo(1);
        assertThat(result.get().page().issues()).singleElement()
                .satisfies(i -> assertThat(i.id()).isEqualTo(1));
    }

    // --- getTree ---

    @Test
    void getTreeShouldThrowWhenRootMissing() {
        when(client.getIssue(99)).thenReturn(null);
        assertThatThrownBy(() -> service.getTree(99, null))
                .isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void getTreeShouldBuildAncestorChainAndSubtree() {
        var grandParent = treeIssue(1, "Root", null, List.of(child(2, "Mid")));
        var parent = treeIssue(2, "Mid", new IdName(1, "Root"), List.of(child(3, "Leaf")));
        var leaf = treeIssue(3, "Leaf", new IdName(2, "Mid"), List.of());
        when(client.getIssue(3)).thenReturn(leaf);
        when(client.getIssue(2)).thenReturn(parent);
        when(client.getIssue(1)).thenReturn(grandParent);

        var view = service.getTree(3, 2);

        assertThat(view.root().id()).isEqualTo(3);
        assertThat(view.ancestors()).extracting(Issue::id).containsExactly(2, 1);
        assertThat(view.subtree().id()).isEqualTo(3);
        assertThat(view.fetchedCount()).isEqualTo(3);
        assertThat(view.limitReached()).isFalse();
    }

    @Test
    void getTreeShouldRespectDepthLimitWithStubChildren() {
        var root = treeIssue(1, "Root", null, List.of(child(2, "L1")));
        var l1 = treeIssue(2, "L1", new IdName(1, "Root"), List.of(child(3, "L2")));
        when(client.getIssue(1)).thenReturn(root);
        when(client.getIssue(2)).thenReturn(l1);

        var view = service.getTree(1, 1);

        assertThat(view.subtree().children()).hasSize(1);
        var l1Node = view.subtree().children().get(0);
        assertThat(l1Node.id()).isEqualTo(2);
        assertThat(l1Node.children()).hasSize(1);
        var l2Stub = l1Node.children().get(0);
        assertThat(l2Stub.id()).isEqualTo(3);
        assertThat(l2Stub.stub()).isTrue();
        verify(client, times(0)).getIssue(3);
    }

    @Test
    void getTreeShouldMemoizeFetchesWhenSharingContext() {
        var root = treeIssue(1, "Root", null, List.of());
        when(client.getIssue(1)).thenReturn(root);

        var ctx = new IssueFetchContext(client);
        service.getTree(1, 0, ctx);
        service.getTree(1, 0, ctx);

        // Second call uses the cached issue from ctx.
        verify(client, times(1)).getIssue(1);
    }

    // --- buildHistory ---

    @Test
    void buildHistoryShouldBuildTimelineAndResolveStatusNames() {
        var details = List.of(new RedmineIssue.Detail("attr", "status_id", "1", "2"));
        var journals = List.of(new RedmineIssue.Journal(
                1, new IdName(42, "John"), "Starting", "2025-01-12T14:30:00Z", details));
        var issue = issueWithJournals(100, "Test", new IdName(2, "In Progress"),
                new IdName(42, "John"), journals);
        when(client.getIssueStatuses()).thenReturn(List.of(
                new IdName(1, "New"), new IdName(2, "In Progress")));

        var view = service.buildHistory(issue);

        assertThat(view.timeline()).hasSize(2);
        var created = view.timeline().get(0);
        assertThat(created.kind()).isEqualTo(IssueHistory.Kind.CREATED);
        assertThat(created.actor()).isEqualTo("John");

        var updated = view.timeline().get(1);
        assertThat(updated.kind()).isEqualTo(IssueHistory.Kind.UPDATED);
        assertThat(updated.note()).isEqualTo("Starting");
        assertThat(updated.changes()).singleElement().satisfies(c -> {
            assertThat(c.fieldLabel()).isEqualTo("Status");
            assertThat(c.oldValue()).isEqualTo("New");
            assertThat(c.newValue()).isEqualTo("In Progress");
        });

        assertThat(view.statusDurations()).extracting(IssueHistory.StatusDuration::statusName)
                .containsExactly("New", "In Progress");
        assertThat(view.statusDurations().get(1).toTimestamp()).isNull();
    }

    @Test
    void buildHistoryShouldNotLoadUnusedReferenceData() {
        var issue = issueWithJournals(100, "Test", new IdName(1, "New"),
                new IdName(42, "John"), List.of());

        service.buildHistory(issue);

        verify(client, times(0)).getIssueStatuses();
        verify(client, times(0)).getIssuePriorities();
        verify(client, times(0)).getTrackers();
    }

    // --- helpers ---

    private static RedmineIssue issue(int id, String subject) {
        return new RedmineIssue(
                id, new IdName(1, "p"), new IdName(1, "Bug"),
                new IdName(1, "New"), new IdName(2, "Normal"),
                new IdName(42, "John"), null, null, null, null,
                subject, null, null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, null);
    }

    private static RedmineIssueSummary summary(int id, String subject) {
        return RedmineIssueSummary.fromIssue(issue(id, subject));
    }

    private static RedmineIssue treeIssue(int id, String subject, IdName parent,
                                          List<RedmineIssue.Child> children) {
        return new RedmineIssue(
                id, new IdName(1, "p"), new IdName(1, "Task"),
                new IdName(1, "Open"), new IdName(2, "Normal"),
                new IdName(42, "John"), new IdName(42, "John"),
                parent, null, null,
                subject, null, null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, children);
    }

    private static RedmineIssue.Child child(int id, String subject) {
        return new RedmineIssue.Child(id, new IdName(1, "Task"), subject);
    }

    private static RedmineIssue issueWithJournals(int id, String subject, IdName status,
                                                  IdName author, List<RedmineIssue.Journal> journals) {
        return new RedmineIssue(
                id, new IdName(1, "p"), new IdName(1, "Bug"),
                status, new IdName(2, "Normal"),
                author, null, null, null, null,
                subject, null, null, null, 0, null, null, false,
                "2025-01-10T10:00:00Z", "2025-01-15T09:00:00Z",
                null, null, journals, null, null);
    }
}

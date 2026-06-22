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
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;
import ru.it_spectrum.ai.redmine.mcp.service.RelatedRefBuilder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueStructureToolsTest {

    @Mock
    private RedmineClient client;

    private IssueStructureTools tools;

    @BeforeEach
    void setUp() {
        var properties = TestRedmineMcpProperties.defaults();
        var attachmentService = mock(AttachmentService.class);
        var relatedRefBuilder = new RelatedRefBuilder(client, attachmentService, properties);
        var issueService = new IssueService(client, attachmentService, relatedRefBuilder, properties);
        tools = new IssueStructureTools(issueService);
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

        var result = ToolJsonTestSupport.stringify(tools.getIssueTree(300, null));

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

        var result = ToolJsonTestSupport.stringify(tools.getIssueTree(1, 1));

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

        assertThatThrownBy(() -> tools.getIssueTree(999, null))
                .hasMessageContaining("Issue #999 not found");
    }

    // --- getIssueHistory ---

    @Test
    void shouldReturnInterpretedHistory() {
        var journals = List.of(
                new RedmineIssue.Journal(
                        1001,
                        new IdName(56, "Igor Olvovsky"),
                        "Important review note",
                        "2026-05-15T14:05:42Z",
                        List.of(new RedmineIssue.Detail("attr", "status_id", "1", "2")))
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
                "Issue with history", "desc",
                null, null, 0,
                null, null, false,
                "2026-05-15T00:00:00Z", "2026-05-15T14:05:42Z",
                null, null, journals, null, null
        );
        when(client.getIssue(4183)).thenReturn(issue);

        var result = ToolJsonTestSupport.stringify(tools.getIssueHistory(4183));

        assertThat(result).contains("\"timeline\"");
        assertThat(result).contains("Important review note");
    }

    @Test
    void shouldHandleIssueNotFoundInHistory() {
        when(client.getIssue(999)).thenReturn(null);

        assertThatThrownBy(() -> tools.getIssueHistory(999))
                .hasMessageContaining("Issue #999 not found");
    }

    // --- helpers ---

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

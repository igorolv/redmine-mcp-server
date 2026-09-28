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
import ru.it_spectrum.ai.redmine.mcp.compression.HistoryCompression;
import ru.it_spectrum.ai.redmine.mcp.compression.ResponseCompressor;
import ru.it_spectrum.ai.redmine.mcp.config.JsonConfig;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;
import ru.it_spectrum.ai.redmine.mcp.service.RelatedRefBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        var relatedRefBuilder = new RelatedRefBuilder(client, properties);
        var mapper = new JsonConfig().redmineMcpObjectMapper();
        var issueService = new IssueService(client, attachmentService, relatedRefBuilder,
                new HistoryCompression(new ResponseCompressor(mapper), mapper, properties), properties);
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

    @Test
    void treeKeepsIssueShapeWithoutReturningHeavyFields() throws Exception {
        String largeText = "specification".repeat(20_000);
        var root = issueWithHistory(300, new IdName(200, "Parent"), largeText,
                List.of(new RedmineIssue.Journal(1, new IdName(42, "John"), largeText,
                        "2026-05-15T14:05:42Z", List.of())));
        var parent = issueWithHistory(200, null, largeText, List.of());
        when(client.getIssue(300)).thenReturn(root);
        when(client.getIssue(200)).thenReturn(parent);

        String result = ToolJsonTestSupport.stringify(tools.getIssueTree(300, 0));
        var json = ToolJsonTestSupport.parse(result);

        assertThat(json.get("root").get("id").asInt()).isEqualTo(300);
        assertThat(json.get("root").has("description")).isFalse();
        assertThat(json.get("root").has("journals")).isFalse();
        assertThat(json.get("ancestors").get(0).has("description")).isFalse();
        assertThat(json.get("root").get("focusNotes").get(0).asText()).contains("getIssue");
        assertThat(result.length()).isLessThan(50_000);
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
        assertThat(result).contains("\"journalId\":1001");
        assertThat(result).contains("\"totalEvents\":2");
    }

    @Test
    void largeHistoryFitsBudgetAndKeepsEveryJournal() throws Exception {
        String description = "description revision ".repeat(900);
        String note = "review note ".repeat(130);
        var journals = new ArrayList<RedmineIssue.Journal>();
        for (int i = 1; i <= 90; i++) {
            journals.add(new RedmineIssue.Journal(2_000 + i, new IdName(42, "John"), note,
                    "2026-05-15T14:05:42Z", List.of(
                    new RedmineIssue.Detail("attr", "description", description, description + i))));
        }
        // Relation-only journals used to disappear from the interpreted history.
        journals.add(new RedmineIssue.Journal(3_000, new IdName(42, "John"), null,
                "2026-05-15T14:05:43Z", List.of(
                new RedmineIssue.Detail("relation", "relates", null, "123"))));
        when(client.getIssue(4183)).thenReturn(issueWithHistory(4183, null, "Short", journals));

        String result = ToolJsonTestSupport.stringify(tools.getIssueHistory(4183));
        var json = ToolJsonTestSupport.parse(result);

        assertThat(result.length()).isLessThanOrEqualTo(50_000);
        assertThat(result.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(50_000);
        assertThat(json.get("timeline")).hasSize(92);
        assertThat(json.get("totalEvents").asInt()).isEqualTo(92);
        assertThat(json.has("nextOffset")).isFalse();
        assertThat(result).contains("\"journalId\":3000", "getIssueJournal(issueId=4183, journalId=2001)",
                "compressionNotes");
        assertThat(result).doesNotContain(description);
    }

    @Test
    void historyNextOffsetVisitsAllEventsWithoutGaps() throws Exception {
        var journals = new ArrayList<RedmineIssue.Journal>();
        for (int i = 1; i <= 900; i++) {
            List<RedmineIssue.Detail> details = i % 100 == 0
                    ? List.of(new RedmineIssue.Detail("attr", "status_id", "1", "2"))
                    : List.of();
            journals.add(new RedmineIssue.Journal(4_000 + i, new IdName(42, "John"), "Короткая заметка",
                    "2026-05-15T14:05:42Z", details));
        }
        when(client.getIssue(4183)).thenReturn(issueWithHistory(4183, null, "Short", journals));
        when(client.getIssueStatuses()).thenReturn(List.of(
                new IdName(1, "Open"), new IdName(2, "In Progress")));

        var seen = new ArrayList<Integer>();
        int statusIntervals = 0;
        Integer offset = null;
        int pages = 0;
        do {
            String result = ToolJsonTestSupport.stringify(tools.getIssueHistory(4183, offset));
            var json = ToolJsonTestSupport.parse(result);
            assertThat(result.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(50_000);
            for (var entry : json.get("timeline")) {
                if (entry.has("journalId")) {
                    seen.add(entry.get("journalId").asInt());
                }
            }
            statusIntervals += json.get("statusDurations").size();
            offset = json.has("nextOffset") ? json.get("nextOffset").asInt() : null;
            pages++;
            assertThat(pages).isLessThan(20);
        } while (offset != null);

        assertThat(pages).isGreaterThan(1);
        assertThat(statusIntervals).isEqualTo(10);
        assertThat(seen).containsExactlyElementsOf(journals.stream().map(RedmineIssue.Journal::id).toList());
    }

    @Test
    void oneOversizedEventStillHasARecoverablePage() throws Exception {
        String hugeStatus = "Большой статус".repeat(8_000);
        var journal = new RedmineIssue.Journal(7_001, new IdName(42, "John"), "note",
                "2026-05-15T14:05:42Z", List.of(
                new RedmineIssue.Detail("attr", "status_id", "1", "2")));
        when(client.getIssue(4183)).thenReturn(issueWithHistory(4183, null, "Short", List.of(journal)));
        when(client.getIssueStatuses()).thenReturn(List.of(
                new IdName(1, "Open"), new IdName(2, hugeStatus)));

        var first = ToolJsonTestSupport.parse(ToolJsonTestSupport.stringify(tools.getIssueHistory(4183)));
        var secondText = ToolJsonTestSupport.stringify(
                tools.getIssueHistory(4183, first.get("nextOffset").asInt()));
        var second = ToolJsonTestSupport.parse(secondText);

        assertThat(secondText.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(50_000);
        assertThat(second.get("timeline")).hasSize(1);
        assertThat(second.get("timeline").get(0).get("journalId").asInt()).isEqualTo(7_001);
        assertThat(secondText).contains("oversized event", "getIssueJournal(issueId=4183, journalId=7001)");
        assertThat(second.has("nextOffset")).isFalse();
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

    private static RedmineIssue issueWithHistory(int id, IdName parent, String description,
                                                 List<RedmineIssue.Journal> journals) {
        return new RedmineIssue(id, new IdName(1, "proj"), new IdName(1, "Task"),
                new IdName(1, "Open"), new IdName(2, "Normal"), new IdName(42, "John"),
                null, parent, null, null, "Issue " + id, description, null, null,
                0, null, null, false, "2026-05-15T00:00:00Z", "2026-05-15T14:05:42Z",
                null, null, journals, null, List.of());
    }
}

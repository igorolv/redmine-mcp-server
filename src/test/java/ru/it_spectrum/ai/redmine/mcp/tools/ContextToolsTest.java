package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.AttachmentTextCache;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextToolsTest {

    @Mock
    private RedmineClient client;

    private ContextTools tools;

    @BeforeEach
    void setUp() {
        var extractor = new DocumentTextExtractor(client, new AttachmentTextCache());
        tools = new ContextTools(client, extractor);

        // Default stub for listIssues — many tools call it for "similar" searches
        lenient().when(client.listIssues(
                any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()
        )).thenReturn(new RedmineIssue.Page(List.of(), 0, 0, 25));
    }

    // ── getIssueFullContext ──────────────────────────────────────────

    @Nested
    class GetIssueFullContext {

        @Test
        void shouldReturnFullContext() {
            var parent = issueBuilder(100, "Epic: Reports Module")
                    .status(new IdName(2, "In Progress"))
                    .assignedTo(new IdName(1, "Alice"))
                    .description("Build the reports module")
                    .children(List.of(child(123, "Date Filter"), child(124, "Charts")))
                    .build();

            var issue = issueBuilder(123, "Implement date filter")
                    .status(new IdName(1, "New"))
                    .assignedTo(new IdName(2, "Bob"))
                    .parent(new IdName(100, "Epic: Reports Module"))
                    .description("Filter issues by date range\nAcceptance: date picker works")
                    .relations(List.of(new RedmineIssue.Relation(1, 123, 95, "relates", null)))
                    .journals(List.of(journal(1, "Alice", "Check the date format spec")))
                    .build();

            var related = issueForTree(95, "Date API endpoint",
                    new IdName(3, "Closed"), new IdName(3, "Charlie"),
                    "REST endpoint for date-based filtering");

            var siblingCharts = issueForTree(124, "Add charts",
                    new IdName(1, "New"), null, null);

            when(client.getIssue(123)).thenReturn(issue);
            when(client.getIssue(100)).thenReturn(parent);
            when(client.getIssueForTree(124)).thenReturn(siblingCharts);
            when(client.getIssueForTree(95)).thenReturn(related);

            String result = tools.getIssueFullContext(123);

            assertThat(result).contains("=== Issue #123: Implement date filter ===");
            assertThat(result).contains("Assigned: Bob");
            assertThat(result).contains("Filter issues by date range");
            assertThat(result).contains("Parent: #100 Epic: Reports Module");
            assertThat(result).contains("Siblings");
            assertThat(result).contains("#124 Add charts");
            assertThat(result).contains("relates #95: Date API endpoint [Closed]");
            assertThat(result).contains("Check the date format spec");
        }

        @Test
        void shouldHandleIssueNotFound() {
            when(client.getIssue(999)).thenReturn(null);
            assertThat(tools.getIssueFullContext(999)).contains("not found");
        }

        @Test
        void shouldHandleIssueWithNoParent() {
            var issue = issueBuilder(10, "Standalone task")
                    .description("Fix the bug")
                    .build();

            when(client.getIssue(10)).thenReturn(issue);

            String result = tools.getIssueFullContext(10);

            assertThat(result).contains("Issue #10: Standalone task");
            assertThat(result).doesNotContain("Parent:");
            assertThat(result).doesNotContain("Siblings");
        }
    }

    // ── getIssueSiblings ────────────────────────────────────────────

    @Nested
    class GetIssueSiblings {

        @Test
        void shouldListSiblings() {
            var parent = issueBuilder(100, "Epic")
                    .status(new IdName(2, "In Progress"))
                    .children(List.of(child(101, "Part A"), child(102, "Part B"), child(103, "Part C")))
                    .build();

            // getIssueSiblings first calls getIssueForTree(102)
            var issue102tree = issueForTreeWithParent(102, "Part B",
                    new IdName(1, "New"), new IdName(1, "Alice"), new IdName(100, "Epic"));

            var sib101 = issueForTree(101, "Part A",
                    new IdName(3, "Closed"), new IdName(2, "Bob"), null);
            var sib102 = issueForTree(102, "Part B",
                    new IdName(1, "New"), new IdName(1, "Alice"), null);
            var sib103 = issueForTree(103, "Part C",
                    new IdName(1, "New"), null, null);

            when(client.getIssueForTree(102)).thenReturn(issue102tree);
            when(client.getIssue(100)).thenReturn(parent);
            when(client.getIssueForTree(101)).thenReturn(sib101);
            when(client.getIssueForTree(103)).thenReturn(sib103);

            String result = tools.getIssueSiblings(102);

            assertThat(result).contains("Siblings of #102: Part B");
            assertThat(result).contains("Parent: #100 Epic");
            assertThat(result).contains("1/3 closed");
            assertThat(result).contains("#101 Part A");
            assertThat(result).contains("#103 Part C");
        }

        @Test
        void shouldHandleNoParent() {
            var issue = issueForTree(10, "Orphan",
                    new IdName(1, "Open"), null, null);

            when(client.getIssueForTree(10)).thenReturn(issue);

            String result = tools.getIssueSiblings(10);
            assertThat(result).contains("no parent");
        }
    }

    // ── findRelatedClosedIssues ─────────────────────────────────────

    @Nested
    class FindRelatedClosedIssues {

        @Test
        void shouldFindDirectRelatedClosed() {
            var issue = issueBuilder(50, "New task")
                    .description("Do something")
                    .relations(List.of(new RedmineIssue.Relation(1, 50, 40, "relates", null)))
                    .build();

            var closed40 = issueForTree(40, "Old related task",
                    new IdName(3, "Closed"), new IdName(1, "Alice"),
                    "This was already done");

            when(client.getIssue(50)).thenReturn(issue);
            when(client.getIssueForTree(40)).thenReturn(closed40);

            String result = tools.findRelatedClosedIssues(50, null);

            assertThat(result).contains("Direct relations (closed):");
            assertThat(result).contains("#40 Old related task [Closed]");
        }

        @Test
        void shouldFindClosedSiblings() {
            var issue = issueBuilder(60, "My task")
                    .parent(new IdName(50, "Epic"))
                    .description("Description")
                    .build();

            var parentWithChildren = issueForTreeWithChildren(50, "Epic",
                    new IdName(2, "In Progress"),
                    List.of(child(60, "My task"), child(61, "Done task")));

            var closedSib = issueForTree(61, "Done task",
                    new IdName(3, "Closed"), new IdName(2, "Bob"),
                    "Completed implementation");

            when(client.getIssue(60)).thenReturn(issue);
            when(client.getIssueForTree(50)).thenReturn(parentWithChildren);
            when(client.getIssueForTree(61)).thenReturn(closedSib);

            String result = tools.findRelatedClosedIssues(60, null);

            assertThat(result).contains("Closed siblings");
            assertThat(result).contains("#61 Done task [Closed]");
        }
    }

    // ── findLatestAttachment ────────────────────────────────────────

    @Nested
    class FindLatestAttachment {

        @Test
        void shouldFindAttachmentsByPattern() {
            var att1 = attachment(10, "spec_v1.docx", "2025-01-01T00:00:00Z");
            var att2 = attachment(11, "spec_v2.docx", "2025-02-01T00:00:00Z");

            var issue = issueBuilder(100, "Task")
                    .attachments(List.of(att1, att2))
                    .build();

            when(client.getIssue(100)).thenReturn(issue);

            String result = tools.findLatestAttachment("spec", 100, null);

            assertThat(result).contains("Found 2 attachments");
            assertThat(result).contains("LATEST");
            assertThat(result).contains("spec_v2.docx");
            int v2pos = result.indexOf("spec_v2.docx");
            int v1pos = result.indexOf("spec_v1.docx");
            assertThat(v2pos).isLessThan(v1pos);
        }

        @Test
        void shouldSearchParentAndSiblings() {
            var parentAtt = attachment(20, "requirements.pdf", "2025-03-01T00:00:00Z");
            var parent = issueBuilder(50, "Epic")
                    .children(List.of(child(100, "My task"), child(101, "Other task")))
                    .attachments(List.of(parentAtt))
                    .build();

            var sibAtt = attachment(30, "requirements_updated.pdf", "2025-04-01T00:00:00Z");
            var sibling = issueBuilder(101, "Other task")
                    .attachments(List.of(sibAtt))
                    .build();

            var issue = issueBuilder(100, "My task")
                    .parent(new IdName(50, "Epic"))
                    .build();

            when(client.getIssue(100)).thenReturn(issue);
            when(client.getIssue(50)).thenReturn(parent);
            when(client.getIssue(101)).thenReturn(sibling);

            String result = tools.findLatestAttachment("requirements", 100, null);

            assertThat(result).contains("Found 2 attachments");
            assertThat(result).contains("requirements_updated.pdf");
            assertThat(result).contains("requirements.pdf");
        }

        @Test
        void shouldHandleNoMatches() {
            var issue = issueBuilder(100, "Task").build();
            when(client.getIssue(100)).thenReturn(issue);

            String result = tools.findLatestAttachment("spec", 100, null);
            assertThat(result).contains("No matching attachments found");
        }
    }

    // ── getIssueNetwork ─────────────────────────────────────────────

    @Nested
    class GetIssueNetwork {

        @Test
        void shouldBuildNetwork() {
            var root = issueForTreeWithRelations(10, "Root task",
                    new IdName(1, "New"), new IdName(1, "Alice"),
                    List.of(
                            new RedmineIssue.Relation(1, 10, 20, "blocks", null),
                            new RedmineIssue.Relation(2, 10, 30, "relates", null)
                    ));

            var blocked = issueForTree(20, "Blocked task",
                    new IdName(1, "New"), new IdName(2, "Bob"), null);

            var related = issueForTree(30, "Related task",
                    new IdName(3, "Closed"), null, null);

            when(client.getIssueForTree(10)).thenReturn(root);
            when(client.getIssueForTree(20)).thenReturn(blocked);
            when(client.getIssueForTree(30)).thenReturn(related);

            String result = tools.getIssueNetwork(10, 1);

            assertThat(result).contains("Issue network for #10: Root task");
            assertThat(result).contains("Nodes: 3");
            assertThat(result).contains("blocks");
            assertThat(result).contains("#20 Blocked task");
            assertThat(result).contains("relates");
            assertThat(result).contains("#30 Related task");
            assertThat(result).contains("Issue index");
        }

        @Test
        void shouldHandleNotFound() {
            when(client.getIssueForTree(999)).thenReturn(null);
            assertThat(tools.getIssueNetwork(999, null)).contains("not found");
        }
    }

    // ── Test helpers ────────────────────────────────────────────────

    /** Builder-style helper for creating full issues (returned by getIssue) */
    private static IssueBuilder issueBuilder(int id, String subject) {
        return new IssueBuilder(id, subject);
    }

    private static class IssueBuilder {
        private final int id;
        private final String subject;
        private IdName status = new IdName(1, "New");
        private IdName assignedTo;
        private IdName parent;
        private String description;
        private List<RedmineIssue.Child> children;
        private List<RedmineIssue.Relation> relations;
        private List<RedmineIssue.Journal> journals;
        private List<RedmineAttachment> attachments;

        IssueBuilder(int id, String subject) {
            this.id = id;
            this.subject = subject;
        }

        IssueBuilder status(IdName status) { this.status = status; return this; }
        IssueBuilder assignedTo(IdName a) { this.assignedTo = a; return this; }
        IssueBuilder parent(IdName p) { this.parent = p; return this; }
        IssueBuilder description(String d) { this.description = d; return this; }
        IssueBuilder children(List<RedmineIssue.Child> c) { this.children = c; return this; }
        IssueBuilder relations(List<RedmineIssue.Relation> r) { this.relations = r; return this; }
        IssueBuilder journals(List<RedmineIssue.Journal> j) { this.journals = j; return this; }
        IssueBuilder attachments(List<RedmineAttachment> a) { this.attachments = a; return this; }

        RedmineIssue build() {
            return new RedmineIssue(id,
                    new IdName(1, "project"), new IdName(2, "Task"),
                    status, new IdName(2, "Normal"),
                    new IdName(99, "Author"), assignedTo, parent,
                    null, null, subject, description,
                    null, null, 0, null, null, false,
                    "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                    null, attachments, journals, relations, children);
        }
    }

    /** Lightweight issue (returned by getIssueForTree) */
    private static RedmineIssue issueForTree(int id, String subject,
                                              IdName status, IdName assignedTo, String description) {
        return new RedmineIssue(id,
                new IdName(1, "project"), new IdName(2, "Task"),
                status, new IdName(2, "Normal"),
                new IdName(99, "Author"), assignedTo, null,
                null, null, subject, description,
                null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, null);
    }

    private static RedmineIssue issueForTreeWithParent(int id, String subject,
                                                        IdName status, IdName assignedTo, IdName parent) {
        return new RedmineIssue(id,
                new IdName(1, "project"), new IdName(2, "Task"),
                status, new IdName(2, "Normal"),
                new IdName(99, "Author"), assignedTo, parent,
                null, null, subject, null,
                null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, null);
    }

    private static RedmineIssue issueForTreeWithChildren(int id, String subject,
                                                          IdName status,
                                                          List<RedmineIssue.Child> children) {
        return new RedmineIssue(id,
                new IdName(1, "project"), new IdName(2, "Task"),
                status, new IdName(2, "Normal"),
                new IdName(99, "Author"), null, null,
                null, null, subject, null,
                null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, null, children);
    }

    private static RedmineIssue issueForTreeWithRelations(int id, String subject,
                                                           IdName status, IdName assignedTo,
                                                           List<RedmineIssue.Relation> relations) {
        return new RedmineIssue(id,
                new IdName(1, "project"), new IdName(2, "Task"),
                status, new IdName(2, "Normal"),
                new IdName(99, "Author"), assignedTo, null,
                null, null, subject, null,
                null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, null, null, relations, null);
    }

    private static RedmineIssue.Child child(int id, String subject) {
        return new RedmineIssue.Child(id, new IdName(2, "Task"), subject);
    }

    private static RedmineIssue.Journal journal(int id, String user, String notes) {
        return new RedmineIssue.Journal(id, new IdName(1, user), notes,
                "2025-03-15T10:00:00Z", null);
    }

    private static RedmineAttachment attachment(int id, String filename, String createdOn) {
        return new RedmineAttachment(id, filename, 10_000,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "http://redmine/download/" + id + "/" + filename,
                null, new IdName(1, "Author"), createdOn);
    }
}

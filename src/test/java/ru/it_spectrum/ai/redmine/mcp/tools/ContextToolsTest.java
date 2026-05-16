package ru.it_spectrum.ai.redmine.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.IssueSnapshotService;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;
import ru.it_spectrum.ai.redmine.mcp.service.ContextService;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextToolsTest {

    @Mock
    private RedmineClient client;

    @TempDir
    private Path dataDir;

    private ContextTools tools;

    @BeforeEach
    void setUp() {
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), new RedmineMcpProperties(dataDir.toString()));
        var service = new AttachmentService(client,
                new DocumentTextExtractor(), snapshot);
        tools = new ContextTools(new ContextService(client, service),
                ToolJsonTestSupport.json(), ToolJsonTestSupport.errors());
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

            var related = contextIssue(95, "Date API endpoint",
                    new IdName(3, "Closed"), new IdName(3, "Charlie"),
                    "REST endpoint for date-based filtering");

            var siblingCharts = contextIssue(124, "Add charts",
                    new IdName(1, "New"), null, null);

            when(client.getIssue(123)).thenReturn(issue);
            when(client.getIssue(100)).thenReturn(parent);
            when(client.getIssue(124)).thenReturn(siblingCharts);
            when(client.getIssue(95)).thenReturn(related);

            String result = tools.getIssueFullContext(123);

            assertThat(result).contains("\"issue\"");
            assertThat(result).contains("\"id\":123");
            assertThat(result).contains("Implement date filter");
            assertThat(result).contains("Bob");
            assertThat(result).contains("Filter issues by date range");
            assertThat(result).contains("\"parent\"");
            assertThat(result).contains("\"id\":100");
            assertThat(result).contains("Epic: Reports Module");
            assertThat(result).contains("\"siblings\"");
            assertThat(result).contains("\"id\":124");
            assertThat(result).contains("Add charts");
            assertThat(result).contains("\"relates\"");
            assertThat(result).contains("\"id\":95");
            assertThat(result).contains("Date API endpoint");
            assertThat(result).contains("Check the date format spec");
        }

        @Test
        void shouldIncludeFormattedCustomFields() {
            var issue = issueBuilder(123, "Implement date filter")
                    .customFields(List.of(
                            new RedmineIssue.CustomField(3, "# в системе заказчика", false, "502167"),
                            new RedmineIssue.CustomField(10, "applications", true, List.of("rtk", "sskv"))
                    ))
                    .build();

            when(client.getIssue(123)).thenReturn(issue);

            String result = tools.getIssueFullContext(123);

            assertThat(result).contains("custom_fields");
            assertThat(result).contains("# в системе заказчика");
            assertThat(result).contains("502167");
            assertThat(result).contains("applications");
            assertThat(result).contains("rtk");
            assertThat(result).contains("sskv");
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

            assertThat(result).contains("\"id\":10");
            assertThat(result).contains("Standalone task");
            assertThat(result).doesNotContain("Parent:");
            assertThat(result).doesNotContain("Siblings");
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
        private List<RedmineIssue.CustomField> customFields;

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
        IssueBuilder customFields(List<RedmineIssue.CustomField> c) { this.customFields = c; return this; }

        RedmineIssue build() {
            return new RedmineIssue(id,
                    new IdName(1, "project"), new IdName(2, "Task"),
                    status, new IdName(2, "Normal"),
                    new IdName(99, "Author"), assignedTo, parent,
                    null, null, subject, description,
                    null, null, 0, null, null, false,
                    "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                    customFields, null, journals, relations, children);
        }
    }

    private static RedmineIssue contextIssue(int id, String subject,
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

    private static RedmineIssue.Child child(int id, String subject) {
        return new RedmineIssue.Child(id, new IdName(2, "Task"), subject);
    }

    private static RedmineIssue.Journal journal(int id, String user, String notes) {
        return new RedmineIssue.Journal(id, new IdName(1, user), notes,
                "2025-03-15T10:00:00Z", null);
    }

}


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
import ru.it_spectrum.ai.redmine.mcp.service.IssueService;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueFullContextToolTest {

    @Mock
    private RedmineClient client;

    @TempDir
    private Path dataDir;

    private IssueTools tools;

    @BeforeEach
    void setUp() {
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), new RedmineMcpProperties(dataDir.toString()));
        var service = new AttachmentService(client,
                new DocumentTextExtractor(), snapshot);
        var issueService = new IssueService(client);
        tools = new IssueTools(issueService, new ContextService(client, service, issueService));
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

            var result = ToolJsonTestSupport.stringify(tools.getIssueFullContext(123));

            assertThat(result).contains("\"issue\"");
            assertThat(result).contains("\"history\"");
            assertThat(result).contains("\"statusDurations\"");
            assertThat(result).contains("\"id\":123");
            assertThat(result).contains("Implement date filter");
            assertThat(result).contains("Bob");
            assertThat(result).contains("Filter issues by date range");
            assertThat(result).contains("\"contextIssues\"");
            assertThat(result).contains("\"role\":\"parent\"");
            assertThat(result).contains("\"id\":100");
            assertThat(result).contains("Epic: Reports Module");
            assertThat(result).contains("\"role\":\"sibling\"");
            assertThat(result).contains("\"id\":124");
            assertThat(result).contains("Add charts");
            assertThat(result).contains("\"role\":\"related\"");
            assertThat(result).contains("\"relationType\":\"relates\"");
            assertThat(result).contains("\"id\":95");
            assertThat(result).contains("Date API endpoint");
            assertThat(result).contains("\"stats\"");
            assertThat(result).contains("\"siblingsTruncated\":false");
            assertThat(result).contains("\"childrenTruncated\":false");
            assertThat(result).contains("\"relatedTruncated\":false");
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

            var result = ToolJsonTestSupport.stringify(tools.getIssueFullContext(123));

            assertThat(result).contains("custom_fields");
            assertThat(result).contains("# в системе заказчика");
            assertThat(result).contains("502167");
            assertThat(result).contains("applications");
            assertThat(result).contains("rtk");
            assertThat(result).contains("sskv");
        }

        @Test
        void shouldIncludeInterpretedHistory() {
            var details1 = List.of(
                    new RedmineIssue.Detail("attr", "status_id", "1", "2")
            );
            var details2 = List.of(
                    new RedmineIssue.Detail("attr", "assigned_to_id", "42", "43")
            );
            var issue = issueBuilder(123, "Investigate incident")
                    .status(new IdName(2, "In Progress"))
                    .assignedTo(new IdName(43, "Jane"))
                    .journals(List.of(
                            new RedmineIssue.Journal(1, new IdName(42, "John"), "Starting work",
                                    "2025-01-12T14:30:00Z", details1),
                            new RedmineIssue.Journal(2, new IdName(43, "Jane"), null,
                                    "2025-01-15T09:00:00Z", details2)
                    ))
                    .build();

            when(client.getIssue(123)).thenReturn(issue);
            when(client.getIssueStatuses()).thenReturn(List.of(
                    new IdName(1, "New"), new IdName(2, "In Progress")));

            var result = ToolJsonTestSupport.stringify(tools.getIssueFullContext(123));

            assertThat(result).contains("\"history\"");
            assertThat(result).contains("\"kind\":\"CREATED\"");
            assertThat(result).contains("\"kind\":\"UPDATED\"");
            assertThat(result).contains("\"actor\":\"John\"");
            assertThat(result).contains("\"fieldLabel\":\"Status\"");
            assertThat(result).contains("\"oldValue\":\"New\"");
            assertThat(result).contains("\"newValue\":\"In Progress\"");
            assertThat(result).contains("Starting work");
            assertThat(result).contains("\"fieldLabel\":\"Assigned to\"");
            assertThat(result).contains("\"oldValue\":\"John\"");
            assertThat(result).contains("\"newValue\":\"Jane\"");
            assertThat(result).contains("\"statusDurations\"");
        }

        @Test
        void shouldHandleIssueNotFound() {
            when(client.getIssue(999)).thenReturn(null);
            assertThatThrownBy(() -> tools.getIssueFullContext(999))
                    .hasMessageContaining("not found");
        }

        @Test
        void shouldHandleIssueWithNoParent() {
            var issue = issueBuilder(10, "Standalone task")
                    .description("Fix the bug")
                    .build();

            when(client.getIssue(10)).thenReturn(issue);

            var result = ToolJsonTestSupport.stringify(tools.getIssueFullContext(10));

            assertThat(result).contains("\"id\":10");
            assertThat(result).contains("Standalone task");
            assertThat(result).contains("\"contextIssues\":[]");
            assertThat(result).contains("\"siblingsTruncated\":false");
            assertThat(result).contains("\"childrenTruncated\":false");
            assertThat(result).contains("\"relatedTruncated\":false");
        }

        @Test
        void shouldMergeMultipleRolesForSameContextIssue() throws Exception {
            var issue = issueBuilder(123, "Implement date filter")
                    .children(List.of(child(201, "Validate date range")))
                    .relations(List.of(new RedmineIssue.Relation(7, 123, 201, "relates", null)))
                    .build();
            var contextIssue = contextIssue(201, "Validate date range",
                    new IdName(1, "New"), new IdName(2, "Bob"),
                    "Reject invalid date ranges");

            when(client.getIssue(123)).thenReturn(issue);
            when(client.getIssue(201)).thenReturn(contextIssue);

            var result = ToolJsonTestSupport.stringify(tools.getIssueFullContext(123));

            var root = new ObjectMapper().readTree(result);
            var contextIssues = root.get("contextIssues");
            assertThat(contextIssues).hasSize(1);
            assertThat(contextIssues.get(0).get("issue").get("id").asInt()).isEqualTo(201);
            assertThat(contextIssues.get(0).get("roles")).hasSize(2);
            assertThat(result).contains("\"role\":\"child\"");
            assertThat(result).contains("\"role\":\"related\"");
            assertThat(result).contains("\"relationType\":\"relates\"");
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


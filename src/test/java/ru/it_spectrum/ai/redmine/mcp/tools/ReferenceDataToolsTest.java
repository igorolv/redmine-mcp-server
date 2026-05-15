package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineQuery;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReferenceDataToolsTest {

    @Mock
    private RedmineClient client;

    private ReferenceDataTools tools;

    @BeforeEach
    void setUp() {
        tools = new ReferenceDataTools(client, ToolJsonTestSupport.json());
    }

    // --- listStatuses ---

    @Test
    void shouldListStatuses() {
        when(client.getIssueStatuses()).thenReturn(List.of(
                new IdName(1, "New"),
                new IdName(2, "In Progress"),
                new IdName(5, "Closed")
        ));

        String result = tools.listStatuses();

        assertThat(result).contains("\"id\":1");
        assertThat(result).contains("\"name\":\"New\"");
        assertThat(result).contains("\"name\":\"In Progress\"");
        assertThat(result).contains("\"name\":\"Closed\"");
    }

    @Test
    void shouldReturnMessageWhenNoStatusesFound() {
        when(client.getIssueStatuses()).thenReturn(List.of());

        String result = tools.listStatuses();

        assertThat(result).isEqualTo("[]");
    }

    // --- listTrackers ---

    @Test
    void shouldListTrackers() {
        when(client.getTrackers()).thenReturn(List.of(
                new IdName(1, "Bug"),
                new IdName(2, "Feature"),
                new IdName(3, "Support")
        ));

        String result = tools.listTrackers();

        assertThat(result).contains("\"name\":\"Bug\"");
        assertThat(result).contains("\"name\":\"Feature\"");
        assertThat(result).contains("\"name\":\"Support\"");
    }

    @Test
    void shouldReturnMessageWhenNoTrackersFound() {
        when(client.getTrackers()).thenReturn(List.of());

        String result = tools.listTrackers();

        assertThat(result).isEqualTo("[]");
    }

    // --- listPriorities ---

    @Test
    void shouldListPriorities() {
        when(client.getIssuePriorities()).thenReturn(List.of(
                new IdName(1, "Low"),
                new IdName(2, "Normal"),
                new IdName(3, "High"),
                new IdName(4, "Urgent")
        ));

        String result = tools.listPriorities();

        assertThat(result).contains("\"name\":\"Low\"");
        assertThat(result).contains("\"name\":\"Normal\"");
        assertThat(result).contains("\"name\":\"High\"");
        assertThat(result).contains("\"name\":\"Urgent\"");
    }

    @Test
    void shouldReturnMessageWhenNoPrioritiesFound() {
        when(client.getIssuePriorities()).thenReturn(List.of());

        String result = tools.listPriorities();

        assertThat(result).isEqualTo("[]");
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

        assertThat(result).contains("\"name\":\"Backend\"");
        assertThat(result).contains("\"name\":\"Frontend\"");
        assertThat(result).contains("\"name\":\"DevOps\"");
    }

    @Test
    void shouldReturnMessageWhenNoCategoriesFound() {
        when(client.getIssueCategories("empty-project")).thenReturn(List.of());

        String result = tools.listIssueCategories("empty-project");

        assertThat(result).isEqualTo("[]");
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

        assertThat(result).contains("\"name\":\"Design\"");
        assertThat(result).contains("\"name\":\"Development\"");
        assertThat(result).contains("\"name\":\"Testing\"");
    }

    @Test
    void shouldReturnMessageWhenNoActivitiesFound() {
        when(client.getTimeEntryActivities()).thenReturn(List.of());

        String result = tools.listTimeEntryActivities();

        assertThat(result).isEqualTo("[]");
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

        assertThat(result).contains("\"total_count\":3");
        assertThat(result).contains("\"name\":\"My open bugs\"");
        assertThat(result).contains("\"project_id\":5");
        assertThat(result).contains("\"name\":\"Sprint backlog\"");
        assertThat(result).contains("\"is_public\":true");
        assertThat(result).contains("\"name\":\"Overdue tasks\"");
        assertThat(result).contains("\"project_id\":10");
    }

    @Test
    void shouldReturnMessageWhenNoQueriesFound() {
        when(client.getQueries(0, 25)).thenReturn(new RedmineQuery.Page(List.of(), 0, 0, 25));

        String result = tools.listQueries(null, null);

        assertThat(result).contains("\"queries\":[]");
        assertThat(result).contains("\"total_count\":0");
    }
}

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
        tools = new ReferenceDataTools(client);
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

        assertThat(result).contains("Issue statuses:");
        assertThat(result).contains("- [1] New");
        assertThat(result).contains("- [2] In Progress");
        assertThat(result).contains("- [5] Closed");
    }

    @Test
    void shouldReturnMessageWhenNoStatusesFound() {
        when(client.getIssueStatuses()).thenReturn(List.of());

        String result = tools.listStatuses();

        assertThat(result).isEqualTo("No statuses found");
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

        assertThat(result).contains("Trackers:");
        assertThat(result).contains("- [1] Bug");
        assertThat(result).contains("- [2] Feature");
        assertThat(result).contains("- [3] Support");
    }

    @Test
    void shouldReturnMessageWhenNoTrackersFound() {
        when(client.getTrackers()).thenReturn(List.of());

        String result = tools.listTrackers();

        assertThat(result).isEqualTo("No trackers found");
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

        assertThat(result).contains("Issue priorities:");
        assertThat(result).contains("- [1] Low");
        assertThat(result).contains("- [2] Normal");
        assertThat(result).contains("- [3] High");
        assertThat(result).contains("- [4] Urgent");
    }

    @Test
    void shouldReturnMessageWhenNoPrioritiesFound() {
        when(client.getIssuePriorities()).thenReturn(List.of());

        String result = tools.listPriorities();

        assertThat(result).isEqualTo("No priorities found");
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

        assertThat(result).contains("Issue categories for project my-project");
        assertThat(result).contains("- [1] Backend");
        assertThat(result).contains("- [2] Frontend");
        assertThat(result).contains("- [3] DevOps");
    }

    @Test
    void shouldReturnMessageWhenNoCategoriesFound() {
        when(client.getIssueCategories("empty-project")).thenReturn(List.of());

        String result = tools.listIssueCategories("empty-project");

        assertThat(result).isEqualTo("No issue categories found for project: empty-project");
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

        assertThat(result).contains("Time entry activities:");
        assertThat(result).contains("- [8] Design");
        assertThat(result).contains("- [9] Development");
        assertThat(result).contains("- [10] Testing");
    }

    @Test
    void shouldReturnMessageWhenNoActivitiesFound() {
        when(client.getTimeEntryActivities()).thenReturn(List.of());

        String result = tools.listTimeEntryActivities();

        assertThat(result).isEqualTo("No time entry activities found");
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

        assertThat(result).contains("Saved queries: 3 total");
        assertThat(result).contains("- [1] My open bugs (project #5)");
        assertThat(result).contains("- [2] Sprint backlog [public]");
        assertThat(result).contains("- [3] Overdue tasks (project #10)");
    }

    @Test
    void shouldReturnMessageWhenNoQueriesFound() {
        when(client.getQueries(0, 25)).thenReturn(new RedmineQuery.Page(List.of(), 0, 0, 25));

        String result = tools.listQueries(null, null);

        assertThat(result).isEqualTo("No saved queries found");
    }
}

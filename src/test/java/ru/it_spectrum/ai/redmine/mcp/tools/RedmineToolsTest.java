package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedmineToolsTest {

    @Mock
    private RedmineClient client;

    private RedmineTools tools;

    @BeforeEach
    void setUp() {
        tools = new RedmineTools(client);
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
}

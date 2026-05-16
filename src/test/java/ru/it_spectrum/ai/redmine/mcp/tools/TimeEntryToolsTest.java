package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;
import ru.it_spectrum.ai.redmine.mcp.service.TimeEntryService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeEntryToolsTest {

    @Mock
    private RedmineClient client;

    private TimeEntryTools tools;

    @BeforeEach
    void setUp() {
        tools = new TimeEntryTools(new TimeEntryService(client));
    }

    // --- listTimeEntries ---

    @Test
    void shouldListTimeEntries() {
        var entries = List.of(
                new RedmineTimeEntry(1, new IdName(1, "backend"), new IdName(101, "Fix bug"),
                        new IdName(42, "John"), new IdName(9, "Development"),
                        2.5, "Fixed null pointer", "2025-03-01", null, null),
                new RedmineTimeEntry(2, new IdName(1, "backend"), null,
                        new IdName(43, "Jane"), new IdName(10, "Testing"),
                        1.0, null, "2025-03-01", null, null)
        );
        when(client.getTimeEntries(null, null, null, null, null, 0, 25))
                .thenReturn(new RedmineTimeEntry.Page(entries, 2, 0, 25));

        var result = ToolJsonTestSupport.stringify(tools.listTimeEntries(null, null, null, null, null, null, null));

        assertThat(result).contains("\"totalCount\":2");
        assertThat(result).contains("2025-03-01");
        assertThat(result).contains("\"hours\":2.5");
        assertThat(result).contains("John");
        assertThat(result).contains("Development");
        assertThat(result).contains("\"id\":101");
        assertThat(result).contains("Fixed null pointer");
        assertThat(result).contains("\"hours\":1.0");
        assertThat(result).contains("Jane");
        assertThat(result).contains("Testing");
    }

    @Test
    void shouldListTimeEntriesWithFilters() {
        var entries = List.of(
                new RedmineTimeEntry(3, new IdName(1, "backend"), new IdName(200, "Deploy"),
                        new IdName(42, "John"), new IdName(9, "Development"),
                        4.0, "Deployment", "2025-03-15", null, null)
        );
        when(client.getTimeEntries("backend", 200, 42, "2025-03-01", "2025-03-31", 0, 10))
                .thenReturn(new RedmineTimeEntry.Page(entries, 1, 0, 10));

        var result = ToolJsonTestSupport.stringify(tools.listTimeEntries("backend", 200, 42, "2025-03-01", "2025-03-31", 10, 0));

        assertThat(result).contains("\"totalCount\":1");
        assertThat(result).contains("\"hours\":4.0");
        assertThat(result).contains("John");
        assertThat(result).contains("Development");
    }

    // --- getMyTimeEntries ---

    @Test
    void shouldGetMyTimeEntries() {
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                null, null, null, null, null, null, null);
        when(client.getCurrentUser()).thenReturn(user);

        var entries = List.of(
                new RedmineTimeEntry(1, null, new IdName(101, "Task"),
                        new IdName(42, "John"), new IdName(9, "Development"),
                        3.0, "Coding", "2025-03-10", null, null)
        );
        when(client.getTimeEntries(null, null, 42, null, null, 0, 25))
                .thenReturn(new RedmineTimeEntry.Page(entries, 1, 0, 25));

        var result = ToolJsonTestSupport.stringify(tools.getMyTimeEntries(null, null, null, null, null, null));

        assertThat(result).contains("\"user\"");
        assertThat(result).contains("\"login\":\"jdoe\"");
        assertThat(result).contains("\"name\":\"John Doe\"");
        assertThat(result).contains("\"hours\":3.0");
        assertThat(result).contains("\"id\":101");
    }

    @Test
    void shouldHandleCurrentUserNotAvailableForTimeEntries() {
        when(client.getCurrentUser()).thenReturn(null);

        assertThatThrownBy(() -> tools.getMyTimeEntries(null, null, null, null, null, null))
                .hasMessageContaining("current user unavailable");
    }
}

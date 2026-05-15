package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineMembership;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserToolsTest {

    @Mock
    private RedmineClient client;

    private UserTools tools;

    @BeforeEach
    void setUp() {
        tools = new UserTools(client, ToolJsonTestSupport.json(), ToolJsonTestSupport.errors());
    }

    @Test
    void shouldReturnCurrentUser() {
        var memberships = List.of(
                new RedmineMembership(1, new IdName(10, "backend"), null, null,
                        List.of(new IdName(1, "Developer"), new IdName(2, "Manager")))
        );
        var user = new RedmineUser(42, "jdoe", "John", "Doe",
                "jdoe@example.com", null, null, null, "2025-03-01T10:00:00Z",
                List.of(new IdName(5, "Developers")), memberships);
        when(client.getCurrentUser()).thenReturn(user);

        String result = tools.getCurrentUser();

        assertThat(result).contains("\"id\":42");
        assertThat(result).contains("\"login\":\"jdoe\"");
        assertThat(result).contains("\"firstname\":\"John\"");
        assertThat(result).contains("\"lastname\":\"Doe\"");
        assertThat(result).contains("jdoe@example.com");
        assertThat(result).contains("Developers");
        assertThat(result).contains("backend");
        assertThat(result).contains("Developer");
        assertThat(result).contains("Manager");
    }

    @Test
    void shouldHandleUserWithNoGroupsOrMemberships() {
        var user = new RedmineUser(1, "solo", "Solo", "Dev",
                null, null, null, null, "2025-01-01T00:00:00Z", null, null);
        when(client.getCurrentUser()).thenReturn(user);

        String result = tools.getCurrentUser();

        assertThat(result).contains("\"firstname\":\"Solo\"");
        assertThat(result).contains("\"lastname\":\"Dev\"");
        assertThat(result).doesNotContain("Groups:");
        assertThat(result).doesNotContain("Project memberships:");
    }

    @Test
    void shouldHandleUserNotAvailable() {
        when(client.getCurrentUser()).thenReturn(null);

        String result = tools.getCurrentUser();

        assertThat(result).contains("\"kind\":\"unavailable\"");
        assertThat(result).contains("current user unavailable");
    }
}

package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.TestRedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineMembership;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineProject;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;
import ru.it_spectrum.ai.redmine.mcp.service.ProjectService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectToolsTest {

    @Mock
    private RedmineClient client;

    private ProjectTools tools;

    @BeforeEach
    void setUp() {
        tools = new ProjectTools(new ProjectService(client), TestRedmineMcpProperties.defaults());
    }

    // --- listProjects ---

    @Test
    void shouldListProjects() {
        var projects = List.of(
                new RedmineProject(1, "Backend", "backend", "Backend services", null, null, 1, true,
                        null, null, null, null),
                new RedmineProject(2, "Frontend", "frontend", null, null, null, 1, true,
                        null, null, null, null)
        );
        when(client.getProjects(0, 25)).thenReturn(new RedmineProject.Page(projects, 2, 0, 25));

        var result = ToolJsonTestSupport.stringify(tools.listProjects(null, null));

        assertThat(result).contains("\"totalCount\":2");
        assertThat(result).contains("Backend");
        assertThat(result).contains("backend");
        assertThat(result).contains("Backend services");
        assertThat(result).contains("Frontend");
        assertThat(result).contains("frontend");
    }

    @Test
    void shouldListProjectsWithPagination() {
        var projects = List.of(
                new RedmineProject(3, "Mobile", "mobile", null, null, null, 1, true,
                        null, null, null, null)
        );
        when(client.getProjects(10, 5)).thenReturn(new RedmineProject.Page(projects, 15, 10, 5));

        var result = ToolJsonTestSupport.stringify(tools.listProjects(5, 10));

        assertThat(result).contains("\"totalCount\":15");
        assertThat(result).contains("Mobile");
        assertThat(result).contains("mobile");
    }

    // --- getProject ---

    @Test
    void shouldGetProjectDetails() {
        var project = new RedmineProject(1, "Backend", "backend", "Main backend",
                "https://example.com", new IdName(0, "Parent Corp"), 1, true,
                "2024-01-01", "2025-01-01",
                List.of(new IdName(1, "Bug"), new IdName(2, "Feature")),
                List.of(new RedmineProject.NameOnly("issue_tracking"), new RedmineProject.NameOnly("wiki")));
        when(client.getProject("backend")).thenReturn(project);

        var result = ToolJsonTestSupport.stringify(tools.getProject("backend"));

        assertThat(result).contains("\"name\":\"Backend\"");
        assertThat(result).contains("\"identifier\":\"backend\"");
        assertThat(result).contains("Parent Corp");
        assertThat(result).contains("Main backend");
        assertThat(result).contains("https://example.com");
        assertThat(result).contains("\"isPublic\":true");
        assertThat(result).contains("Bug");
        assertThat(result).contains("Feature");
        assertThat(result).contains("issue_tracking");
        assertThat(result).contains("wiki");
    }

    @Test
    void shouldHandleProjectNotFound() {
        when(client.getProject("nonexistent")).thenReturn(null);

        assertThatThrownBy(() -> tools.getProject("nonexistent"))
                .hasMessageContaining("project nonexistent not found");
    }

    // --- listProjectMembers ---

    @Test
    void shouldListProjectMembers() {
        var memberships = List.of(
                new RedmineMembership(1, null, new IdName(10, "Alice"), null,
                        List.of(new IdName(1, "Developer"))),
                new RedmineMembership(2, null, null, new IdName(20, "QA Team"),
                        List.of(new IdName(2, "Tester")))
        );
        when(client.getProjectMembers("backend", 0, 100))
                .thenReturn(new RedmineMembership.Page(memberships, 2, 0, 100));

        var result = ToolJsonTestSupport.stringify(tools.listProjectMembers("backend", null, null));

        assertThat(result).contains("\"totalCount\":2");
        assertThat(result).contains("Alice");
        assertThat(result).contains("Developer");
        assertThat(result).contains("QA Team");
        assertThat(result).contains("Tester");
    }

    // --- listVersions ---

    @Test
    void shouldListVersions() {
        var versions = List.of(
                new RedmineVersion(1, null, "v1.0", "First release", "closed",
                        "2025-01-15", null, null, null, null),
                new RedmineVersion(2, null, "v2.0", null, "open",
                        null, null, null, null, null)
        );
        when(client.getProjectVersions("backend")).thenReturn(versions);

        var result = ToolJsonTestSupport.stringify(tools.listVersions("backend"));

        assertThat(result).contains("\"name\":\"v1.0\"");
        assertThat(result).contains("\"status\":\"closed\"");
        assertThat(result).contains("2025-01-15");
        assertThat(result).contains("First release");
        assertThat(result).contains("\"name\":\"v2.0\"");
        assertThat(result).contains("\"status\":\"open\"");
    }

    @Test
    void shouldHandleNoVersions() {
        when(client.getProjectVersions("empty")).thenReturn(List.of());

        var result = ToolJsonTestSupport.stringify(tools.listVersions("empty"));

        assertThat(result).isEqualTo("[]");
    }
}

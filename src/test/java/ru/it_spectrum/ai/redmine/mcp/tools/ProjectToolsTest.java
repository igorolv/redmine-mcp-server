package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineMembership;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineProject;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineVersion;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectToolsTest {

    @Mock
    private RedmineClient client;

    private ProjectTools tools;

    @BeforeEach
    void setUp() {
        tools = new ProjectTools(client);
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

        String result = tools.listProjects(null, null);

        assertThat(result).contains("Projects: 2 total");
        assertThat(result).contains("Backend [backend] (id: 1)");
        assertThat(result).contains("Backend services");
        assertThat(result).contains("Frontend [frontend] (id: 2)");
    }

    @Test
    void shouldListProjectsWithPagination() {
        var projects = List.of(
                new RedmineProject(3, "Mobile", "mobile", null, null, null, 1, true,
                        null, null, null, null)
        );
        when(client.getProjects(10, 5)).thenReturn(new RedmineProject.Page(projects, 15, 10, 5));

        String result = tools.listProjects(5, 10);

        assertThat(result).contains("Projects: 15 total (showing 11-11)");
        assertThat(result).contains("Mobile [mobile]");
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

        String result = tools.getProject("backend");

        assertThat(result).contains("Project: Backend");
        assertThat(result).contains("Identifier: backend");
        assertThat(result).contains("Parent: Parent Corp");
        assertThat(result).contains("Description: Main backend");
        assertThat(result).contains("Homepage: https://example.com");
        assertThat(result).contains("Public: yes");
        assertThat(result).contains("Trackers: Bug, Feature");
        assertThat(result).contains("Modules: issue_tracking, wiki");
    }

    @Test
    void shouldHandleProjectNotFound() {
        when(client.getProject("nonexistent")).thenReturn(null);

        String result = tools.getProject("nonexistent");

        assertThat(result).isEqualTo("Project 'nonexistent' not found");
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

        String result = tools.listProjectMembers("backend", null, null);

        assertThat(result).contains("Members of project 'backend': 2 total");
        assertThat(result).contains("Alice — Developer");
        assertThat(result).contains("QA Team (group) — Tester");
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

        String result = tools.listVersions("backend");

        assertThat(result).contains("Versions for project 'backend' (2):");
        assertThat(result).contains("v1.0 (status: closed, due: 2025-01-15)");
        assertThat(result).contains("First release");
        assertThat(result).contains("v2.0 (status: open)");
    }

    @Test
    void shouldHandleNoVersions() {
        when(client.getProjectVersions("empty")).thenReturn(List.of());

        String result = tools.listVersions("empty");

        assertThat(result).isEqualTo("No versions found for project 'empty'");
    }
}

package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.MembershipPage;
import ru.it_spectrum.ai.redmine.mcp.api.Project;
import ru.it_spectrum.ai.redmine.mcp.api.ProjectPage;
import ru.it_spectrum.ai.redmine.mcp.api.Version;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;

import java.util.List;

@Service
public class ProjectService {
    private final RedmineClient client;

    public ProjectService(RedmineClient client) {
        this.client = client;
    }

    public ProjectPage listProjects(int offset, int limit) {
        return ProjectPage.from(client.getProjects(offset, limit));
    }

    public Project getProjectOrThrow(String projectId) {
        var project = client.getProject(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
        return Project.from(project);
    }

    public MembershipPage listMembers(String projectId, int offset, int limit) {
        return MembershipPage.from(client.getProjectMembers(projectId, offset, limit));
    }

    public List<Version> listVersions(String projectId) {
        var versions = client.getProjectVersions(projectId);
        if (versions == null) {
            return List.of();
        }
        return versions.stream().map(Version::from).toList();
    }
}

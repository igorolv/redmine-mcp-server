package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineMembership;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineProject;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;

import java.util.List;

@Service
public class ProjectService {
    private final RedmineClient client;

    public ProjectService(RedmineClient client) {
        this.client = client;
    }

    public RedmineProject.Page listProjects(int offset, int limit) {
        return client.getProjects(offset, limit);
    }

    public RedmineProject getProjectOrThrow(String projectId) {
        var project = client.getProject(projectId);
        if (project == null) {
            throw new ResourceNotFoundException("project", projectId);
        }
        return project;
    }

    public RedmineMembership.Page listMembers(String projectId, int offset, int limit) {
        return client.getProjectMembers(projectId, offset, limit);
    }

    public List<RedmineVersion> listVersions(String projectId) {
        return client.getProjectVersions(projectId);
    }
}

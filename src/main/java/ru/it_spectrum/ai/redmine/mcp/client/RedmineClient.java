package ru.it_spectrum.ai.redmine.mcp.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineProject;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineSearchResult;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RedmineClient {

    private final RestClient restClient;

    public RedmineClient(RestClient redmineRestClient) {
        this.restClient = redmineRestClient;
    }

    /**
     * Full-text search via /search.json, then fetches issue details for matching results.
     */
    public SearchWithIssues searchIssues(String query, String projectId, int offset, int limit) {
        var searchUri = buildSearchUri(query, projectId, offset, limit);

        var searchResult = restClient.get()
                .uri(searchUri)
                .retrieve()
                .body(RedmineSearchResult.class);

        if (searchResult == null || searchResult.results() == null) {
            return new SearchWithIssues(List.of(), 0, offset, limit);
        }

        // Filter only issue-type results and fetch their details
        List<Integer> issueIds = searchResult.results().stream()
                .filter(r -> "issue".equals(r.type()))
                .map(RedmineSearchResult.ResultItem::id)
                .toList();

        List<RedmineIssue> issues = fetchIssuesByIds(issueIds);

        return new SearchWithIssues(issues, searchResult.totalCount(), offset, limit);
    }

    /**
     * Get a single issue by ID with all available details.
     */
    public RedmineIssue getIssue(int issueId) {
        var response = restClient.get()
                .uri("/issues/{id}.json?include=attachments,journals,relations", issueId)
                .retrieve()
                .body(RedmineIssue.Single.class);

        return response != null ? response.issue() : null;
    }

    /**
     * Get attachment metadata.
     */
    public RedmineAttachment getAttachment(int attachmentId) {
        var response = restClient.get()
                .uri("/attachments/{id}.json", attachmentId)
                .retrieve()
                .body(RedmineAttachment.Single.class);

        return response != null ? response.attachment() : null;
    }

    /**
     * Download attachment content as bytes.
     */
    public byte[] downloadAttachment(String contentUrl) {
        return restClient.get()
                .uri(contentUrl)
                .retrieve()
                .body(byte[].class);
    }

    /**
     * Get a list of projects with pagination.
     */
    public RedmineProject.Page getProjects(int offset, int limit) {
        var response = restClient.get()
                .uri("/projects.json?offset={offset}&limit={limit}", offset, limit)
                .retrieve()
                .body(RedmineProject.Page.class);

        return response != null ? response : new RedmineProject.Page(List.of(), 0, offset, limit);
    }

    /**
     * Get project details by identifier or numeric ID.
     */
    public RedmineProject getProject(String projectId) {
        var response = restClient.get()
                .uri("/projects/{id}.json?include=trackers,enabled_modules", projectId)
                .retrieve()
                .body(RedmineProject.Single.class);

        return response != null ? response.project() : null;
    }

    /**
     * Fetch issues by a list of IDs using /issues.json?issue_id=1,2,3
     */
    private List<RedmineIssue> fetchIssuesByIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        String idsParam = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        var response = restClient.get()
                .uri("/issues.json?issue_id={ids}&status_id=*&limit={limit}", idsParam, ids.size())
                .retrieve()
                .body(RedmineIssue.Page.class);

        return response != null ? response.issues() : List.of();
    }

    private String buildSearchUri(String query, String projectId, int offset, int limit) {
        var sb = new StringBuilder();

        if (projectId != null && !projectId.isBlank()) {
            sb.append("/projects/").append(projectId);
        }

        sb.append("/search.json?q=").append(query)
                .append("&issues=1")
                .append("&titles_only=0")
                .append("&offset=").append(offset)
                .append("&limit=").append(limit);

        return sb.toString();
    }

    public record SearchWithIssues(
            List<RedmineIssue> issues,
            int totalCount,
            int offset,
            int limit
    ) {
    }
}

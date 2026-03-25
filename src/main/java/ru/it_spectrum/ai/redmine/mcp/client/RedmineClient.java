package ru.it_spectrum.ai.redmine.mcp.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineMembership;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineProject;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineSearchResult;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineVersion;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineWikiPage;

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
     * List issues with flexible filtering.
     */
    public RedmineIssue.Page listIssues(String projectId, String statusId, Integer trackerId,
                                         Integer assignedToId, Integer priorityId, Integer versionId,
                                         String sort, int offset, int limit) {
        var sb = new StringBuilder("/issues.json?");
        if (projectId != null && !projectId.isBlank()) sb.append("project_id=").append(projectId).append("&");
        if (statusId != null && !statusId.isBlank()) sb.append("status_id=").append(statusId).append("&");
        if (trackerId != null) sb.append("tracker_id=").append(trackerId).append("&");
        if (assignedToId != null) sb.append("assigned_to_id=").append(assignedToId).append("&");
        if (priorityId != null) sb.append("priority_id=").append(priorityId).append("&");
        if (versionId != null) sb.append("fixed_version_id=").append(versionId).append("&");
        if (sort != null && !sort.isBlank()) sb.append("sort=").append(sort).append("&");
        sb.append("offset=").append(offset).append("&limit=").append(limit);

        var response = restClient.get()
                .uri(sb.toString())
                .retrieve()
                .body(RedmineIssue.Page.class);

        return response != null ? response : new RedmineIssue.Page(List.of(), 0, offset, limit);
    }

    /**
     * Get project members.
     */
    public RedmineMembership.Page getProjectMembers(String projectId, int offset, int limit) {
        var response = restClient.get()
                .uri("/projects/{id}/memberships.json?offset={offset}&limit={limit}", projectId, offset, limit)
                .retrieve()
                .body(RedmineMembership.Page.class);

        return response != null ? response : new RedmineMembership.Page(List.of(), 0, offset, limit);
    }

    /**
     * Get project versions/milestones.
     */
    public List<RedmineVersion> getProjectVersions(String projectId) {
        var response = restClient.get()
                .uri("/projects/{id}/versions.json", projectId)
                .retrieve()
                .body(RedmineVersion.Page.class);

        return response != null && response.versions() != null ? response.versions() : List.of();
    }

    /**
     * Get a wiki page by project and page title.
     */
    public RedmineWikiPage getWikiPage(String projectId, String pageTitle) {
        var response = restClient.get()
                .uri("/projects/{projectId}/wiki/{page}.json?include=attachments", projectId, pageTitle)
                .retrieve()
                .body(RedmineWikiPage.Single.class);

        return response != null ? response.wikiPage() : null;
    }

    /**
     * Global full-text search across all Redmine content (issues, wiki, news, etc).
     */
    public RedmineSearchResult search(String query, int offset, int limit) {
        var uri = "/search.json?q=" + query
                + "&all_words=1"
                + "&titles_only=0"
                + "&offset=" + offset
                + "&limit=" + limit;

        var response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(RedmineSearchResult.class);

        return response != null ? response : new RedmineSearchResult(List.of(), 0, offset, limit);
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

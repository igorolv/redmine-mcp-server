package ru.it_spectrum.ai.redmine.mcp.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineMembership;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineProject;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineQuery;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineSearchResult;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPage;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RedmineClient {
    private static final String FULL_ISSUE_PATH =
            "/issues/%d.json?include=attachments,journals,relations,children,changesets";

    private final RestClient restClient;

    public RedmineClient(RestClient redmineRestClient) {
        this.restClient = redmineRestClient;
    }

    /**
     * Full-text search via /search.json, then fetches issue summaries for matching results.
     */
    public SearchWithIssueSummaries searchIssues(String query, String projectId, int offset, int limit) {
        var searchUri = buildSearchUri(query, projectId, offset, limit);

        var searchResult = restClient.get()
                .uri(searchUri)
                .retrieve()
                .body(RedmineSearchResult.class);

        if (searchResult == null || searchResult.results() == null) {
            return new SearchWithIssueSummaries(List.of(), 0, offset, limit);
        }

        // Filter only issue-type results and fetch their summaries.
        List<Integer> issueIds = searchResult.results().stream()
                .filter(r -> "issue".equals(r.type()))
                .map(RedmineSearchResult.ResultItem::id)
                .toList();

        List<RedmineIssueSummary> issues = fetchIssuesByIds(issueIds);

        return new SearchWithIssueSummaries(issues, searchResult.totalCount(), offset, limit);
    }

    /**
     * Get a single issue by ID with all available details.
     */
    public RedmineIssue getIssue(int issueId) {
        var response = restClient.get()
                .uri(fullIssuePath(issueId))
                .retrieve()
                .body(RedmineIssue.Single.class);

        return response != null ? response.issue() : null;
    }

    public static String fullIssueSource(int issueId) {
        return "GET " + fullIssuePath(issueId);
    }

    private static String fullIssuePath(int issueId) {
        return FULL_ISSUE_PATH.formatted(issueId);
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
        URI uri = URI.create(contentUrl);
        return restClient.get()
                .uri(uri)
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
    public RedmineIssueSummary.Page listIssues(String projectId, String statusId, Integer trackerId,
                                               Integer assignedToId, Integer priorityId, Integer versionId,
                                               String sort, int offset, int limit) {
        return listIssues(projectId, statusId, trackerId, assignedToId, priorityId, versionId,
                sort, null, Map.of(), offset, limit);
    }

    /**
     * List issues with flexible filtering, optionally using a saved query.
     */
    public RedmineIssueSummary.Page listIssues(String projectId, String statusId, Integer trackerId,
                                               Integer assignedToId, Integer priorityId, Integer versionId,
                                               String sort, Integer queryId, int offset, int limit) {
        return listIssues(projectId, statusId, trackerId, assignedToId, priorityId, versionId,
                sort, queryId, Map.of(), offset, limit);
    }

    /**
     * List issues with flexible filtering, including dynamic custom field filters like cf_10=rtk.
     */
    public RedmineIssueSummary.Page listIssues(String projectId, String statusId, Integer trackerId,
                                               Integer assignedToId, Integer priorityId, Integer versionId,
                                               String sort, Integer queryId, Map<String, String> customFieldFilters,
                                               int offset, int limit) {
        var params = new LinkedHashMap<String, String>();
        putIfPresent(params, "project_id", projectId);
        putIfPresent(params, "query_id", queryId);
        putIfPresent(params, "status_id", statusId);
        putIfPresent(params, "tracker_id", trackerId);
        putIfPresent(params, "assigned_to_id", assignedToId);
        putIfPresent(params, "priority_id", priorityId);
        putIfPresent(params, "fixed_version_id", versionId);
        putIfPresent(params, "sort", sort);
        if (customFieldFilters != null) {
            customFieldFilters.forEach((key, value) -> putIfPresent(params, key, value));
        }
        params.put("offset", String.valueOf(offset));
        params.put("limit", String.valueOf(limit));

        var response = restClient.get()
                .uri("/issues.json" + buildQueryString(params))
                .retrieve()
                .body(RedmineIssueSummary.Page.class);

        return response != null ? response : new RedmineIssueSummary.Page(List.of(), 0, offset, limit);
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
     * List all wiki pages in a project.
     */
    public List<RedmineWikiPage> getWikiIndex(String projectId) {
        var response = restClient.get()
                .uri("/projects/{projectId}/wiki/index.json", projectId)
                .retrieve()
                .body(RedmineWikiPage.Index.class);

        return response != null && response.wikiPages() != null ? response.wikiPages() : List.of();
    }

    /**
     * Get the currently authenticated user.
     */
    public RedmineUser getCurrentUser() {
        var response = restClient.get()
                .uri("/users/current.json?include=memberships,groups")
                .retrieve()
                .body(RedmineUser.Single.class);

        return response != null ? response.user() : null;
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
     * Get all issue statuses.
     */
    public List<IdName> getIssueStatuses() {
        var response = restClient.get()
                .uri("/issue_statuses.json")
                .retrieve()
                .body(IdName.IssueStatuses.class);

        return response != null && response.items() != null ? response.items() : List.of();
    }

    /**
     * Get all trackers.
     */
    public List<IdName> getTrackers() {
        var response = restClient.get()
                .uri("/trackers.json")
                .retrieve()
                .body(IdName.Trackers.class);

        return response != null && response.trackers() != null ? response.trackers() : List.of();
    }

    /**
     * List time entries with optional filtering.
     */
    public RedmineTimeEntry.Page getTimeEntries(String projectId, Integer issueId,
                                                 Integer userId, String from, String to,
                                                 int offset, int limit) {
        var sb = new StringBuilder("/time_entries.json?");
        if (projectId != null && !projectId.isBlank()) sb.append("project_id=").append(projectId).append("&");
        if (issueId != null) sb.append("issue_id=").append(issueId).append("&");
        if (userId != null) sb.append("user_id=").append(userId).append("&");
        if (from != null && !from.isBlank()) sb.append("from=").append(from).append("&");
        if (to != null && !to.isBlank()) sb.append("to=").append(to).append("&");
        sb.append("offset=").append(offset).append("&limit=").append(limit);

        var response = restClient.get()
                .uri(sb.toString())
                .retrieve()
                .body(RedmineTimeEntry.Page.class);

        return response != null ? response : new RedmineTimeEntry.Page(List.of(), 0, offset, limit);
    }

    /**
     * Get issue priorities.
     */
    public List<IdName> getIssuePriorities() {
        var response = restClient.get()
                .uri("/enumerations/issue_priorities.json")
                .retrieve()
                .body(IdName.IssuePriorities.class);

        return response != null && response.items() != null ? response.items() : List.of();
    }

    /**
     * Get issue categories for a project.
     */
    public List<IdName> getIssueCategories(String projectId) {
        var response = restClient.get()
                .uri("/projects/{id}/issue_categories.json", projectId)
                .retrieve()
                .body(IdName.IssueCategories.class);

        return response != null && response.items() != null ? response.items() : List.of();
    }

    /**
     * Get time entry activities.
     */
    public List<IdName> getTimeEntryActivities() {
        var response = restClient.get()
                .uri("/enumerations/time_entry_activities.json")
                .retrieve()
                .body(IdName.TimeEntryActivities.class);

        return response != null && response.items() != null ? response.items() : List.of();
    }

    /**
     * Get saved queries with pagination.
     */
    public RedmineQuery.Page getQueries(int offset, int limit) {
        var response = restClient.get()
                .uri("/queries.json?offset={offset}&limit={limit}", offset, limit)
                .retrieve()
                .body(RedmineQuery.Page.class);

        return response != null ? response : new RedmineQuery.Page(List.of(), 0, offset, limit);
    }

    /**
     * Fetch issue summaries by a list of IDs using /issues.json?issue_id=1,2,3
     */
    private List<RedmineIssueSummary> fetchIssuesByIds(List<Integer> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        String idsParam = ids.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));

        var response = restClient.get()
                .uri("/issues.json?issue_id={ids}&status_id=*&limit={limit}", idsParam, ids.size())
                .retrieve()
                .body(RedmineIssueSummary.Page.class);

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

    private void putIfPresent(Map<String, String> params, String key, Object value) {
        if (value == null) {
            return;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return;
        }
        params.put(key, text);
    }

    private String buildQueryString(Map<String, String> params) {
        if (params.isEmpty()) {
            return "";
        }
        return "?" + params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record SearchWithIssueSummaries(
            List<RedmineIssueSummary> issues,
            int totalCount,
            int offset,
            int limit
    ) {
    }
}

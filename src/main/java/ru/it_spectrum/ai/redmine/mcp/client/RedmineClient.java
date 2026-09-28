package ru.it_spectrum.ai.redmine.mcp.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RedmineClient {
    /** Redmine's default and maximum page size for {@code /issues.json}. */
    private static final int ISSUE_BATCH_SIZE = 100;
    private static final Set<String> ISSUE_SEARCH_TYPES = Set.of("issue", "issue-closed");
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
        var searchResult = search(query, projectId, Set.of(SearchType.ISSUES), false, offset, limit);

        if (searchResult == null || searchResult.results() == null) {
            return new SearchWithIssueSummaries(List.of(), 0, offset, limit);
        }

        // Redmine reports closed issues as "issue-closed"; both are issues.
        List<Integer> issueIds = searchResult.results().stream()
                .filter(r -> ISSUE_SEARCH_TYPES.contains(r.type()))
                .map(RedmineSearchResult.ResultItem::id)
                .toList();

        var summariesById = getIssueSummariesByIds(issueIds).stream()
                .collect(Collectors.toMap(RedmineIssueSummary::id, summary -> summary, (a, b) -> a));
        // Keep the search relevance order rather than the /issues.json order.
        List<RedmineIssueSummary> issues = issueIds.stream()
                .map(summariesById::get)
                .filter(Objects::nonNull)
                .toList();

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

    /**
     * Get a single issue with its child list only: enough to name it and enumerate its subtasks
     * without paying for journals, changesets and attachments.
     */
    public RedmineIssue getIssueWithChildren(int issueId) {
        var response = restClient.get()
                .uri("/issues/{id}.json?include=children", issueId)
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
        try {
            var response = restClient.get()
                    .uri("/projects/{projectId}/wiki/{page}.json?include=attachments", projectId, pageTitle)
                    .retrieve()
                    .body(RedmineWikiPage.Single.class);

            return response != null ? response.wikiPage() : null;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return null;
            }
            throw e;
        }
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
     * Full-text search via /search.json, optionally scoped to a project and content types.
     */
    public RedmineSearchResult search(String query, String projectId, Set<SearchType> types,
                                      boolean allWords, int offset, int limit) {
        var response = restClient.get()
                .uri(uriBuilder -> buildSearchUri(uriBuilder, query, projectId, types, allWords, offset, limit))
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
     * Fetch summaries for the given issue IDs in batched {@code /issues.json} requests (any status,
     * across projects). IDs the API key cannot see are silently absent from the result.
     */
    public List<RedmineIssueSummary> getIssueSummariesByIds(Collection<Integer> ids) {
        var distinct = List.copyOf(new LinkedHashSet<>(ids));
        var result = new ArrayList<RedmineIssueSummary>(distinct.size());
        for (int from = 0; from < distinct.size(); from += ISSUE_BATCH_SIZE) {
            var chunk = distinct.subList(from, Math.min(from + ISSUE_BATCH_SIZE, distinct.size()));
            String idsParam = chunk.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));

            var response = restClient.get()
                    .uri("/issues.json?issue_id={ids}&status_id=*&limit={limit}", idsParam, chunk.size())
                    .retrieve()
                    .body(RedmineIssueSummary.Page.class);
            if (response != null && response.issues() != null) {
                result.addAll(response.issues());
            }
        }
        return result;
    }

    private URI buildSearchUri(UriBuilder uriBuilder, String query, String projectId, Set<SearchType> types,
                               boolean allWords, int offset, int limit) {
        if (projectId != null && !projectId.isBlank()) {
            uriBuilder.pathSegment("projects", projectId);
        }

        uriBuilder.path("/search.json")
                .queryParam("q", query);
        if (allWords) {
            uriBuilder.queryParam("all_words", "1");
        }
        if (types != null) {
            types.forEach(type -> uriBuilder.queryParam(type.parameterName(), "1"));
        }

        return uriBuilder
                .queryParam("offset", offset)
                .queryParam("limit", limit)
                .build();
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

    public enum SearchType {
        ISSUES("issues"),
        NEWS("news"),
        DOCUMENTS("documents"),
        CHANGESETS("changesets"),
        WIKI_PAGES("wiki_pages"),
        MESSAGES("messages"),
        PROJECTS("projects");

        private final String parameterName;

        SearchType(String parameterName) {
            this.parameterName = parameterName;
        }

        public String parameterName() {
            return parameterName;
        }
    }
}

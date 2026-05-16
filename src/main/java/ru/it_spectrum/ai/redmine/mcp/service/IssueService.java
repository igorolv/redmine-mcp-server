package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.model.IssueHistoryView;
import ru.it_spectrum.ai.redmine.mcp.model.IssueTreeView;
import ru.it_spectrum.ai.redmine.mcp.model.MyIssuesResult;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class IssueService {

    public static final int MAX_TREE_DEPTH = 5;
    public static final int MAX_TREE_ISSUES = 50;
    public static final int DEFAULT_TREE_DEPTH = 2;

    private static final Map<String, String> FIELD_NAMES = Map.ofEntries(
            Map.entry("status_id", "Status"),
            Map.entry("assigned_to_id", "Assigned to"),
            Map.entry("priority_id", "Priority"),
            Map.entry("tracker_id", "Tracker"),
            Map.entry("fixed_version_id", "Target version"),
            Map.entry("done_ratio", "Done ratio"),
            Map.entry("subject", "Subject"),
            Map.entry("description", "Description"),
            Map.entry("due_date", "Due date"),
            Map.entry("start_date", "Start date"),
            Map.entry("estimated_hours", "Estimated hours"),
            Map.entry("category_id", "Category"),
            Map.entry("parent_id", "Parent task"),
            Map.entry("is_private", "Private")
    );

    private final RedmineClient client;

    public IssueService(RedmineClient client) {
        this.client = client;
    }

    // --- Basic lookup ---

    public Optional<RedmineIssue> find(int issueId) {
        return Optional.ofNullable(client.getIssue(issueId));
    }

    public RedmineIssue findOrThrow(int issueId) {
        return find(issueId).orElseThrow(() -> new IssueNotFoundException(issueId));
    }

    // --- Listing / search ---

    public RedmineIssueSummary.Page list(String projectId, String statusId, Integer trackerId,
                                         Integer assignedToId, Integer priorityId, Integer versionId,
                                         Integer queryId, Map<String, String> customFieldFilters,
                                         String sort, int offset, int limit) {
        return client.listIssues(projectId, statusId, trackerId, assignedToId, priorityId,
                versionId, sort, queryId, customFieldFilters != null ? customFieldFilters : Map.of(),
                offset, limit);
    }

    public Map<String, String> parseCustomFieldFilters(String customFieldFilters) {
        if (customFieldFilters == null || customFieldFilters.isBlank()) {
            return Map.of();
        }
        var filters = new java.util.LinkedHashMap<String, String>();
        for (String token : customFieldFilters.split("[&\\r\\n]+")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separatorIndex = trimmed.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex == trimmed.length() - 1) {
                throw new IllegalArgumentException(
                        "Invalid customFieldFilters token '%s'. Use format like 'cf_10=rtk&cf_3=502167'."
                                .formatted(trimmed));
            }
            String key = decodeQueryToken(trimmed.substring(0, separatorIndex));
            String value = decodeQueryToken(trimmed.substring(separatorIndex + 1));
            if (!key.matches("cf_\\d+")) {
                throw new IllegalArgumentException(
                        "Invalid custom field key '%s'. Expected keys like cf_10.".formatted(key));
            }
            if (value.isBlank()) {
                throw new IllegalArgumentException(
                        "Invalid custom field value for '%s'. Expected a non-empty value.".formatted(key));
            }
            filters.put(key, value);
        }
        return filters;
    }

    public RedmineClient.SearchWithIssueSummaries searchIssues(String query, String projectId, int offset, int limit) {
        return client.searchIssues(query, projectId, offset, limit);
    }

    public ru.it_spectrum.ai.redmine.mcp.client.model.RedmineSearchResult searchAll(String query, int offset, int limit) {
        return client.search(query, offset, limit);
    }

    public Optional<MyIssuesResult> getMyIssues(String projectId, String statusId, String sort,
                                                int offset, int limit) {
        var user = client.getCurrentUser();
        if (user == null) {
            return Optional.empty();
        }
        var page = client.listIssues(projectId, statusId, null, user.id(), null, null,
                sort, offset, limit);
        return Optional.of(new MyIssuesResult(user, page));
    }

    // --- Tree ---

    public IssueTreeView getTree(int issueId, Integer depth) {
        return getTree(issueId, depth, new IssueFetchContext(client));
    }

    public IssueTreeView getTree(int issueId, Integer depth, IssueFetchContext ctx) {
        int actualDepth = depth != null
                ? Math.min(Math.max(depth, 0), MAX_TREE_DEPTH)
                : DEFAULT_TREE_DEPTH;

        var visited = new HashSet<Integer>();
        var fetchCount = new int[]{0};

        RedmineIssue root = fetchForTree(issueId, visited, fetchCount, ctx);
        if (root == null) {
            throw new IssueNotFoundException(issueId);
        }

        var ancestors = new ArrayList<RedmineIssue>();
        RedmineIssue current = root;
        while (current.parent() != null && fetchCount[0] < MAX_TREE_ISSUES) {
            var parent = fetchForTree(current.parent().id(), visited, fetchCount, ctx);
            if (parent == null) {
                break;
            }
            ancestors.add(parent);
            current = parent;
        }

        var subtree = nodeFromIssue(root);
        expandChildren(subtree, root, actualDepth, 0, visited, fetchCount, ctx);

        var relations = root.relations() != null ? root.relations() : List.<RedmineIssue.Relation>of();
        boolean limitReached = fetchCount[0] >= MAX_TREE_ISSUES;
        return new IssueTreeView(root, ancestors, subtree, relations, fetchCount[0], limitReached);
    }

    // --- History ---

    public IssueHistoryView buildHistory(RedmineIssue issue) {
        return buildHistory(issue, new IssueFetchContext(client));
    }

    public IssueHistoryView buildHistory(RedmineIssue issue, IssueFetchContext ctx) {
        var timeline = new ArrayList<IssueHistoryView.TimelineEntry>();
        var statusSnapshots = new ArrayList<StatusSnapshot>();

        var customFieldNames = customFieldNames(issue);

        String initialStatus = findInitialStatus(issue, ctx);
        statusSnapshots.add(new StatusSnapshot(initialStatus, issue.createdOn()));

        var createdChanges = new ArrayList<IssueHistoryView.FieldChange>();
        createdChanges.add(new IssueHistoryView.FieldChange("Status", null, nameOf(issue.status())));
        createdChanges.add(new IssueHistoryView.FieldChange("Priority", null, nameOf(issue.priority())));
        if (issue.assignedTo() != null) {
            createdChanges.add(new IssueHistoryView.FieldChange("Assigned to", null, issue.assignedTo().name()));
        }
        timeline.add(new IssueHistoryView.TimelineEntry(
                IssueHistoryView.Kind.CREATED,
                issue.createdOn(),
                nameOf(issue.author()),
                List.copyOf(createdChanges),
                null
        ));

        if (issue.journals() != null) {
            for (var journal : issue.journals()) {
                var changes = new ArrayList<IssueHistoryView.FieldChange>();
                if (journal.details() != null) {
                    for (var detail : journal.details()) {
                        var fc = toFieldChange(detail, ctx, issue, customFieldNames);
                        if (fc != null) {
                            changes.add(fc);
                        }
                        if ("attr".equals(detail.property()) && "status_id".equals(detail.name())
                                && detail.newValue() != null) {
                            String statusName = resolveRefValue(ctx.statuses(), detail.newValue());
                            statusSnapshots.add(new StatusSnapshot(statusName, journal.createdOn()));
                        }
                    }
                }
                boolean hasNotes = journal.notes() != null && !journal.notes().isBlank();
                if (!changes.isEmpty() || hasNotes) {
                    timeline.add(new IssueHistoryView.TimelineEntry(
                            IssueHistoryView.Kind.UPDATED,
                            journal.createdOn(),
                            journal.user() != null ? journal.user().name() : "unknown",
                            List.copyOf(changes),
                            hasNotes ? journal.notes() : null
                    ));
                }
            }
        }

        var durations = computeStatusDurations(statusSnapshots);
        return new IssueHistoryView(List.copyOf(timeline), durations);
    }

    // --- Tree helpers ---

    private RedmineIssue fetchForTree(int issueId, Set<Integer> visited, int[] fetchCount,
                                      IssueFetchContext ctx) {
        if (!visited.add(issueId) || fetchCount[0] >= MAX_TREE_ISSUES) {
            return null;
        }
        fetchCount[0]++;
        return ctx.getIssue(issueId);
    }

    private IssueTreeView.TreeNode nodeFromIssue(RedmineIssue issue) {
        return new IssueTreeView.TreeNode(
                issue.id(), issue.subject(),
                issue.status(), issue.tracker(), issue.assignedTo(),
                new ArrayList<>(), false
        );
    }

    private IssueTreeView.TreeNode stubFromChild(RedmineIssue.Child child) {
        return new IssueTreeView.TreeNode(
                child.id(), child.subject(),
                null, child.tracker(), null,
                List.of(), true
        );
    }

    private void expandChildren(IssueTreeView.TreeNode parentNode, RedmineIssue parentIssue,
                                int maxDepth, int currentDepth,
                                Set<Integer> visited, int[] fetchCount,
                                IssueFetchContext ctx) {
        if (parentIssue.children() == null || parentIssue.children().isEmpty()) {
            return;
        }
        if (currentDepth >= maxDepth) {
            for (var child : parentIssue.children()) {
                parentNode.children().add(stubFromChild(child));
            }
            return;
        }
        for (var child : parentIssue.children()) {
            if (fetchCount[0] >= MAX_TREE_ISSUES) {
                break;
            }
            var fullChild = fetchForTree(child.id(), visited, fetchCount, ctx);
            if (fullChild != null) {
                var childNode = nodeFromIssue(fullChild);
                parentNode.children().add(childNode);
                expandChildren(childNode, fullChild, maxDepth, currentDepth + 1, visited, fetchCount, ctx);
            } else {
                parentNode.children().add(stubFromChild(child));
            }
        }
    }

    // --- History helpers ---

    private record StatusSnapshot(String statusName, String timestamp) {
    }

    private String findInitialStatus(RedmineIssue issue, IssueFetchContext ctx) {
        if (issue.journals() != null) {
            for (var journal : issue.journals()) {
                if (journal.details() == null) continue;
                for (var detail : journal.details()) {
                    if ("attr".equals(detail.property()) && "status_id".equals(detail.name())
                            && detail.oldValue() != null) {
                        return resolveRefValue(ctx.statuses(), detail.oldValue());
                    }
                }
            }
        }
        return nameOf(issue.status());
    }

    private IssueHistoryView.FieldChange toFieldChange(RedmineIssue.Detail detail, IssueFetchContext ctx,
                                                       RedmineIssue issue, Map<String, String> customFieldNames) {
        if (!"attr".equals(detail.property()) && !"cf".equals(detail.property())) {
            return null;
        }
        String fieldLabel = formatFieldLabel(detail.property(), detail.name(), customFieldNames);
        String oldVal = resolveDetailValue(detail.property(), detail.name(), detail.oldValue(), ctx, issue);
        String newVal = resolveDetailValue(detail.property(), detail.name(), detail.newValue(), ctx, issue);
        if (oldVal == null && newVal == null) {
            return null;
        }
        return new IssueHistoryView.FieldChange(fieldLabel, oldVal, newVal);
    }

    private String formatFieldLabel(String property, String name, Map<String, String> customFieldNames) {
        if ("cf".equals(property)) {
            String customName = customFieldNames.get(name);
            if (customName != null && !customName.isBlank()) {
                return "%s [cf_%s]".formatted(customName, name);
            }
            return "Custom field [cf_%s]".formatted(name);
        }
        return FIELD_NAMES.getOrDefault(name, name);
    }

    private String resolveDetailValue(String property, String name, String value,
                                      IssueFetchContext ctx, RedmineIssue issue) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!"attr".equals(property)) {
            return value;
        }
        return switch (name) {
            case "status_id" -> resolveRefValue(ctx.statuses(), value);
            case "priority_id" -> resolveRefValue(ctx.priorities(), value);
            case "tracker_id" -> resolveRefValue(ctx.trackers(), value);
            case "fixed_version_id" -> issue.project() != null
                    ? resolveRefValue(ctx.versions(String.valueOf(issue.project().id())), value)
                    : value;
            case "assigned_to_id" -> resolveUserName(value, issue);
            case "done_ratio" -> value + "%";
            default -> value;
        };
    }

    private String resolveRefValue(Map<Integer, String> map, String value) {
        if (value == null) return null;
        try {
            return map.getOrDefault(Integer.parseInt(value), value);
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private String resolveUserName(String value, RedmineIssue issue) {
        int userId;
        try {
            userId = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return value;
        }
        if (issue.author() != null && issue.author().id() == userId) {
            return issue.author().name();
        }
        if (issue.assignedTo() != null && issue.assignedTo().id() == userId) {
            return issue.assignedTo().name();
        }
        if (issue.journals() != null) {
            for (var j : issue.journals()) {
                if (j.user() != null && j.user().id() == userId) {
                    return j.user().name();
                }
            }
        }
        return value;
    }

    private Map<String, String> customFieldNames(RedmineIssue issue) {
        var map = new HashMap<String, String>();
        if (issue.customFields() != null) {
            for (var cf : issue.customFields()) {
                map.put(String.valueOf(cf.id()), cf.name());
            }
        }
        return map;
    }

    private List<IssueHistoryView.StatusDuration> computeStatusDurations(List<StatusSnapshot> snapshots) {
        var durations = new ArrayList<IssueHistoryView.StatusDuration>();
        for (int i = 0; i < snapshots.size(); i++) {
            var s = snapshots.get(i);
            if (i + 1 < snapshots.size()) {
                var next = snapshots.get(i + 1);
                durations.add(new IssueHistoryView.StatusDuration(
                        s.statusName(), s.timestamp(), next.timestamp(),
                        formatDuration(s.timestamp(), next.timestamp())
                ));
            } else {
                durations.add(new IssueHistoryView.StatusDuration(
                        s.statusName(), s.timestamp(), null,
                        formatDuration(s.timestamp(), null)
                ));
            }
        }
        return List.copyOf(durations);
    }

    private String formatDuration(String from, String to) {
        try {
            var start = java.time.OffsetDateTime.parse(from, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            var end = to != null
                    ? java.time.OffsetDateTime.parse(to, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    : java.time.OffsetDateTime.now();
            long days = java.time.Duration.between(start, end).toDays();
            if (days == 0) {
                long hours = java.time.Duration.between(start, end).toHours();
                return hours <= 1 ? "< 1 hour" : hours + " hours";
            }
            return days == 1 ? "1 day" : days + " days";
        } catch (java.time.format.DateTimeParseException e) {
            return "?";
        }
    }

    private static String nameOf(IdName idName) {
        return idName != null ? idName.name() : "—";
    }

    private String decodeQueryToken(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8).trim();
    }

}

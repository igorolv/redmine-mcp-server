package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineVersion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalysisTools {
    private static final int MAX_PAGES = 5;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_BLOCKER_DEPTH = 10;
    private static final int MAX_BLOCKER_ISSUES = 30;

    private final RedmineClient client;
    private final JsonResponses json;
    private final ToolErrors errors;

    public AnalysisTools(RedmineClient client, JsonResponses json, ToolErrors errors) {
        this.client = client;
        this.json = json;
        this.errors = errors;
    }

    // ── getProjectSummary ──────────────────────────────────────────────

    @McpTool(description = "Get an aggregated summary of a Redmine project: issue counts by status, " +
            "tracker, priority, and assignee; overdue count; estimated vs spent hours. " +
            "Optionally filter by version/milestone. One call replaces dozens of listIssues calls.")
    public String getProjectSummary(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId
    ) {
        var openBatch = fetchAllIssues(projectId, "open", versionId, null);
        int closedCount = client.listIssues(projectId, "closed", null, null,
                null, versionId, null, null, 0, 1).totalCount();

        var issues = openBatch.issues;
        int openCount = openBatch.totalCount;
        int total = openCount + closedCount;

        var byStatus = groupAndCount(issues, i -> name(i.status()));
        var byTracker = groupAndCount(issues, i -> name(i.tracker()));
        var byPriority = groupAndCount(issues, i -> name(i.priority()));
        var byAssignee = groupAndCount(issues,
                i -> i.assignedTo() != null ? i.assignedTo().name() : "Unassigned");
        var assignees = byAssignee.entrySet().stream()
                .map(e -> new AssigneeSummary(
                        e.getKey(),
                        e.getValue(),
                        (int) issues.stream()
                                .filter(i -> e.getKey().equals(i.assignedTo() != null
                                        ? i.assignedTo().name() : "Unassigned"))
                                .filter(this::isOverdue)
                                .count()))
                .toList();
        long overdueCount = issues.stream().filter(this::isOverdue).count();
        double estimated = issues.stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(RedmineIssue::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssue::spentHours).sum();

        return json.write(new ProjectSummaryResult(
                projectId, versionId,
                new IssueCountSummary(openCount, closedCount, total),
                openBatch.truncated(),
                issues.size(),
                byStatus, byTracker, byPriority, assignees,
                (int) overdueCount,
                new HoursSummary(estimated, spent)
        ));
    }

    // ── getUserWorkload ────────────────────────────────────────────────

    @McpTool(description = "Get workload analysis for a user: open issues grouped by project and priority, " +
            "overdue count, estimated vs spent hours, and top issues by priority. " +
            "Defaults to the current authenticated user if no userId is provided.")
    public String getUserWorkload(
            @McpToolParam(description = "User ID (optional, defaults to current user)", required = false) Integer userId,
            @McpToolParam(description = "Project identifier to limit scope (optional)", required = false) String projectId
    ) {
        int actualUserId;
        String userName;

        if (userId != null) {
            actualUserId = userId;
            userName = "User #" + userId;
        } else {
            var user = client.getCurrentUser();
            if (user == null) {
                return errors.unavailable("current user");
            }
            actualUserId = user.id();
            userName = user.firstname() + " " + user.lastname();
        }

        var batch = fetchAllIssues(projectId, "open", null, actualUserId);
        var issues = batch.issues;

        double estimated = issues.stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(RedmineIssue::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssue::spentHours).sum();
        long overdueCount = issues.stream().filter(this::isOverdue).count();

        var byProject = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> name(i.project()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        var projects = byProject.entrySet().stream()
                .map(e -> new ProjectWorkload(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream()
                                .filter(i -> i.estimatedHours() != null)
                                .mapToDouble(RedmineIssue::estimatedHours).sum(),
                        groupAndCount(e.getValue(), i -> name(i.priority()))))
                .toList();

        var priorityOrder = client.getIssuePriorities().stream()
                .collect(Collectors.toMap(p -> p.name(), p -> p.id()));
        var sorted = issues.stream()
                .sorted(Comparator.<RedmineIssue, Integer>comparing(
                                i -> priorityOrder.getOrDefault(name(i.priority()), 0))
                        .reversed()
                        .thenComparing(i -> i.dueDate() != null ? i.dueDate() : "9999"))
                .limit(10)
                .toList();

        return json.write(new UserWorkloadResult(
                actualUserId, userName, projectId,
                batch.totalCount, issues.size(), batch.truncated(),
                new HoursSummary(estimated, spent),
                (int) overdueCount,
                projects,
                sorted
        ));
    }

    // ── getVersionChangelog ────────────────────────────────────────────

    @McpTool(description = "Get a changelog for a specific version/milestone: all issues grouped by tracker, " +
            "with status and summary. Shows both open and closed issues for the version.")
    public String getVersionChangelog(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        // Find version metadata
        var versions = client.getProjectVersions(projectId);
        RedmineVersion version = versions.stream()
                .filter(v -> v.id() == versionId)
                .findFirst().orElse(null);

        var batch = fetchAllIssues(projectId, "*", versionId, null);
        var issues = batch.issues;

        var byTracker = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> name(i.tracker()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        long closed = issues.stream().filter(i -> isClosedStatus(i.status())).count();
        long open = issues.size() - closed;
        double estimated = issues.stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(RedmineIssue::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssue::spentHours).sum();

        return json.write(new VersionChangelogResult(
                projectId, versionId, version,
                batch.totalCount, issues.size(), batch.truncated(),
                byTracker,
                new IssueCountSummary((int) open, (int) closed, issues.size()),
                new HoursSummary(estimated, spent)
        ));
    }

    // ── getBlockerChain ────────────────────────────────────────────────

    @McpTool(description = "Trace the full chain of blocking dependencies for an issue. " +
            "Shows what blocks this issue (must be resolved first) and what this issue blocks. " +
            "Follows blocks/blocked_by relations recursively to reveal the critical path.")
    public String getBlockerChain(
            @McpToolParam(description = "Issue ID number") int issueId
    ) {
        var visited = new HashSet<Integer>();
        var fetchCount = new int[]{0};

        RedmineIssue root = fetchBlockerIssue(issueId, visited, fetchCount);
        if (root == null) {
            return errors.notFound("issue", "#" + issueId);
        }

        var blockedBy = new ArrayList<BlockerNode>();
        collectBlockers(root, true, visited, fetchCount, blockedBy, 0);

        // Reset visited for downstream (keep root)
        visited.clear();
        visited.add(issueId);
        fetchCount[0] = 1;

        // Collect "blocks" (downstream: what waits on this issue)
        var blocks = new ArrayList<BlockerNode>();
        collectBlockers(root, false, visited, fetchCount, blocks, 0);

        int upstreamDepth = blockedBy.stream().mapToInt(n -> n.depth).max().orElse(0);
        int downstreamDepth = blocks.stream().mapToInt(n -> n.depth).max().orElse(0);
        int totalDepth = upstreamDepth + 1 + downstreamDepth;
        int totalIssues = 1 + blockedBy.size() + blocks.size();

        return json.write(new BlockerChainResult(
                root, blockedBy, blocks, totalDepth, totalIssues
        ));
    }

    // ── getStaleIssues ─────────────────────────────────────────────────

    @McpTool(description = "Find open issues that haven't been updated for a specified number of days. " +
            "Sorted by staleness (oldest first). Useful for identifying neglected or forgotten tasks.")
    public String getStaleIssues(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Minimum days since last update, default 30", required = false) Integer daysSinceUpdate,
            @McpToolParam(description = "Maximum number of results, default 25", required = false) Integer limit
    ) {
        int minDays = daysSinceUpdate != null ? daysSinceUpdate : 30;
        int actualLimit = limit != null ? Math.min(Math.max(limit, 1), 100) : 25;

        // Fetch open issues sorted by updated_on ascending (oldest updates first)
        var page = client.listIssues(projectId, "open", null, null,
                null, null, "updated_on:asc", null, 0, actualLimit);

        LocalDate cutoff = LocalDate.now().minusDays(minDays);

        var stale = page.issues().stream()
                .filter(i -> {
                    LocalDate updated = parseDate(i.updatedOn());
                    return updated != null && updated.isBefore(cutoff);
                })
                .toList();

        long oldest = stale.stream().mapToLong(i -> daysAgo(i.updatedOn())).max().orElse(0);
        var staleItems = stale.stream()
                .map(i -> new StaleIssue(i, daysAgo(i.updatedOn()), isOverdue(i)))
                .toList();
        return json.write(new StaleIssuesResult(projectId, minDays, actualLimit, staleItems, oldest));
    }

    // ── getReleaseRisks ────────────────────────────────────────────────

    @McpTool(description = "Assess release risks for a version/milestone: identifies open blockers, " +
            "overdue issues, high-priority unresolved issues, and unassigned tasks. " +
            "Provides a risk score summary.")
    public String getReleaseRisks(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId
    ) {
        // Version metadata
        var versions = client.getProjectVersions(projectId);
        RedmineVersion version = versions.stream()
                .filter(v -> v.id() == versionId)
                .findFirst().orElse(null);

        // Open issues for this version
        var batch = fetchAllIssues(projectId, "open", versionId, null);
        var issues = batch.issues;

        var blockers = issues.stream()
                .filter(i -> i.relations() != null && i.relations().stream()
                        .anyMatch(r -> "blocks".equals(r.relationType()) && r.issueId() == i.id()))
                .toList();
        var overdue = issues.stream().filter(this::isOverdue).toList();
        var priorities = client.getIssuePriorities();
        Set<String> highPriorityNames = new HashSet<>();
        if (priorities.size() >= 3) {
            int highThreshold = priorities.size() - priorities.size() / 3;
            for (int i = highThreshold; i < priorities.size(); i++) {
                highPriorityNames.add(priorities.get(i).name());
            }
        } else if (!priorities.isEmpty()) {
            highPriorityNames.add(priorities.getLast().name());
        }

        var highPriority = issues.stream()
                .filter(i -> highPriorityNames.contains(name(i.priority())))
                .toList();
        var unassigned = issues.stream().filter(i -> i.assignedTo() == null).toList();
        var categories = new ArrayList<RiskCategory>();
        if (!blockers.isEmpty()) categories.add(new RiskCategory("blockers", blockers));
        if (!overdue.isEmpty()) categories.add(new RiskCategory("overdue", overdue));
        if (!highPriority.isEmpty()) categories.add(new RiskCategory("high_priority", highPriority));
        if (!unassigned.isEmpty()) categories.add(new RiskCategory("unassigned", unassigned));
        int riskItems = categories.stream().mapToInt(c -> c.issues().size()).sum();

        return json.write(new ReleaseRisksResult(
                projectId, versionId, version,
                batch.totalCount, issues.size(), batch.truncated(),
                highPriorityNames,
                categories,
                new RiskScore(riskItems, categories.size(), issues.size())
        ));
    }

    // ── compareVersions ────────────────────────────────────────────────

    @McpTool(description = "Compare two versions/milestones: shows issues unique to each version, " +
            "shared issues, and status completion percentages. " +
            "Useful for understanding scope changes between releases.")
    public String compareVersions(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "First version/milestone ID") int versionId1,
            @McpToolParam(description = "Second version/milestone ID") int versionId2
    ) {
        var versions = client.getProjectVersions(projectId);
        var v1Meta = versions.stream().filter(v -> v.id() == versionId1).findFirst().orElse(null);
        var v2Meta = versions.stream().filter(v -> v.id() == versionId2).findFirst().orElse(null);

        String v1Name = v1Meta != null ? v1Meta.name() : "#" + versionId1;
        String v2Name = v2Meta != null ? v2Meta.name() : "#" + versionId2;

        var batch1 = fetchAllIssues(projectId, "*", versionId1, null);
        var batch2 = fetchAllIssues(projectId, "*", versionId2, null);

        var ids1 = batch1.issues.stream().map(RedmineIssue::id).collect(Collectors.toSet());
        var ids2 = batch2.issues.stream().map(RedmineIssue::id).collect(Collectors.toSet());

        var onlyIn1 = batch1.issues.stream().filter(i -> !ids2.contains(i.id())).toList();
        var onlyIn2 = batch2.issues.stream().filter(i -> !ids1.contains(i.id())).toList();
        var inBoth = batch1.issues.stream().filter(i -> ids2.contains(i.id())).toList();

        long closed1 = batch1.issues.stream().filter(i -> isClosedStatus(i.status())).count();
        long closed2 = batch2.issues.stream().filter(i -> isClosedStatus(i.status())).count();

        return json.write(new VersionComparisonResult(
                projectId,
                new VersionScope(versionId1, v1Name, v1Meta, batch1.totalCount,
                        batch1.issues.size(), (int) closed1, batch1.issues.size() - (int) closed1,
                        completionPercent(closed1, batch1.issues.size()), batch1.truncated()),
                new VersionScope(versionId2, v2Name, v2Meta, batch2.totalCount,
                        batch2.issues.size(), (int) closed2, batch2.issues.size() - (int) closed2,
                        completionPercent(closed2, batch2.issues.size()), batch2.truncated()),
                onlyIn1, onlyIn2, inBoth
        ));
    }

    // ── Shared helpers ─────────────────────────────────────────────────

    private record IssuesBatch(List<RedmineIssue> issues, int totalCount) {
        boolean truncated() {
            return issues.size() < totalCount;
        }
    }

    public record IssueCountSummary(int open, int closed, int total) {
    }

    public record HoursSummary(double estimated, double spent) {
    }

    public record AssigneeSummary(String assignee, int total, int overdue) {
    }

    public record ProjectSummaryResult(
            String projectId,
            Integer versionId,
            IssueCountSummary counts,
            boolean truncated,
            int analyzedOpenIssues,
            Map<String, Integer> byStatus,
            Map<String, Integer> byTracker,
            Map<String, Integer> byPriority,
            List<AssigneeSummary> byAssignee,
            int overdue,
            HoursSummary hours
    ) {
    }

    public record ProjectWorkload(
            String project,
            int issueCount,
            double estimatedHours,
            Map<String, Integer> byPriority
    ) {
    }

    public record UserWorkloadResult(
            int userId,
            String userName,
            String projectId,
            int totalOpenIssues,
            int analyzedIssues,
            boolean truncated,
            HoursSummary hours,
            int overdue,
            List<ProjectWorkload> byProject,
            List<RedmineIssue> topIssues
    ) {
    }

    public record VersionChangelogResult(
            String projectId,
            int versionId,
            RedmineVersion version,
            int totalIssues,
            int analyzedIssues,
            boolean truncated,
            Map<String, List<RedmineIssue>> byTracker,
            IssueCountSummary counts,
            HoursSummary hours
    ) {
    }

    public record BlockerChainResult(
            RedmineIssue root,
            List<BlockerNode> blockedBy,
            List<BlockerNode> blocks,
            int chainDepth,
            int totalIssues
    ) {
    }

    public record StaleIssue(RedmineIssue issue, long daysSinceUpdated, boolean overdue) {
    }

    public record StaleIssuesResult(
            String projectId,
            int daysSinceUpdate,
            int limit,
            List<StaleIssue> issues,
            long oldestDaysSinceUpdated
    ) {
    }

    public record RiskCategory(String kind, List<RedmineIssue> issues) {
    }

    public record RiskScore(int items, int categories, int openIssues) {
    }

    public record ReleaseRisksResult(
            String projectId,
            int versionId,
            RedmineVersion version,
            int totalOpenIssues,
            int analyzedIssues,
            boolean truncated,
            Set<String> highPriorityNames,
            List<RiskCategory> categories,
            RiskScore score
    ) {
    }

    public record VersionScope(
            int versionId,
            String name,
            RedmineVersion version,
            int totalIssues,
            int analyzedIssues,
            int closed,
            int open,
            int completionPercent,
            boolean truncated
    ) {
    }

    public record VersionComparisonResult(
            String projectId,
            VersionScope first,
            VersionScope second,
            List<RedmineIssue> onlyInFirst,
            List<RedmineIssue> onlyInSecond,
            List<RedmineIssue> inBoth
    ) {
    }

    private IssuesBatch fetchAllIssues(String projectId, String statusId,
                                        Integer versionId, Integer assignedToId) {
        var all = new ArrayList<RedmineIssue>();
        int offset = 0;
        int total = 0;
        for (int page = 0; page < MAX_PAGES; page++) {
            var result = client.listIssues(projectId, statusId, null, assignedToId,
                    null, versionId, null, null, offset, PAGE_SIZE);
            all.addAll(result.issues());
            total = result.totalCount();
            if (offset + PAGE_SIZE >= total) break;
            offset += PAGE_SIZE;
        }
        return new IssuesBatch(all, total);
    }

    // ── Blocker helpers ────────────────────────────────────────────────

    public record BlockerNode(RedmineIssue issue, int depth) {
    }

    private RedmineIssue fetchBlockerIssue(int issueId, Set<Integer> visited, int[] fetchCount) {
        if (!visited.add(issueId) || fetchCount[0] >= MAX_BLOCKER_ISSUES) {
            return null;
        }
        fetchCount[0]++;
        return client.getIssueForTree(issueId);
    }

    private void collectBlockers(RedmineIssue issue, boolean upstream,
                                  Set<Integer> visited, int[] fetchCount,
                                  List<BlockerNode> result, int depth) {
        if (issue.relations() == null || depth >= MAX_BLOCKER_DEPTH) return;

        for (var rel : issue.relations()) {
            if (!"blocks".equals(rel.relationType())) continue;

            int targetId;
            if (upstream) {
                // Looking for issues that block this one:
                // relation: issueId blocks issueToId → if issueToId == our issue, issueId is the blocker
                if (rel.issueToId() != issue.id()) continue;
                targetId = rel.issueId();
            } else {
                // Looking for issues that this one blocks:
                // relation: issueId blocks issueToId → if issueId == our issue, issueToId is blocked
                if (rel.issueId() != issue.id()) continue;
                targetId = rel.issueToId();
            }

            RedmineIssue target = fetchBlockerIssue(targetId, visited, fetchCount);
            if (target == null) continue;

            result.add(new BlockerNode(target, depth));
            collectBlockers(target, upstream, visited, fetchCount, result, depth + 1);
        }
    }

    // ── Date/status helpers ────────────────────────────────────────────

    private boolean isOverdue(RedmineIssue issue) {
        if (issue.dueDate() == null) return false;
        try {
            return LocalDate.parse(issue.dueDate()).isBefore(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private long daysOverdue(RedmineIssue issue) {
        if (issue.dueDate() == null) return 0;
        try {
            LocalDate due = LocalDate.parse(issue.dueDate());
            return ChronoUnit.DAYS.between(due, LocalDate.now());
        } catch (DateTimeParseException e) {
            return 0;
        }
    }

    private long daysAgo(String timestamp) {
        LocalDate date = parseDate(timestamp);
        if (date == null) return 0;
        return ChronoUnit.DAYS.between(date, LocalDate.now());
    }

    private LocalDate parseDate(String timestamp) {
        if (timestamp == null) return null;
        try {
            if (timestamp.length() > 10) {
                return OffsetDateTime.parse(timestamp, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDate();
            }
            return LocalDate.parse(timestamp);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private boolean isClosedStatus(IdName status) {
        if (status == null) return false;
        String lower = status.name().toLowerCase();
        return lower.contains("closed") || lower.contains("rejected")
                || lower.contains("resolved") || lower.contains("done");
    }

    private int completionPercent(long closed, int total) {
        if (total == 0) return 0;
        return (int) Math.round(100.0 * closed / total);
    }

    // ── Generic helpers ────────────────────────────────────────────────

    private String name(IdName idName) {
        return idName != null ? idName.name() : "\u2014";
    }

    private Map<String, Integer> groupAndCount(List<RedmineIssue> issues,
                                                java.util.function.Function<RedmineIssue, String> keyFn) {
        var map = new LinkedHashMap<String, Integer>();
        for (var issue : issues) {
            map.merge(keyFn.apply(issue), 1, Integer::sum);
        }
        return map;
    }
}

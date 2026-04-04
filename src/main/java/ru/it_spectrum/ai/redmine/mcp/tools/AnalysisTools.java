package ru.it_spectrum.ai.redmine.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
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
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
public class AnalysisTools {
    private static final int MAX_PAGES = 5;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_BLOCKER_DEPTH = 10;
    private static final int MAX_BLOCKER_ISSUES = 30;

    private final RedmineClient client;

    public AnalysisTools(RedmineClient client) {
        this.client = client;
    }

    // ── getProjectSummary ──────────────────────────────────────────────

    @McpTool(description = "Get an aggregated summary of a Redmine project: issue counts by status, " +
            "tracker, priority, and assignee; overdue count; estimated vs spent hours. " +
            "Optionally filter by version/milestone. One call replaces dozens of listIssues calls.")
    public String getProjectSummary(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID to filter by (optional)", required = false) Integer versionId,
            McpSyncRequestContext context
    ) {
        var openBatch = fetchAllIssues(projectId, "open", versionId, null,
                context, "open issues for project %s".formatted(projectId));
        int closedCount = client.listIssues(projectId, "closed", null, null,
                null, versionId, null, null, 0, 1).totalCount();

        var issues = openBatch.issues;
        int openCount = openBatch.totalCount;
        int total = openCount + closedCount;

        var sb = new StringBuilder();
        sb.append("Project summary: %s\n".formatted(projectId));
        if (versionId != null) {
            sb.append("Version filter: #%d\n".formatted(versionId));
        }
        sb.append("\nOverview: %d open, %d closed (%d total)".formatted(openCount, closedCount, total));
        if (openBatch.truncated()) {
            sb.append(" [breakdown based on first %d open issues]".formatted(issues.size()));
        }
        sb.append("\n");

        // By status
        var byStatus = groupAndCount(issues, i -> name(i.status()));
        sb.append("\nBy status:\n");
        byStatus.forEach((k, v) -> sb.append("  %s: %d\n".formatted(k, v)));

        // By tracker
        var byTracker = groupAndCount(issues, i -> name(i.tracker()));
        sb.append("\nBy tracker:\n");
        byTracker.forEach((k, v) -> sb.append("  %s: %d\n".formatted(k, v)));

        // By priority
        var byPriority = groupAndCount(issues, i -> name(i.priority()));
        sb.append("\nBy priority:\n");
        byPriority.forEach((k, v) -> sb.append("  %s: %d\n".formatted(k, v)));

        // By assignee
        var byAssignee = groupAndCount(issues,
                i -> i.assignedTo() != null ? i.assignedTo().name() : "Unassigned");
        sb.append("\nBy assignee:\n");
        byAssignee.forEach((k, v) -> {
            long overdue = issues.stream()
                    .filter(i -> k.equals(i.assignedTo() != null ? i.assignedTo().name() : "Unassigned"))
                    .filter(this::isOverdue)
                    .count();
            sb.append("  %s: %d".formatted(k, v));
            if (overdue > 0) sb.append(" (%d overdue)".formatted(overdue));
            sb.append("\n");
        });

        // Overdue
        long overdueCount = issues.stream().filter(this::isOverdue).count();
        sb.append("\nOverdue: %d issues past due date\n".formatted(overdueCount));

        // Hours
        double estimated = issues.stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(RedmineIssue::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssue::spentHours).sum();
        sb.append("Hours: %.1f estimated, %.1f spent\n".formatted(estimated, spent));
        ProgressSupport.done(context, "Project summary ready");

        return sb.toString();
    }

    public String getProjectSummary(String projectId, Integer versionId) {
        return getProjectSummary(projectId, versionId, null);
    }

    // ── getUserWorkload ────────────────────────────────────────────────

    @McpTool(description = "Get workload analysis for a user: open issues grouped by project and priority, " +
            "overdue count, estimated vs spent hours, and top issues by priority. " +
            "Defaults to the current authenticated user if no userId is provided.")
    public String getUserWorkload(
            @McpToolParam(description = "User ID (optional, defaults to current user)", required = false) Integer userId,
            @McpToolParam(description = "Project identifier to limit scope (optional)", required = false) String projectId,
            McpSyncRequestContext context
    ) {
        int actualUserId;
        String userName;

        if (userId != null) {
            actualUserId = userId;
            userName = "User #" + userId;
        } else {
            var user = client.getCurrentUser();
            if (user == null) {
                return "Could not retrieve current user";
            }
            actualUserId = user.id();
            userName = user.firstname() + " " + user.lastname();
        }

        var batch = fetchAllIssues(projectId, "open", null, actualUserId,
                context, "open issues for %s".formatted(userName));
        var issues = batch.issues;

        var sb = new StringBuilder();
        sb.append("Workload for %s\n".formatted(userName));
        if (batch.truncated()) {
            sb.append("[Based on first %d of %d issues]\n".formatted(issues.size(), batch.totalCount));
        }

        double estimated = issues.stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(RedmineIssue::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssue::spentHours).sum();
        long overdueCount = issues.stream().filter(this::isOverdue).count();

        sb.append("\nTotal: %d open issues (%.1f estimated hours, %.1f spent)\n"
                .formatted(batch.totalCount, estimated, spent));
        sb.append("Overdue: %d issues\n".formatted(overdueCount));

        // By project
        var byProject = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> name(i.project()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        sb.append("\nBy project:\n");
        byProject.forEach((proj, projIssues) -> {
            double projEst = projIssues.stream()
                    .filter(i -> i.estimatedHours() != null)
                    .mapToDouble(RedmineIssue::estimatedHours).sum();
            sb.append("  %s (%d issues, %.1fh est):\n".formatted(proj, projIssues.size(), projEst));
            var byPriority = groupAndCount(projIssues, i -> name(i.priority()));
            sb.append("    %s\n".formatted(
                    byPriority.entrySet().stream()
                            .map(e -> "%s: %d".formatted(e.getKey(), e.getValue()))
                            .collect(Collectors.joining(", "))));
        });

        // Top issues by priority (highest first)
        var priorityOrder = client.getIssuePriorities().stream()
                .collect(Collectors.toMap(p -> p.name(), p -> p.id()));
        var sorted = issues.stream()
                .sorted(Comparator.<RedmineIssue, Integer>comparing(
                                i -> priorityOrder.getOrDefault(name(i.priority()), 0))
                        .reversed()
                        .thenComparing(i -> i.dueDate() != null ? i.dueDate() : "9999"))
                .limit(10)
                .toList();

        sb.append("\nTop issues by priority:\n");
        for (var issue : sorted) {
            sb.append("  #%d [%s] %s".formatted(issue.id(), name(issue.priority()), issue.subject()));
            if (issue.dueDate() != null) {
                sb.append(" \u2014 due %s".formatted(issue.dueDate()));
                if (isOverdue(issue)) sb.append(" (OVERDUE)");
            }
            sb.append("\n");
        }
        ProgressSupport.done(context, "User workload ready");

        return sb.toString();
    }

    public String getUserWorkload(Integer userId, String projectId) {
        return getUserWorkload(userId, projectId, null);
    }

    // ── getVersionChangelog ────────────────────────────────────────────

    @McpTool(description = "Get a changelog for a specific version/milestone: all issues grouped by tracker, " +
            "with status and summary. Shows both open and closed issues for the version.")
    public String getVersionChangelog(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId,
            McpSyncRequestContext context
    ) {
        // Find version metadata
        var versions = client.getProjectVersions(projectId);
        RedmineVersion version = versions.stream()
                .filter(v -> v.id() == versionId)
                .findFirst().orElse(null);

        var batch = fetchAllIssues(projectId, "*", versionId, null,
                context, "issues for version #%d".formatted(versionId));
        var issues = batch.issues;

        var sb = new StringBuilder();
        sb.append("Changelog for %s (project: %s)\n".formatted(
                version != null ? version.name() : "version #" + versionId, projectId));
        if (version != null) {
            sb.append("Status: %s".formatted(version.status()));
            if (version.dueDate() != null) sb.append(" | Due: %s".formatted(version.dueDate()));
            sb.append("\n");
        }
        if (batch.truncated()) {
            sb.append("[Showing first %d of %d issues]\n".formatted(issues.size(), batch.totalCount));
        }

        // Group by tracker
        var byTracker = issues.stream()
                .collect(Collectors.groupingBy(
                        i -> name(i.tracker()),
                        LinkedHashMap::new,
                        Collectors.toList()));

        sb.append("\n");
        byTracker.forEach((tracker, trackerIssues) -> {
            sb.append("%s (%d):\n".formatted(tracker, trackerIssues.size()));
            for (var issue : trackerIssues) {
                sb.append("  - #%d %s [%s]".formatted(issue.id(), issue.subject(), name(issue.status())));
                if (issue.assignedTo() != null) sb.append(" (%s)".formatted(issue.assignedTo().name()));
                sb.append("\n");
            }
            sb.append("\n");
        });

        long closed = issues.stream().filter(i -> isClosedStatus(i.status())).count();
        long open = issues.size() - closed;
        double estimated = issues.stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(RedmineIssue::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssue::spentHours).sum();

        sb.append("Summary: %d issues (%d closed, %d open)".formatted(issues.size(), closed, open));
        if (estimated > 0 || spent > 0) {
            sb.append(", %.1fh estimated, %.1fh spent".formatted(estimated, spent));
        }
        sb.append("\n");
        ProgressSupport.done(context, "Version changelog ready");

        return sb.toString();
    }

    public String getVersionChangelog(String projectId, int versionId) {
        return getVersionChangelog(projectId, versionId, null);
    }

    // ── getBlockerChain ────────────────────────────────────────────────

    @McpTool(description = "Trace the full chain of blocking dependencies for an issue. " +
            "Shows what blocks this issue (must be resolved first) and what this issue blocks. " +
            "Follows blocks/blocked_by relations recursively to reveal the critical path.")
    public String getBlockerChain(
            @McpToolParam(description = "Issue ID number") int issueId,
            McpSyncRequestContext context
    ) {
        var visited = new HashSet<Integer>();
        var fetchCount = new int[]{0};
        ProgressSupport.stage(context, "Loading blocker chain root");

        RedmineIssue root = fetchBlockerIssue(issueId, visited, fetchCount, context, "Loaded blocker node");
        if (root == null) {
            return "Issue #%d not found".formatted(issueId);
        }

        var sb = new StringBuilder();
        sb.append("Blocker chain for #%d: %s\n".formatted(root.id(), root.subject()));

        // Collect "blocked by" (upstream: what must be resolved first)
        var blockedBy = new ArrayList<BlockerNode>();
        ProgressSupport.stage(context, "Traversing upstream blockers");
        collectBlockers(root, true, visited, fetchCount, blockedBy, 0, context);

        // Reset visited for downstream (keep root)
        visited.clear();
        visited.add(issueId);
        fetchCount[0] = 1;

        // Collect "blocks" (downstream: what waits on this issue)
        var blocks = new ArrayList<BlockerNode>();
        ProgressSupport.stage(context, "Traversing downstream blockers");
        collectBlockers(root, false, visited, fetchCount, blocks, 0, context);

        if (blockedBy.isEmpty() && blocks.isEmpty()) {
            sb.append("\nNo blocking relations found.\n");
            return sb.toString();
        }

        if (!blockedBy.isEmpty()) {
            sb.append("\nBlocked by (must be resolved first):\n");
            for (var node : blockedBy) {
                String indent = "  " + "  ".repeat(node.depth);
                sb.append(indent);
                if (node.depth > 0) sb.append("\u2514\u2500 ");
                appendBlockerNode(sb, node.issue);
            }
        }

        if (!blocks.isEmpty()) {
            sb.append("\nBlocks (waiting on #%d):\n".formatted(issueId));
            for (var node : blocks) {
                String indent = "  " + "  ".repeat(node.depth);
                sb.append(indent);
                if (node.depth > 0) sb.append("\u2514\u2500 ");
                appendBlockerNode(sb, node.issue);
            }
        }

        // Critical path
        int upstreamDepth = blockedBy.stream().mapToInt(n -> n.depth).max().orElse(0);
        int downstreamDepth = blocks.stream().mapToInt(n -> n.depth).max().orElse(0);
        int totalDepth = upstreamDepth + 1 + downstreamDepth;
        int totalIssues = 1 + blockedBy.size() + blocks.size();

        sb.append("\nChain depth: %d, Total issues: %d\n".formatted(totalDepth, totalIssues));
        ProgressSupport.done(context, "Blocker chain ready");

        return sb.toString();
    }

    public String getBlockerChain(int issueId) {
        return getBlockerChain(issueId, null);
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

        var sb = new StringBuilder();
        sb.append("Stale issues in project %s (not updated for %d+ days)\n\n".formatted(projectId, minDays));

        if (stale.isEmpty()) {
            sb.append("No stale issues found.\n");
            return sb.toString();
        }

        for (var issue : stale) {
            long daysAgo = daysAgo(issue.updatedOn());
            sb.append("#%-5d %s [%s] %s \u2014 last updated %s (%d days ago)\n".formatted(
                    issue.id(), issue.subject(), name(issue.tracker()), name(issue.priority()),
                    issue.updatedOn() != null ? issue.updatedOn().substring(0, 10) : "?", daysAgo));
            sb.append("       ");
            if (issue.assignedTo() != null) {
                sb.append("Assigned: %s".formatted(issue.assignedTo().name()));
            } else {
                sb.append("Unassigned");
            }
            if (issue.dueDate() != null) {
                sb.append(" | Due: %s".formatted(issue.dueDate()));
                if (isOverdue(issue)) sb.append(" (OVERDUE)");
            }
            sb.append("\n\n");
        }

        long oldest = stale.stream().mapToLong(i -> daysAgo(i.updatedOn())).max().orElse(0);
        sb.append("Found %d stale issues (oldest: %d days)\n".formatted(stale.size(), oldest));

        return sb.toString();
    }

    // ── getReleaseRisks ────────────────────────────────────────────────

    @McpTool(description = "Assess release risks for a version/milestone: identifies open blockers, " +
            "overdue issues, high-priority unresolved issues, and unassigned tasks. " +
            "Provides a risk score summary.")
    public String getReleaseRisks(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "Version/milestone ID") int versionId,
            McpSyncRequestContext context
    ) {
        // Version metadata
        var versions = client.getProjectVersions(projectId);
        RedmineVersion version = versions.stream()
                .filter(v -> v.id() == versionId)
                .findFirst().orElse(null);

        // Open issues for this version
        var batch = fetchAllIssues(projectId, "open", versionId, null,
                context, "open issues for version #%d".formatted(versionId));
        var issues = batch.issues;

        var sb = new StringBuilder();
        sb.append("Release risks for %s (project: %s)\n".formatted(
                version != null ? version.name() : "version #" + versionId, projectId));
        if (version != null && version.dueDate() != null) {
            sb.append("Version due date: %s\n".formatted(version.dueDate()));
        }
        sb.append("Open issues: %d\n".formatted(batch.totalCount));
        if (batch.truncated()) {
            sb.append("[Analysis based on first %d issues]\n".formatted(issues.size()));
        }

        int riskItems = 0;
        int riskCategories = 0;

        // BLOCKERS — issues with blocking relations
        var blockers = issues.stream()
                .filter(i -> i.relations() != null && i.relations().stream()
                        .anyMatch(r -> "blocks".equals(r.relationType()) && r.issueId() == i.id()))
                .toList();
        if (!blockers.isEmpty()) {
            riskCategories++;
            sb.append("\nBLOCKERS (open issues with blocking relations): %d\n".formatted(blockers.size()));
            for (var issue : blockers) {
                var blocked = issue.relations().stream()
                        .filter(r -> "blocks".equals(r.relationType()) && r.issueId() == issue.id())
                        .map(r -> "#" + r.issueToId())
                        .collect(Collectors.joining(", "));
                sb.append("  #%d %s [%s] blocks %s\n".formatted(
                        issue.id(), issue.subject(), name(issue.status()), blocked));
                riskItems++;
            }
        }

        // OVERDUE
        var overdue = issues.stream().filter(this::isOverdue).toList();
        if (!overdue.isEmpty()) {
            riskCategories++;
            sb.append("\nOVERDUE (past due date): %d\n".formatted(overdue.size()));
            for (var issue : overdue) {
                long days = daysOverdue(issue);
                sb.append("  #%d %s [%s] due %s (%d days overdue)\n".formatted(
                        issue.id(), issue.subject(), name(issue.priority()),
                        issue.dueDate(), days));
                riskItems++;
            }
        }

        // HIGH PRIORITY — fetch priorities to identify top ones
        var priorities = client.getIssuePriorities();
        // Consider the top third of priorities as "high"
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
        if (!highPriority.isEmpty()) {
            riskCategories++;
            sb.append("\nHIGH PRIORITY (open): %d\n".formatted(highPriority.size()));
            for (var issue : highPriority) {
                sb.append("  #%d %s [%s]".formatted(issue.id(), issue.subject(), name(issue.priority())));
                if (issue.assignedTo() != null) sb.append(" assigned %s".formatted(issue.assignedTo().name()));
                sb.append("\n");
                riskItems++;
            }
        }

        // UNASSIGNED
        var unassigned = issues.stream().filter(i -> i.assignedTo() == null).toList();
        if (!unassigned.isEmpty()) {
            riskCategories++;
            sb.append("\nUNASSIGNED: %d\n".formatted(unassigned.size()));
            for (var issue : unassigned) {
                sb.append("  #%d %s [%s]\n".formatted(issue.id(), issue.subject(), name(issue.priority())));
                riskItems++;
            }
        }

        if (riskItems == 0) {
            sb.append("\nNo risks identified.\n");
        } else {
            sb.append("\nRisk score: %d risk items across %d categories in %d open issues\n"
                    .formatted(riskItems, riskCategories, issues.size()));
        }
        ProgressSupport.done(context, "Release risk analysis ready");

        return sb.toString();
    }

    public String getReleaseRisks(String projectId, int versionId) {
        return getReleaseRisks(projectId, versionId, null);
    }

    // ── compareVersions ────────────────────────────────────────────────

    @McpTool(description = "Compare two versions/milestones: shows issues unique to each version, " +
            "shared issues, and status completion percentages. " +
            "Useful for understanding scope changes between releases.")
    public String compareVersions(
            @McpToolParam(description = "Project identifier") String projectId,
            @McpToolParam(description = "First version/milestone ID") int versionId1,
            @McpToolParam(description = "Second version/milestone ID") int versionId2,
            McpSyncRequestContext context
    ) {
        var versions = client.getProjectVersions(projectId);
        var v1Meta = versions.stream().filter(v -> v.id() == versionId1).findFirst().orElse(null);
        var v2Meta = versions.stream().filter(v -> v.id() == versionId2).findFirst().orElse(null);

        String v1Name = v1Meta != null ? v1Meta.name() : "#" + versionId1;
        String v2Name = v2Meta != null ? v2Meta.name() : "#" + versionId2;

        var batch1 = fetchAllIssues(projectId, "*", versionId1, null,
                context, "issues for %s".formatted(v1Name));
        var batch2 = fetchAllIssues(projectId, "*", versionId2, null,
                context, "issues for %s".formatted(v2Name));

        var ids1 = batch1.issues.stream().map(RedmineIssue::id).collect(Collectors.toSet());
        var ids2 = batch2.issues.stream().map(RedmineIssue::id).collect(Collectors.toSet());

        var onlyIn1 = batch1.issues.stream().filter(i -> !ids2.contains(i.id())).toList();
        var onlyIn2 = batch2.issues.stream().filter(i -> !ids1.contains(i.id())).toList();
        var inBoth = batch1.issues.stream().filter(i -> ids2.contains(i.id())).toList();

        long closed1 = batch1.issues.stream().filter(i -> isClosedStatus(i.status())).count();
        long closed2 = batch2.issues.stream().filter(i -> isClosedStatus(i.status())).count();

        var sb = new StringBuilder();
        sb.append("Comparison: %s \u2192 %s (project: %s)\n\n".formatted(v1Name, v2Name, projectId));

        sb.append("%s: %d issues (%d closed, %d open)\n".formatted(
                v1Name, batch1.totalCount, closed1, batch1.issues.size() - closed1));
        sb.append("%s: %d issues (%d closed, %d open)\n".formatted(
                v2Name, batch2.totalCount, closed2, batch2.issues.size() - closed2));

        sb.append("\nOnly in %s (%d issues):\n".formatted(v1Name, onlyIn1.size()));
        for (var issue : onlyIn1) {
            sb.append("  #%d %s [%s] %s\n".formatted(
                    issue.id(), issue.subject(), name(issue.tracker()), name(issue.status())));
        }

        sb.append("\nOnly in %s (%d issues):\n".formatted(v2Name, onlyIn2.size()));
        for (var issue : onlyIn2) {
            sb.append("  #%d %s [%s] %s\n".formatted(
                    issue.id(), issue.subject(), name(issue.tracker()), name(issue.status())));
        }

        if (!inBoth.isEmpty()) {
            sb.append("\nIn both (%d issues):\n".formatted(inBoth.size()));
            for (var issue : inBoth) {
                sb.append("  #%d %s [%s] %s\n".formatted(
                        issue.id(), issue.subject(), name(issue.tracker()), name(issue.status())));
            }
        }

        sb.append("\nCompletion:\n");
        sb.append("  %s: %s\n".formatted(v1Name, percentClosed(closed1, batch1.issues.size())));
        sb.append("  %s: %s\n".formatted(v2Name, percentClosed(closed2, batch2.issues.size())));
        ProgressSupport.done(context, "Version comparison ready");

        return sb.toString();
    }

    public String compareVersions(String projectId, int versionId1, int versionId2) {
        return compareVersions(projectId, versionId1, versionId2, null);
    }

    // ── Shared helpers ─────────────────────────────────────────────────

    private record IssuesBatch(List<RedmineIssue> issues, int totalCount) {
        boolean truncated() {
            return issues.size() < totalCount;
        }
    }

    private IssuesBatch fetchAllIssues(String projectId, String statusId,
                                        Integer versionId, Integer assignedToId) {
        return fetchAllIssues(projectId, statusId, versionId, assignedToId, null, "issues");
    }

    private IssuesBatch fetchAllIssues(String projectId, String statusId,
                                        Integer versionId, Integer assignedToId,
                                        McpSyncRequestContext context, String progressLabel) {
        var all = new ArrayList<RedmineIssue>();
        int offset = 0;
        int total = 0;
        ProgressSupport.stage(context, "Loading %s".formatted(progressLabel));
        for (int page = 0; page < MAX_PAGES; page++) {
            var result = client.listIssues(projectId, statusId, null, assignedToId,
                    null, versionId, null, null, offset, PAGE_SIZE);
            all.addAll(result.issues());
            total = result.totalCount();
            int loaded = Math.min(all.size(), total);
            if (total > 0) {
                ProgressSupport.report(context, loaded, total,
                        "Loaded %d/%d %s".formatted(loaded, total, progressLabel));
            } else {
                ProgressSupport.stage(context, "No %s found".formatted(progressLabel));
            }
            if (offset + PAGE_SIZE >= total) break;
            offset += PAGE_SIZE;
        }
        return new IssuesBatch(all, total);
    }

    // ── Blocker helpers ────────────────────────────────────────────────

    private record BlockerNode(RedmineIssue issue, int depth) {
    }

    private RedmineIssue fetchBlockerIssue(int issueId, Set<Integer> visited, int[] fetchCount) {
        return fetchBlockerIssue(issueId, visited, fetchCount, null, "Loaded blocker issue");
    }

    private RedmineIssue fetchBlockerIssue(int issueId, Set<Integer> visited, int[] fetchCount,
                                           McpSyncRequestContext context, String messagePrefix) {
        if (!visited.add(issueId) || fetchCount[0] >= MAX_BLOCKER_ISSUES) {
            return null;
        }
        fetchCount[0]++;
        ProgressSupport.report(context, fetchCount[0], MAX_BLOCKER_ISSUES,
                "%s %d/%d".formatted(messagePrefix, fetchCount[0], MAX_BLOCKER_ISSUES));
        return client.getIssueForTree(issueId);
    }

    private void collectBlockers(RedmineIssue issue, boolean upstream,
                                  Set<Integer> visited, int[] fetchCount,
                                  List<BlockerNode> result, int depth,
                                  McpSyncRequestContext context) {
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

            RedmineIssue target = fetchBlockerIssue(targetId, visited, fetchCount, context, "Loaded blocker node");
            if (target == null) continue;

            result.add(new BlockerNode(target, depth));
            collectBlockers(target, upstream, visited, fetchCount, result, depth + 1, context);
        }
    }

    private void appendBlockerNode(StringBuilder sb, RedmineIssue issue) {
        sb.append("#%d %s [%s]".formatted(issue.id(), issue.subject(), name(issue.status())));
        if (issue.assignedTo() != null) {
            sb.append(" (%s)".formatted(issue.assignedTo().name()));
        }
        sb.append("\n");
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

    private String percentClosed(long closed, int total) {
        if (total == 0) return "no issues";
        return "%d%% closed (%d/%d)".formatted(Math.round(100.0 * closed / total), closed, total);
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

package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineVersion;
import ru.it_spectrum.ai.redmine.mcp.model.AssigneeSummary;
import ru.it_spectrum.ai.redmine.mcp.model.BlockerChainResult;
import ru.it_spectrum.ai.redmine.mcp.model.BlockerNode;
import ru.it_spectrum.ai.redmine.mcp.model.HoursSummary;
import ru.it_spectrum.ai.redmine.mcp.model.IssueCountSummary;
import ru.it_spectrum.ai.redmine.mcp.model.ProjectSummaryResult;
import ru.it_spectrum.ai.redmine.mcp.model.ProjectWorkload;
import ru.it_spectrum.ai.redmine.mcp.model.ReleaseRisksResult;
import ru.it_spectrum.ai.redmine.mcp.model.RiskCategory;
import ru.it_spectrum.ai.redmine.mcp.model.RiskScore;
import ru.it_spectrum.ai.redmine.mcp.model.StaleIssue;
import ru.it_spectrum.ai.redmine.mcp.model.StaleIssuesResult;
import ru.it_spectrum.ai.redmine.mcp.model.UserWorkloadResult;
import ru.it_spectrum.ai.redmine.mcp.model.VersionChangelogResult;
import ru.it_spectrum.ai.redmine.mcp.model.VersionComparisonResult;
import ru.it_spectrum.ai.redmine.mcp.model.VersionScope;

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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AnalysisService {
    private static final int MAX_PAGES = 5;
    private static final int PAGE_SIZE = 100;
    private static final int MAX_BLOCKER_DEPTH = 10;
    private static final int MAX_BLOCKER_ISSUES = 30;

    private final RedmineClient client;

    public AnalysisService(RedmineClient client) {
        this.client = client;
    }

    public ProjectSummaryResult getProjectSummary(String projectId, Integer versionId) {
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
                .mapToDouble(RedmineIssueSummary::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssueSummary::spentHours).sum();

        return new ProjectSummaryResult(
                projectId, versionId,
                new IssueCountSummary(openCount, closedCount, total),
                openBatch.truncated(),
                issues.size(),
                byStatus, byTracker, byPriority, assignees,
                (int) overdueCount,
                new HoursSummary(estimated, spent)
        );
    }

    public Optional<UserWorkloadResult> getUserWorkload(Integer userId, String projectId) {
        int actualUserId;
        String userName;

        if (userId != null) {
            actualUserId = userId;
            userName = "User #" + userId;
        } else {
            var user = client.getCurrentUser();
            if (user == null) {
                return Optional.empty();
            }
            actualUserId = user.id();
            userName = user.firstname() + " " + user.lastname();
        }

        var batch = fetchAllIssues(projectId, "open", null, actualUserId);
        var issues = batch.issues;

        double estimated = issues.stream()
                .filter(i -> i.estimatedHours() != null)
                .mapToDouble(RedmineIssueSummary::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssueSummary::spentHours).sum();
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
                                .mapToDouble(RedmineIssueSummary::estimatedHours).sum(),
                        groupAndCount(e.getValue(), i -> name(i.priority()))))
                .toList();

        var priorityOrder = client.getIssuePriorities().stream()
                .collect(Collectors.toMap(p -> p.name(), p -> p.id()));
        var sorted = issues.stream()
                .sorted(Comparator.<RedmineIssueSummary, Integer>comparing(
                                i -> priorityOrder.getOrDefault(name(i.priority()), 0))
                        .reversed()
                        .thenComparing(i -> i.dueDate() != null ? i.dueDate() : "9999"))
                .limit(10)
                .toList();

        return Optional.of(new UserWorkloadResult(
                actualUserId, userName, projectId,
                batch.totalCount, issues.size(), batch.truncated(),
                new HoursSummary(estimated, spent),
                (int) overdueCount,
                projects,
                sorted
        ));
    }

    public VersionChangelogResult getVersionChangelog(String projectId, int versionId) {
        var version = findVersion(projectId, versionId);
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
                .mapToDouble(RedmineIssueSummary::estimatedHours).sum();
        double spent = issues.stream()
                .filter(i -> i.spentHours() != null)
                .mapToDouble(RedmineIssueSummary::spentHours).sum();

        return new VersionChangelogResult(
                projectId, versionId, version,
                batch.totalCount, issues.size(), batch.truncated(),
                byTracker,
                new IssueCountSummary((int) open, (int) closed, issues.size()),
                new HoursSummary(estimated, spent)
        );
    }

    public BlockerChainResult getBlockerChain(int issueId) {
        var visited = new HashSet<Integer>();
        var fetchCount = new int[]{0};

        RedmineIssue root = fetchBlockerIssue(issueId, visited, fetchCount);
        if (root == null) {
            throw new ResourceNotFoundException("issue", "#" + issueId);
        }

        var blockedBy = new ArrayList<BlockerNode>();
        collectBlockers(root, true, visited, fetchCount, blockedBy, 0);

        visited.clear();
        visited.add(issueId);
        fetchCount[0] = 1;

        var blocks = new ArrayList<BlockerNode>();
        collectBlockers(root, false, visited, fetchCount, blocks, 0);

        int upstreamDepth = blockedBy.stream().mapToInt(BlockerNode::depth).max().orElse(0);
        int downstreamDepth = blocks.stream().mapToInt(BlockerNode::depth).max().orElse(0);
        int totalDepth = upstreamDepth + 1 + downstreamDepth;
        int totalIssues = 1 + blockedBy.size() + blocks.size();

        return new BlockerChainResult(root, blockedBy, blocks, totalDepth, totalIssues);
    }

    public StaleIssuesResult getStaleIssues(String projectId, Integer daysSinceUpdate, Integer limit) {
        int minDays = daysSinceUpdate != null ? daysSinceUpdate : 30;
        int actualLimit = limit != null ? Math.min(Math.max(limit, 1), 100) : 25;

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
        return new StaleIssuesResult(projectId, minDays, actualLimit, staleItems, oldest);
    }

    public ReleaseRisksResult getReleaseRisks(String projectId, int versionId) {
        var version = findVersion(projectId, versionId);
        var batch = fetchAllIssues(projectId, "open", versionId, null);
        var issues = batch.issues;

        var blockers = issues.stream()
                .filter(this::hasBlockingRelation)
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

        return new ReleaseRisksResult(
                projectId, versionId, version,
                batch.totalCount, issues.size(), batch.truncated(),
                highPriorityNames,
                categories,
                new RiskScore(riskItems, categories.size(), issues.size())
        );
    }

    public VersionComparisonResult compareVersions(String projectId, int versionId1, int versionId2) {
        var versions = client.getProjectVersions(projectId);
        var v1Meta = versions.stream().filter(v -> v.id() == versionId1).findFirst().orElse(null);
        var v2Meta = versions.stream().filter(v -> v.id() == versionId2).findFirst().orElse(null);

        String v1Name = v1Meta != null ? v1Meta.name() : "#" + versionId1;
        String v2Name = v2Meta != null ? v2Meta.name() : "#" + versionId2;

        var batch1 = fetchAllIssues(projectId, "*", versionId1, null);
        var batch2 = fetchAllIssues(projectId, "*", versionId2, null);

        var ids1 = batch1.issues.stream().map(RedmineIssueSummary::id).collect(Collectors.toSet());
        var ids2 = batch2.issues.stream().map(RedmineIssueSummary::id).collect(Collectors.toSet());

        var onlyIn1 = batch1.issues.stream().filter(i -> !ids2.contains(i.id())).toList();
        var onlyIn2 = batch2.issues.stream().filter(i -> !ids1.contains(i.id())).toList();
        var inBoth = batch1.issues.stream().filter(i -> ids2.contains(i.id())).toList();

        long closed1 = batch1.issues.stream().filter(i -> isClosedStatus(i.status())).count();
        long closed2 = batch2.issues.stream().filter(i -> isClosedStatus(i.status())).count();

        return new VersionComparisonResult(
                projectId,
                new VersionScope(versionId1, v1Name, v1Meta, batch1.totalCount,
                        batch1.issues.size(), (int) closed1, batch1.issues.size() - (int) closed1,
                        completionPercent(closed1, batch1.issues.size()), batch1.truncated()),
                new VersionScope(versionId2, v2Name, v2Meta, batch2.totalCount,
                        batch2.issues.size(), (int) closed2, batch2.issues.size() - (int) closed2,
                        completionPercent(closed2, batch2.issues.size()), batch2.truncated()),
                onlyIn1, onlyIn2, inBoth
        );
    }

    private RedmineVersion findVersion(String projectId, int versionId) {
        return client.getProjectVersions(projectId).stream()
                .filter(v -> v.id() == versionId)
                .findFirst()
                .orElse(null);
    }

    private record IssuesBatch(List<RedmineIssueSummary> issues, int totalCount) {
        boolean truncated() {
            return issues.size() < totalCount;
        }
    }

    private IssuesBatch fetchAllIssues(String projectId, String statusId,
                                       Integer versionId, Integer assignedToId) {
        var all = new ArrayList<RedmineIssueSummary>();
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

    private RedmineIssue fetchBlockerIssue(int issueId, Set<Integer> visited, int[] fetchCount) {
        if (!visited.add(issueId) || fetchCount[0] >= MAX_BLOCKER_ISSUES) {
            return null;
        }
        fetchCount[0]++;
        return client.getIssue(issueId);
    }

    private void collectBlockers(RedmineIssue issue, boolean upstream,
                                 Set<Integer> visited, int[] fetchCount,
                                 List<BlockerNode> result, int depth) {
        if (issue.relations() == null || depth >= MAX_BLOCKER_DEPTH) return;

        for (var rel : issue.relations()) {
            if (!"blocks".equals(rel.relationType())) continue;

            int targetId;
            if (upstream) {
                if (rel.issueToId() != issue.id()) continue;
                targetId = rel.issueId();
            } else {
                if (rel.issueId() != issue.id()) continue;
                targetId = rel.issueToId();
            }

            RedmineIssue target = fetchBlockerIssue(targetId, visited, fetchCount);
            if (target == null) continue;

            result.add(new BlockerNode(target, depth));
            collectBlockers(target, upstream, visited, fetchCount, result, depth + 1);
        }
    }

    private boolean hasBlockingRelation(RedmineIssueSummary summary) {
        RedmineIssue issue = client.getIssue(summary.id());
        return issue != null && issue.relations() != null && issue.relations().stream()
                .anyMatch(r -> "blocks".equals(r.relationType()) && r.issueId() == issue.id());
    }

    private boolean isOverdue(RedmineIssueSummary issue) {
        if (issue.dueDate() == null) return false;
        try {
            return LocalDate.parse(issue.dueDate()).isBefore(LocalDate.now());
        } catch (DateTimeParseException e) {
            return false;
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

    private String name(IdName idName) {
        return idName != null ? idName.name() : "—";
    }

    private Map<String, Integer> groupAndCount(List<RedmineIssueSummary> issues,
                                               java.util.function.Function<RedmineIssueSummary, String> keyFn) {
        var map = new LinkedHashMap<String, Integer>();
        for (var issue : issues) {
            map.merge(keyFn.apply(issue), 1, Integer::sum);
        }
        return map;
    }
}

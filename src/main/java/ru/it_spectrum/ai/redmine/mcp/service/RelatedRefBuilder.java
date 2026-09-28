package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.ContextRole;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.api.RelatedRef;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves parent, siblings, children and relation targets for a given issue and exposes them as
 * lightweight {@link RelatedRef}s for the Issue payload.
 *
 * <p>A related ref only needs id, subject, tracker and status, so this deliberately avoids full
 * issue reads: the parent is read with {@code include=children} (to enumerate siblings) and every
 * other related issue comes from one batched {@code /issues.json?issue_id=...} request. Related
 * issues are not snapshotted; {@code getIssue} on one of them snapshots it when it is needed.
 */
@Service
public class RelatedRefBuilder {

    private final RedmineClient client;
    private final RedmineMcpProperties properties;

    public RelatedRefBuilder(RedmineClient client, RedmineMcpProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public Result fetchRelated(RedmineIssue mainIssue) {
        int issueId = mainIssue.id();
        var candidates = new ArrayList<Candidate>();

        RedmineIssue parent = mainIssue.parent() != null
                ? client.getIssueWithChildren(mainIssue.parent().id())
                : null;
        if (parent != null) {
            candidates.add(new Candidate(parent.id(), new ContextRole(
                    ContextRole.Kind.PARENT, null, null, issueId, parent.id(), null)));
        }
        boolean siblingsTruncated = collectSiblings(parent, issueId, candidates);
        boolean childrenTruncated = collectChildren(mainIssue, issueId, candidates);
        boolean relatedTruncated = collectRelations(mainIssue, issueId, candidates);

        var byId = resolve(parent, candidates);
        var entries = new LinkedHashMap<Integer, MutableEntry>();
        for (var candidate : candidates) {
            var target = byId.get(candidate.issueId());
            if (target != null) {
                entries.computeIfAbsent(target.id(), ignored -> new MutableEntry(target))
                        .roles.add(candidate.role());
            }
        }

        var fetched = entries.values().stream()
                .map(MutableEntry::toFetched)
                .toList();
        return new Result(fetched, siblingsTruncated, childrenTruncated, relatedTruncated);
    }

    private boolean collectSiblings(RedmineIssue parent, int issueId, List<Candidate> candidates) {
        if (parent == null || parent.children() == null) {
            return false;
        }
        int maxSiblings = properties.related().maxSiblings();
        long siblingsTotal = parent.children().stream()
                .filter(child -> child.id() != issueId)
                .count();
        int attempts = 0;
        for (var child : parent.children()) {
            if (child.id() == issueId) continue;
            if (attempts >= maxSiblings) break;
            attempts++;
            candidates.add(new Candidate(child.id(), new ContextRole(
                    ContextRole.Kind.SIBLING, null, null, parent.id(), child.id(), null)));
        }
        return siblingsTotal > maxSiblings;
    }

    private boolean collectChildren(RedmineIssue mainIssue, int issueId, List<Candidate> candidates) {
        if (mainIssue.children() == null) {
            return false;
        }
        int maxChildren = properties.related().maxChildren();
        int attempts = 0;
        for (var child : mainIssue.children()) {
            if (attempts >= maxChildren) break;
            attempts++;
            candidates.add(new Candidate(child.id(), new ContextRole(
                    ContextRole.Kind.CHILD, null, null, issueId, child.id(), null)));
        }
        return mainIssue.children().size() > maxChildren;
    }

    private boolean collectRelations(RedmineIssue mainIssue, int issueId, List<Candidate> candidates) {
        if (mainIssue.relations() == null || mainIssue.relations().isEmpty()) {
            return false;
        }
        int maxRelated = properties.related().maxRelated();
        int relCount = 0;
        for (var rel : mainIssue.relations()) {
            if (relCount >= maxRelated) break;
            relCount++;
            int relatedId = rel.issueId() == issueId ? rel.issueToId() : rel.issueId();
            candidates.add(new Candidate(relatedId, new ContextRole(
                    ContextRole.Kind.RELATED, formatRelationType(rel, issueId), rel.id(), issueId, relatedId,
                    rel.delay())));
        }
        return mainIssue.relations().size() > maxRelated;
    }

    /** One batched summary read for every candidate except the parent, which is already loaded. */
    private Map<Integer, Target> resolve(RedmineIssue parent, List<Candidate> candidates) {
        var ids = candidates.stream()
                .map(Candidate::issueId)
                .filter(id -> parent == null || id != parent.id())
                .distinct()
                .toList();
        Map<Integer, Target> byId = ids.isEmpty()
                ? new LinkedHashMap<>()
                : client.getIssueSummariesByIds(ids).stream()
                        .map(Target::from)
                        .collect(Collectors.toMap(Target::id, Function.identity(), (a, b) -> a, LinkedHashMap::new));
        if (parent != null) {
            byId.put(parent.id(), Target.from(parent));
        }
        return byId;
    }

    private static String formatRelationType(RedmineIssue.Relation rel, int currentIssueId) {
        String type = rel.relationType();
        if (rel.issueId() == currentIssueId) {
            return type;
        }
        return switch (type) {
            case "blocks" -> "blocked_by";
            case "precedes" -> "follows";
            case "duplicates" -> "duplicated_by";
            case "copied_to" -> "copied_from";
            default -> type;
        };
    }

    private record Candidate(int issueId, ContextRole role) {
    }

    /** The fields a {@link RelatedRef} needs, from either a full issue or an issue summary. */
    private record Target(int id, String subject, IdName tracker, IdName status) {
        static Target from(RedmineIssue issue) {
            return new Target(issue.id(), issue.subject(), issue.tracker(), issue.status());
        }

        static Target from(RedmineIssueSummary summary) {
            return new Target(summary.id(), summary.subject(), summary.tracker(), summary.status());
        }
    }

    public record Fetched(int id, String subject, IdName tracker, IdName status, List<ContextRole> roles) {
        public RelatedRef toRef() {
            return new RelatedRef(
                    id,
                    subject,
                    Ref.from(tracker),
                    Ref.from(status),
                    roles
            );
        }
    }

    public record Result(
            List<Fetched> entries,
            boolean siblingsTruncated,
            boolean childrenTruncated,
            boolean relatedTruncated
    ) {
        public List<RelatedRef> toRefs() {
            if (entries.isEmpty()) {
                return null;
            }
            return entries.stream().map(Fetched::toRef).toList();
        }
    }

    private static final class MutableEntry {
        private final Target target;
        private final List<ContextRole> roles = new ArrayList<>();

        private MutableEntry(Target target) {
            this.target = target;
        }

        private Fetched toFetched() {
            return new Fetched(target.id(), target.subject(), target.tracker(), target.status(), List.copyOf(roles));
        }
    }
}

package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.ContextRole;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.api.RelatedRef;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fetches parent, siblings, children and relation targets for a given issue,
 * snapshots each fetched RedmineIssue, and exposes the result either as
 * lightweight {@link RelatedRef}s (for the basic Issue payload) or as
 * role-tagged RedmineIssue entries (for IssueFullContext, which still needs
 * the full payloads).
 */
@Service
public class RelatedRefBuilder {

    private final RedmineClient client;
    private final AttachmentService attachmentService;
    private final RedmineMcpProperties properties;

    public RelatedRefBuilder(RedmineClient client,
                             AttachmentService attachmentService,
                             RedmineMcpProperties properties) {
        this.client = client;
        this.attachmentService = attachmentService;
        this.properties = properties;
    }

    public Result fetchRelated(RedmineIssue mainIssue) {
        var entries = new LinkedHashMap<Integer, MutableEntry>();
        int issueId = mainIssue.id();

        RedmineIssue parent = fetchParent(mainIssue, issueId, entries);
        boolean siblingsTruncated = fetchSiblings(parent, issueId, entries);
        boolean childrenTruncated = fetchChildren(mainIssue, issueId, entries);
        boolean relatedTruncated = fetchRelations(mainIssue, issueId, entries);

        var fetched = entries.values().stream()
                .map(MutableEntry::toFetched)
                .toList();
        return new Result(fetched, siblingsTruncated, childrenTruncated, relatedTruncated);
    }

    private RedmineIssue fetchParent(RedmineIssue mainIssue, int issueId,
                                     Map<Integer, MutableEntry> entries) {
        if (mainIssue.parent() == null) {
            return null;
        }
        var parent = client.getIssue(mainIssue.parent().id());
        if (parent == null) {
            return null;
        }
        attachmentService.snapshotIssue(parent);
        addRole(entries, parent, new ContextRole(
                ContextRole.Kind.PARENT, null, null, issueId, parent.id(), null));
        return parent;
    }

    private boolean fetchSiblings(RedmineIssue parent, int issueId,
                                  Map<Integer, MutableEntry> entries) {
        if (parent == null || parent.children() == null) {
            return false;
        }
        int maxSiblings = properties.fullContext().maxSiblings();
        long siblingsTotal = parent.children().stream()
                .filter(child -> child.id() != issueId)
                .count();
        boolean truncated = siblingsTotal > maxSiblings;
        int attempts = 0;
        for (var child : parent.children()) {
            if (child.id() == issueId) continue;
            if (attempts >= maxSiblings) break;
            attempts++;
            var sibling = client.getIssue(child.id());
            if (sibling != null) {
                attachmentService.snapshotIssue(sibling);
                addRole(entries, sibling, new ContextRole(
                        ContextRole.Kind.SIBLING, null, null, parent.id(), sibling.id(), null));
            }
        }
        return truncated;
    }

    private boolean fetchChildren(RedmineIssue mainIssue, int issueId,
                                  Map<Integer, MutableEntry> entries) {
        if (mainIssue.children() == null) {
            return false;
        }
        int maxChildren = properties.fullContext().maxChildren();
        boolean truncated = mainIssue.children().size() > maxChildren;
        int attempts = 0;
        for (var child : mainIssue.children()) {
            if (attempts >= maxChildren) break;
            attempts++;
            var childIssue = client.getIssue(child.id());
            if (childIssue != null) {
                attachmentService.snapshotIssue(childIssue);
                addRole(entries, childIssue, new ContextRole(
                        ContextRole.Kind.CHILD, null, null, issueId, childIssue.id(), null));
            }
        }
        return truncated;
    }

    private boolean fetchRelations(RedmineIssue mainIssue, int issueId,
                                   Map<Integer, MutableEntry> entries) {
        if (mainIssue.relations() == null || mainIssue.relations().isEmpty()) {
            return false;
        }
        int maxRelated = properties.fullContext().maxRelated();
        boolean truncated = mainIssue.relations().size() > maxRelated;
        int relCount = 0;
        for (var rel : mainIssue.relations()) {
            if (relCount >= maxRelated) break;
            int relatedId = rel.issueId() == issueId ? rel.issueToId() : rel.issueId();
            String relType = formatRelationType(rel, issueId);
            var related = client.getIssue(relatedId);
            relCount++;
            if (related != null) {
                attachmentService.snapshotIssue(related);
                addRole(entries, related, new ContextRole(
                        ContextRole.Kind.RELATED, relType, rel.id(), issueId, relatedId, rel.delay()));
            }
        }
        return truncated;
    }

    private static void addRole(Map<Integer, MutableEntry> entries,
                                RedmineIssue issue,
                                ContextRole role) {
        entries.computeIfAbsent(issue.id(), ignored -> new MutableEntry(issue)).roles.add(role);
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

    public record Fetched(RedmineIssue issue, List<ContextRole> roles) {
        public RelatedRef toRef() {
            return new RelatedRef(
                    issue.id(),
                    issue.subject(),
                    Ref.from(issue.tracker()),
                    Ref.from(issue.status()),
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
        private final RedmineIssue issue;
        private final List<ContextRole> roles = new ArrayList<>();

        private MutableEntry(RedmineIssue issue) {
            this.issue = issue;
        }

        private Fetched toFetched() {
            return new Fetched(issue, List.copyOf(roles));
        }
    }
}

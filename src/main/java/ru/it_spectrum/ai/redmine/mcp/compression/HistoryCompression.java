package ru.it_spectrum.ai.redmine.mcp.compression;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.IssueHistory;
import ru.it_spectrum.ai.redmine.mcp.api.Opaque;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** Compresses history before its output-only Opaque fields are constructed. */
@Service
public class HistoryCompression {

    private final ResponseCompressor compressor;
    private final ObjectMapper mapper;
    private final RedmineMcpProperties properties;

    public HistoryCompression(ResponseCompressor compressor, ObjectMapper mapper,
                              RedmineMcpProperties properties) {
        this.compressor = compressor;
        this.mapper = mapper;
        this.properties = properties;
    }

    public record IndexedDuration(int eventIndex, IssueHistory.StatusDuration duration) {
    }

    /** Same JSON fields as IssueHistory, but without Opaque while compression is in progress. */
    public record Draft(
            List<IssueHistory.TimelineEntry> timeline,
            List<IssueHistory.StatusDuration> statusDurations,
            int offset,
            int totalEvents,
            Integer nextOffset,
            String sourceUpdatedOn,
            List<String> compressionNotes
    ) {
        Draft withTimeline(List<IssueHistory.TimelineEntry> entries, String note) {
            var notes = new ArrayList<>(compressionNotes != null ? compressionNotes : List.<String>of());
            notes.add(note);
            return new Draft(entries, statusDurations, offset, totalEvents, nextOffset,
                    sourceUpdatedOn, List.copyOf(notes));
        }

        Draft page(List<IssueHistory.TimelineEntry> entries,
                   List<IssueHistory.StatusDuration> durations, Integer next) {
            var notes = new ArrayList<>(compressionNotes != null ? compressionNotes : List.<String>of());
            if (next != null) {
                notes.add("More history events remain; call getIssueHistory with offset=" + next);
            }
            return new Draft(entries, durations, offset, totalEvents, next, sourceUpdatedOn,
                    notes.isEmpty() ? null : List.copyOf(notes));
        }

        IssueHistory toResponse() {
            return new IssueHistory(timeline.stream().map(Opaque::of).toList(),
                    statusDurations.stream().map(Opaque::of).toList(), offset, totalEvents,
                    nextOffset, sourceUpdatedOn, compressionNotes);
        }
    }

    public IssueHistory compress(int issueId, String sourceUpdatedOn,
                                 List<IssueHistory.TimelineEntry> timeline,
                                 List<IndexedDuration> indexedDurations, Integer requestedOffset) {
        int offset = requestedOffset != null ? requestedOffset : properties.pagination().defaultOffset();
        if (offset < 0 || offset > timeline.size()) {
            throw new IllegalArgumentException("History offset must be between 0 and " + timeline.size());
        }
        int budget = properties.response().maxChars();
        var initial = new Draft(timeline.subList(offset, timeline.size()),
                durationsFor(indexedDurations, offset, timeline.size()), offset,
                timeline.size(), null, sourceUpdatedOn, null);
        var steps = List.<CompressionStep<Draft>>of(
                new TextStep("description-values", "Long description values shortened; use getIssueJournal(issueId, journalId) for full text",
                        draft -> rewriteValues(draft, issueId, 160, true)),
                new TextStep("other-values", "Long field values shortened; use getIssueJournal(issueId, journalId) for full text",
                        draft -> rewriteValues(draft, issueId, 500, false)),
                new TextStep("notes-600", "Long journal notes shortened; use journalId with getIssueJournal for full text",
                        draft -> rewriteNotes(draft, issueId, 600)),
                new TextStep("notes-250", "Journal note previews shortened to 250 characters; use getIssueJournal for full text",
                        draft -> rewriteNotes(draft, issueId, 250)),
                new TextStep("notes-80", "Journal note previews shortened to 80 characters; use getIssueJournal for full text",
                        draft -> rewriteNotes(draft, issueId, 80)),
                new TextStep("other-fields", "Non-status field changes summarized; use getIssueJournal for complete details",
                        HistoryCompression::summarizeOtherFields));
        Draft compact = compressor.fit(initial, steps, budget).value();
        if (fits(compact, budget)) {
            return compact.toResponse();
        }
        // The shared compressor measures Java characters. UTF-8 can be larger; tighten once
        // before splitting the chronology into pages.
        compact = compressor.fit(initial, steps, Math.max(1, budget / 4)).value();
        if (fits(compact, budget)) {
            return compact.toResponse();
        }

        int low = offset + 1;
        int high = timeline.size();
        Draft best = null;
        while (low <= high) {
            int end = (low + high) >>> 1;
            Integer next = end < timeline.size() ? end : null;
            Draft candidate = compact.page(compact.timeline().subList(0, end - offset),
                    durationsFor(indexedDurations, offset, end), next);
            if (fits(candidate, budget)) {
                best = candidate;
                low = end + 1;
            } else {
                high = end - 1;
            }
        }
        if (best == null) {
            Integer next = offset + 1 < timeline.size() ? offset + 1 : null;
            var event = compact.timeline().getFirst();
            String recovery = event.journalId() != null
                    ? "getIssueJournal(issueId=" + issueId + ", journalId=" + event.journalId() + ")"
                    : "getIssue(issueId=" + issueId + ")";
            var minimal = new IssueHistory.TimelineEntry(event.kind(), event.journalId(),
                    prefix(event.timestamp(), 100), prefix(event.actor(), 100),
                    event.changes().isEmpty() ? List.of() : List.of(new IssueHistory.FieldChange(
                            event.changes().size() + " field change(s); see " + recovery, null, null)),
                    event.note() == null ? null : "Note shortened; see " + recovery);
            var notes = new ArrayList<>(compact.compressionNotes() != null
                    ? compact.compressionNotes() : List.<String>of());
            notes.add("One oversized event was reduced to metadata; status intervals for it were omitted; see "
                    + recovery + " for full details");
            if (next != null) {
                notes.add("More history events remain; call getIssueHistory with offset=" + next);
            }
            var emergency = new Draft(List.of(minimal), List.of(), offset, timeline.size(), next,
                    sourceUpdatedOn, List.copyOf(notes));
            if (!fits(emergency, budget)) {
                throw new IllegalStateException("History metadata exceeds the response budget; journalId="
                        + event.journalId());
            }
            return emergency.toResponse();
        }
        return best.toResponse();
    }

    private static String prefix(String value, int cap) {
        return value == null || value.length() <= cap ? value : value.substring(0, cap) + "…";
    }

    private static List<IssueHistory.StatusDuration> durationsFor(List<IndexedDuration> durations,
                                                                   int from, int to) {
        return durations.stream()
                .filter(d -> d.eventIndex() >= from && d.eventIndex() < to)
                .map(IndexedDuration::duration)
                .toList();
    }

    private boolean fits(Draft draft, int budget) {
        try {
            String json = mapper.writeValueAsString(draft);
            return json.length() <= budget && json.getBytes(StandardCharsets.UTF_8).length <= budget;
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to measure issue history response", e);
        }
    }

    private static List<IssueHistory.TimelineEntry> rewriteValues(Draft draft, int issueId,
                                                                    int cap, boolean descriptionsOnly) {
        var result = new ArrayList<IssueHistory.TimelineEntry>(draft.timeline().size());
        for (var entry : draft.timeline()) {
            var changes = new ArrayList<IssueHistory.FieldChange>(entry.changes().size());
            for (var change : entry.changes()) {
                boolean description = "Description".equals(change.fieldLabel());
                if (description == descriptionsOnly && !isCoreChange(change)) {
                    changes.add(new IssueHistory.FieldChange(change.fieldLabel(),
                            shorten(change.oldValue(), cap, issueId, entry.journalId()),
                            shorten(change.newValue(), cap, issueId, entry.journalId())));
                } else {
                    changes.add(change);
                }
            }
            result.add(copy(entry, List.copyOf(changes), entry.note()));
        }
        return result;
    }

    private static List<IssueHistory.TimelineEntry> rewriteNotes(Draft draft, int issueId, int cap) {
        var result = new ArrayList<IssueHistory.TimelineEntry>(draft.timeline().size());
        for (var entry : draft.timeline()) {
            result.add(copy(entry, entry.changes(), shorten(entry.note(), cap, issueId, entry.journalId())));
        }
        return result;
    }

    private static List<IssueHistory.TimelineEntry> summarizeOtherFields(Draft draft) {
        var result = new ArrayList<IssueHistory.TimelineEntry>(draft.timeline().size());
        for (var entry : draft.timeline()) {
            var changes = new ArrayList<IssueHistory.FieldChange>();
            int other = 0;
            for (var change : entry.changes()) {
                if (isCoreChange(change)) {
                    changes.add(change);
                } else {
                    other++;
                }
            }
            if (other > 0) {
                changes.add(new IssueHistory.FieldChange("%d other field change(s); see journal #%d"
                        .formatted(other, entry.journalId() != null ? entry.journalId() : 0), null, null));
            }
            result.add(copy(entry, List.copyOf(changes), entry.note()));
        }
        return result;
    }

    private static boolean isCoreChange(IssueHistory.FieldChange change) {
        return "Status".equals(change.fieldLabel()) || "Assigned to".equals(change.fieldLabel());
    }

    private static IssueHistory.TimelineEntry copy(IssueHistory.TimelineEntry entry,
                                                    List<IssueHistory.FieldChange> changes, String note) {
        return new IssueHistory.TimelineEntry(entry.kind(), entry.journalId(), entry.timestamp(),
                entry.actor(), changes, note);
    }

    private static String shorten(String value, int cap, Integer issueId, Integer journalId) {
        if (value == null || value.length() <= cap) {
            return value;
        }
        int marker = value.indexOf("… [truncated; total ");
        String original = marker >= 0 ? value.substring(0, marker) : value;
        int total = value.length();
        if (marker >= 0) {
            int end = value.indexOf(" chars", marker);
            if (end > marker) {
                try {
                    total = Integer.parseInt(value.substring(marker + 20, end));
                } catch (NumberFormatException ignored) {
                    // A coincidental user-authored marker is treated as ordinary text.
                }
            }
        }
        if (original.length() <= cap) {
            return value;
        }
        String retrieval = journalId != null
                ? issueId != null
                    ? "; getIssueJournal(issueId=" + issueId + ", journalId=" + journalId + ")"
                    : "; getIssueJournal(issueId, journalId=" + journalId + ")"
                : "";
        return original.substring(0, cap) + "… [truncated; total " + total + " chars" + retrieval + "]";
    }

    private record TextStep(String name, String note,
                            Function<Draft, List<IssueHistory.TimelineEntry>> rewrite)
            implements CompressionStep<Draft> {
        @Override
        public Optional<Compressed<Draft>> apply(Draft draft) {
            List<IssueHistory.TimelineEntry> entries = rewrite.apply(draft);
            return entries.equals(draft.timeline())
                    ? Optional.empty()
                    : Optional.of(new Compressed<>(draft.withTimeline(entries, note), note));
        }
    }
}

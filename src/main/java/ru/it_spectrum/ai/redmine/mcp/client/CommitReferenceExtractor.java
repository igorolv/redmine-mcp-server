package ru.it_spectrum.ai.redmine.mcp.client;

import ru.it_spectrum.ai.redmine.mcp.api.Changeset;
import ru.it_spectrum.ai.redmine.mcp.api.Ref;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CommitReferenceExtractor {

    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://\\S+/(?:commits?|changesets?|revisions?)/([0-9a-fA-F]{4,40}|\\d+)",
            Pattern.CASE_INSENSITIVE);

    private static final int DEDUP_MIN_LEN = 4;

    private CommitReferenceExtractor() {
    }

    public static List<Changeset> extractFromJournals(List<RedmineIssue.Journal> journals,
                                                      List<Changeset> existing) {
        if (journals == null || journals.isEmpty()) {
            return List.of();
        }
        var existingRevs = new ArrayList<String>();
        if (existing != null) {
            for (var c : existing) {
                if (c != null && c.revision() != null && !c.revision().isBlank()) {
                    existingRevs.add(c.revision().toLowerCase(Locale.ROOT));
                }
            }
        }
        var byRev = new LinkedHashMap<String, Changeset>();
        for (var journal : journals) {
            if (journal == null) continue;
            var notes = journal.notes();
            if (notes == null || notes.isBlank()) continue;
            Matcher matcher = URL_PATTERN.matcher(notes);
            while (matcher.find()) {
                String rev = matcher.group(1).toLowerCase(Locale.ROOT);
                if (rev.length() < DEDUP_MIN_LEN) continue;
                if (matchesAny(rev, existingRevs)) continue;
                String existingKey = findMatchingKey(rev, byRev.keySet());
                if (existingKey != null) {
                    if (rev.length() > existingKey.length()) {
                        Changeset prev = byRev.remove(existingKey);
                        byRev.put(rev, new Changeset(
                                rev, prev.user(), null, prev.committedOn(),
                                Changeset.SOURCE_COMMENT_REFERENCE));
                    }
                    continue;
                }
                byRev.put(rev, new Changeset(
                        rev,
                        Ref.from(journal.user()),
                        null,
                        journal.createdOn(),
                        Changeset.SOURCE_COMMENT_REFERENCE));
            }
        }
        return List.copyOf(byRev.values());
    }

    private static boolean matchesAny(String rev, List<String> existing) {
        for (var e : existing) {
            if (revsMatch(rev, e)) return true;
        }
        return false;
    }

    private static String findMatchingKey(String rev, Iterable<String> keys) {
        for (var k : keys) {
            if (revsMatch(rev, k)) return k;
        }
        return null;
    }

    private static boolean revsMatch(String a, String b) {
        if (a.length() >= b.length()) {
            return a.startsWith(b);
        }
        return b.startsWith(a);
    }
}

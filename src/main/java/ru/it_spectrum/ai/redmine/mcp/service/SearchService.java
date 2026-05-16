package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.SearchResult;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchType;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class SearchService {

    private static final String EXPECTED_TYPES =
            "issues, wiki_pages, news, documents, changesets, messages, projects";

    private static final Map<String, SearchType> TYPE_ALIASES = Map.ofEntries(
            Map.entry("issue", SearchType.ISSUES),
            Map.entry("issues", SearchType.ISSUES),
            Map.entry("wiki", SearchType.WIKI_PAGES),
            Map.entry("wiki_page", SearchType.WIKI_PAGES),
            Map.entry("wiki_pages", SearchType.WIKI_PAGES),
            Map.entry("news", SearchType.NEWS),
            Map.entry("document", SearchType.DOCUMENTS),
            Map.entry("documents", SearchType.DOCUMENTS),
            Map.entry("changeset", SearchType.CHANGESETS),
            Map.entry("changesets", SearchType.CHANGESETS),
            Map.entry("message", SearchType.MESSAGES),
            Map.entry("messages", SearchType.MESSAGES),
            Map.entry("project", SearchType.PROJECTS),
            Map.entry("projects", SearchType.PROJECTS)
    );

    private final RedmineClient client;

    public SearchService(RedmineClient client) {
        this.client = client;
    }

    public SearchResult searchAll(String query, String projectId, String types, int offset, int limit) {
        return SearchResult.from(
                client.search(query, projectId, parseTypes(types), true, offset, limit));
    }

    Set<SearchType> parseTypes(String types) {
        if (types == null || types.isBlank()) {
            return Set.of();
        }
        var parsed = new LinkedHashSet<SearchType>();
        for (String token : types.split("[,;\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            String key = token.trim().toLowerCase(Locale.ROOT).replace('-', '_');
            SearchType type = TYPE_ALIASES.get(key);
            if (type == null) {
                throw new IllegalArgumentException(
                        "Invalid search type '%s'. Expected one of: %s.".formatted(token, EXPECTED_TYPES));
            }
            parsed.add(type);
        }
        return Collections.unmodifiableSet(parsed);
    }
}

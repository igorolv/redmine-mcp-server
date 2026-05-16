package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.SearchResult;
import ru.it_spectrum.ai.redmine.mcp.api.WikiPage;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchType;

import java.util.List;
import java.util.Set;

@Service
public class WikiService {
    private final RedmineClient client;

    public WikiService(RedmineClient client) {
        this.client = client;
    }

    public WikiPage getPageOrThrow(String projectId, String pageTitle) {
        var page = client.getWikiPage(projectId, pageTitle);
        if (page == null) {
            throw new ResourceNotFoundException("wiki page", pageTitle);
        }
        return WikiPage.from(page);
    }

    public List<WikiPage> listPages(String projectId) {
        var pages = client.getWikiIndex(projectId);
        if (pages == null) {
            return List.of();
        }
        return pages.stream().map(WikiPage::from).toList();
    }

    public SearchResult searchPages(String query, String projectId, int offset, int limit) {
        return SearchResult.from(
                client.search(query, projectId, Set.of(SearchType.WIKI_PAGES), false, offset, limit));
    }
}

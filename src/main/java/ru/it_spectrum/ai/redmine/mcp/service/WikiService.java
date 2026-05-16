package ru.it_spectrum.ai.redmine.mcp.service;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchType;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineSearchResult;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPage;

import java.util.List;
import java.util.Set;

@Service
public class WikiService {
    private final RedmineClient client;

    public WikiService(RedmineClient client) {
        this.client = client;
    }

    public RedmineWikiPage getPageOrThrow(String projectId, String pageTitle) {
        var page = client.getWikiPage(projectId, pageTitle);
        if (page == null) {
            throw new ResourceNotFoundException("wiki page", pageTitle);
        }
        return page;
    }

    public List<RedmineWikiPage> listPages(String projectId) {
        return client.getWikiIndex(projectId);
    }

    public RedmineSearchResult searchPages(String query, String projectId, int offset, int limit) {
        return client.search(query, projectId, Set.of(SearchType.WIKI_PAGES), false, offset, limit);
    }
}

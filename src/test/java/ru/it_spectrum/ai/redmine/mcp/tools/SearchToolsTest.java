package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient.SearchType;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineSearchResult;
import ru.it_spectrum.ai.redmine.mcp.service.SearchService;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchToolsTest {

    @Mock
    private RedmineClient client;

    private SearchTools tools;

    @BeforeEach
    void setUp() {
        tools = new SearchTools(new SearchService(client));
    }

    @Test
    void shouldSearchAllContent() {
        var results = List.of(
                new RedmineSearchResult.ResultItem(101, "Login bug", "issue",
                        "http://redmine/issues/101", "Cannot login with LDAP", "2025-03-01T10:00:00Z"),
                new RedmineSearchResult.ResultItem(5, "Auth Guide", "wiki-page",
                        "http://redmine/wiki/Auth_Guide", "Authentication setup guide", "2025-02-15T08:00:00Z")
        );
        when(client.search("auth", null, Set.of(), true, 0, 25))
                .thenReturn(new RedmineSearchResult(results, 2, 0, 25));

        var result = ToolJsonTestSupport.stringify(tools.searchAll("auth", null, null, null, null));

        assertThat(result).contains("\"totalCount\":2");
        assertThat(result).contains("\"type\":\"issue\"");
        assertThat(result).contains("\"id\":101");
        assertThat(result).contains("Login bug");
        assertThat(result).contains("Cannot login with LDAP");
        assertThat(result).contains("\"type\":\"wiki-page\"");
        assertThat(result).contains("\"id\":5");
        assertThat(result).contains("Auth Guide");
    }

    @Test
    void shouldSearchSelectedTypesInProject() {
        var results = List.of(
                new RedmineSearchResult.ResultItem(5, "Auth Guide", "wiki-page",
                        "http://redmine/wiki/Auth_Guide", "Authentication setup guide", "2025-02-15T08:00:00Z")
        );
        when(client.search("auth", "backend", Set.of(SearchType.ISSUES, SearchType.WIKI_PAGES), true, 10, 5))
                .thenReturn(new RedmineSearchResult(results, 1, 10, 5));

        var result = ToolJsonTestSupport.stringify(tools.searchAll("auth", "backend", "issues,wiki-pages", 5, 10));

        assertThat(result).contains("\"totalCount\":1");
        assertThat(result).contains("\"type\":\"wiki-page\"");
        assertThat(result).contains("Auth Guide");
    }

    @Test
    void shouldRejectInvalidSearchType() {
        assertThatThrownBy(() -> tools.searchAll("auth", null, "invalid", null, null))
                .hasMessageContaining("Invalid search type");
        verifyNoInteractions(client);
    }
}

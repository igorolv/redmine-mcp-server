package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPage;
import ru.it_spectrum.ai.redmine.mcp.service.WikiService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikiToolsTest {

    @Mock
    private RedmineClient client;

    private WikiTools tools;

    @BeforeEach
    void setUp() {
        tools = new WikiTools(new WikiService(client), ToolJsonTestSupport.json(), ToolJsonTestSupport.errors());
    }

    // --- getWikiPage ---

    @Test
    void shouldGetWikiPage() {
        var attachments = List.of(
                new RedmineAttachment(10, "diagram.png", 50_000, "image/png",
                        "http://redmine/download/10/diagram.png", null,
                        new IdName(1, "Author"), "2025-01-01")
        );
        var page = new RedmineWikiPage("Getting Started", "h1. Welcome\n\nThis is the start page.",
                3, new IdName(1, "Admin"), null, null, "2025-02-01", attachments);
        when(client.getWikiPage("backend", "Getting_Started")).thenReturn(page);

        String result = tools.getWikiPage("backend", "Getting_Started");

        assertThat(result).contains("\"title\":\"Getting Started\"");
        assertThat(result).contains("Admin");
        assertThat(result).contains("\"version\":3");
        assertThat(result).contains("h1. Welcome");
        assertThat(result).contains("This is the start page.");
        assertThat(result).contains("\"id\":10");
        assertThat(result).contains("diagram.png");
    }

    @Test
    void shouldHandleWikiPageNotFound() {
        when(client.getWikiPage("backend", "Nonexistent")).thenReturn(null);

        String result = tools.getWikiPage("backend", "Nonexistent");

        assertThat(result).contains("\"kind\":\"not_found\"");
        assertThat(result).contains("wiki page Nonexistent not found");
    }

    // --- listWikiPages ---

    @Test
    void shouldListWikiPages() {
        var pages = List.of(
                new RedmineWikiPage("Wiki", null, 0, null, null, null, "2025-01-01", null),
                new RedmineWikiPage("API Guide", null, 0, null, null, null, "2025-02-15", null)
        );
        when(client.getWikiIndex("backend")).thenReturn(pages);

        String result = tools.listWikiPages("backend");

        assertThat(result).contains("\"title\":\"Wiki\"");
        assertThat(result).contains("2025-01-01");
        assertThat(result).contains("\"title\":\"API Guide\"");
        assertThat(result).contains("2025-02-15");
    }

    @Test
    void shouldHandleNoWikiPages() {
        when(client.getWikiIndex("empty")).thenReturn(List.of());

        String result = tools.listWikiPages("empty");

        assertThat(result).isEqualTo("[]");
    }
}

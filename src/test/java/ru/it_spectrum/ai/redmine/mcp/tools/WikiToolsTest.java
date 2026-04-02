package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineWikiPage;

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
        tools = new WikiTools(client);
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

        assertThat(result).contains("Wiki: Getting Started");
        assertThat(result).contains("Author: Admin");
        assertThat(result).contains("Version: 3");
        assertThat(result).contains("h1. Welcome");
        assertThat(result).contains("This is the start page.");
        assertThat(result).contains("Attachments (1):");
        assertThat(result).contains("[10] diagram.png");
    }

    @Test
    void shouldHandleWikiPageNotFound() {
        when(client.getWikiPage("backend", "Nonexistent")).thenReturn(null);

        String result = tools.getWikiPage("backend", "Nonexistent");

        assertThat(result).isEqualTo("Wiki page 'Nonexistent' not found in project 'backend'");
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

        assertThat(result).contains("Wiki pages in project 'backend' (2):");
        assertThat(result).contains("- Wiki (updated: 2025-01-01)");
        assertThat(result).contains("- API Guide (updated: 2025-02-15)");
    }

    @Test
    void shouldHandleNoWikiPages() {
        when(client.getWikiIndex("empty")).thenReturn(List.of());

        String result = tools.listWikiPages("empty");

        assertThat(result).isEqualTo("No wiki pages found in project 'empty'");
    }
}

package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.api.WikiMutationResult;
import ru.it_spectrum.ai.redmine.mcp.service.WikiMutationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WikiWriteToolsTest {

    @Mock
    private WikiMutationService mutationService;

    @Test
    void createWikiPageReturnsStableMutationResult() {
        when(mutationService.createPage("backend", "Runbook", "h1. Runbook", "Operations", "Initial"))
                .thenReturn(new WikiMutationResult("backend", "Runbook", 1));
        var tools = new WikiWriteTools(mutationService);

        var json = ToolJsonTestSupport.stringify(
                tools.createWikiPage("backend", "Runbook", "h1. Runbook", "Operations", "Initial"));

        assertThat(json).contains("\"projectId\":\"backend\"")
                .contains("\"pageTitle\":\"Runbook\"")
                .contains("\"version\":1");
    }

    @Test
    void updateWikiPageDelegatesExpectedVersion() {
        when(mutationService.updatePage("backend", "Runbook", "Updated", 3, "Refresh"))
                .thenReturn(new WikiMutationResult("backend", "Runbook", 4));
        var tools = new WikiWriteTools(mutationService);

        var json = ToolJsonTestSupport.stringify(
                tools.updateWikiPage("backend", "Runbook", "Updated", 3, "Refresh"));

        assertThat(json).contains("\"version\":4");
        verify(mutationService).updatePage("backend", "Runbook", "Updated", 3, "Refresh");
    }
}

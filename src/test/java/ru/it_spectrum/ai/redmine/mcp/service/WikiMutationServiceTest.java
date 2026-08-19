package ru.it_spectrum.ai.redmine.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationException;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPage;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPageMutation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class WikiMutationServiceTest {

    @Mock
    private RedmineMutationClient mutationClient;
    @Mock
    private RedmineClient client;

    private WikiMutationService service;

    @BeforeEach
    void setUp() {
        service = new WikiMutationService(mutationClient, client, new AiContentMarker());
    }

    @Test
    void shouldCreatePageWithMarkedRevisionComment() {
        when(client.getWikiPage("backend", "Runbook"))
                .thenReturn(null, page("Runbook", "New text", 1));

        var result = service.createPage("backend", "Runbook", "New text", "Operations", "Initial draft");

        var fields = ArgumentCaptor.forClass(RedmineWikiPageMutation.Fields.class);
        verify(mutationClient).putWikiPage(eq("backend"), eq("Runbook"), fields.capture());
        assertThat(fields.getValue().text()).isEqualTo("New text");
        assertThat(fields.getValue().comments()).isEqualTo("AI_EDIT:\n\nInitial draft");
        assertThat(fields.getValue().parentTitle()).isEqualTo("Operations");
        assertThat(fields.getValue().version()).isNull();
        assertThat(result.version()).isEqualTo(1);
    }

    @Test
    void shouldRejectCreatingExistingPage() {
        when(client.getWikiPage("backend", "Runbook")).thenReturn(page("Runbook", "Old", 3));

        assertThatThrownBy(() -> service.createPage("backend", "Runbook", "New", null, null))
                .isInstanceOf(WikiPageAlreadyExistsException.class)
                .hasMessageContaining("already exists");
        verifyNoInteractions(mutationClient);
    }

    @Test
    void shouldUpdatePageWithOptimisticLock() {
        when(client.getWikiPage("backend", "Runbook"))
                .thenReturn(page("Runbook", "Old text", 3), page("Runbook", "New text", 4));

        var result = service.updatePage("backend", "Runbook", "New text", 3, "Refresh steps");

        var fields = ArgumentCaptor.forClass(RedmineWikiPageMutation.Fields.class);
        verify(mutationClient).putWikiPage(eq("backend"), eq("Runbook"), fields.capture());
        assertThat(fields.getValue().text()).isEqualTo("New text");
        assertThat(fields.getValue().comments()).isEqualTo("AI_EDIT:\n\nRefresh steps");
        assertThat(fields.getValue().parentTitle()).isNull();
        assertThat(fields.getValue().version()).isEqualTo(3);
        assertThat(result.version()).isEqualTo(4);
    }

    @Test
    void shouldReturnWithoutWriteWhenTextIsUnchanged() {
        when(client.getWikiPage("backend", "Runbook")).thenReturn(page("Runbook", "Same text", 3));

        var result = service.updatePage("backend", "Runbook", "Same text", 3, "No changes");

        assertThat(result.version()).isEqualTo(3);
        verifyNoInteractions(mutationClient);
        verify(client).getWikiPage("backend", "Runbook");
    }

    @Test
    void shouldRejectStaleVersionBeforeWrite() {
        when(client.getWikiPage("backend", "Runbook")).thenReturn(page("Runbook", "Current", 4));

        assertThatThrownBy(() -> service.updatePage("backend", "Runbook", "New", 3, null))
                .isInstanceOf(WikiVersionConflictException.class)
                .hasMessageContaining("call getWikiPage");
        verifyNoInteractions(mutationClient);
    }

    @Test
    void shouldTranslateRemoteConflictCausedByConcurrentUpdate() {
        when(client.getWikiPage("backend", "Runbook")).thenReturn(page("Runbook", "Old", 3));
        var conflict = new RedmineMutationException(409, "", null);
        org.mockito.Mockito.doThrow(conflict)
                .when(mutationClient).putWikiPage(org.mockito.ArgumentMatchers.eq("backend"),
                        org.mockito.ArgumentMatchers.eq("Runbook"), org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> service.updatePage("backend", "Runbook", "New", 3, null))
                .isInstanceOf(WikiVersionConflictException.class);
        verify(client).getWikiPage("backend", "Runbook");
    }

    @Test
    void shouldRejectUpdatingMissingPage() {
        when(client.getWikiPage("backend", "Missing")).thenReturn(null);

        assertThatThrownBy(() -> service.updatePage("backend", "Missing", "New", 1, null))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("wiki page Missing not found");
        verifyNoInteractions(mutationClient);
    }

    private RedmineWikiPage page(String title, String text, int version) {
        return new RedmineWikiPage(title, text, version, null, null, null, null, null);
    }
}

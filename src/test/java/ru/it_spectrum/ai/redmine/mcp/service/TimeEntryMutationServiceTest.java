package ru.it_spectrum.ai.redmine.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineCustomFieldValue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntryMutation;
import ru.it_spectrum.ai.redmine.mcp.config.JsonConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeEntryMutationServiceTest {

    @Mock
    private RedmineMutationClient mutationClient;

    private TimeEntryMutationService service;

    @BeforeEach
    void setUp() {
        service = new TimeEntryMutationService(
                mutationClient,
                new AiContentMarker(),
                new CustomFieldValuesParser(new JsonConfig().redmineMcpObjectMapper()));
    }

    @Test
    void shouldCreateMarkedIssueTimeEntryWithCustomFields() {
        when(mutationClient.createTimeEntry(any())).thenReturn(timeEntry(321));

        var result = service.createTimeEntry(
                123, null, 1.5, 9, "2026-08-19", "Implementation", "{\"10\":\"remote\"}");

        var captor = ArgumentCaptor.forClass(RedmineTimeEntryMutation.Fields.class);
        org.mockito.Mockito.verify(mutationClient).createTimeEntry(captor.capture());
        assertThat(captor.getValue()).satisfies(fields -> {
            assertThat(fields.issueId()).isEqualTo(123);
            assertThat(fields.projectId()).isNull();
            assertThat(fields.comments()).isEqualTo("AI_EDIT:\n\nImplementation");
            assertThat(fields.customFields()).containsExactly(new RedmineCustomFieldValue(10, "remote"));
        });
        assertThat(result.timeEntryId()).isEqualTo(321);
    }

    @Test
    void shouldCreateProjectTimeEntryWithMarkerOnlyComment() {
        when(mutationClient.createTimeEntry(any())).thenReturn(timeEntry(322));

        service.createTimeEntry(null, 7, 0, null, null, null, null);

        var captor = ArgumentCaptor.forClass(RedmineTimeEntryMutation.Fields.class);
        org.mockito.Mockito.verify(mutationClient).createTimeEntry(captor.capture());
        assertThat(captor.getValue().projectId()).isEqualTo(7);
        assertThat(captor.getValue().comments()).isEqualTo("AI_EDIT:");
    }

    @Test
    void shouldRequireExactlyOneTarget() {
        assertThatThrownBy(() -> service.createTimeEntry(null, null, 1, null, null, null, null))
                .hasMessageContaining("Exactly one");
        assertThatThrownBy(() -> service.createTimeEntry(1, 2, 1, null, null, null, null))
                .hasMessageContaining("Exactly one");

        verifyNoInteractions(mutationClient);
    }

    @Test
    void shouldRejectInvalidHoursAndOversizedMarkedComment() {
        assertThatThrownBy(() -> service.createTimeEntry(1, null, -1, null, null, null, null))
                .hasMessageContaining("non-negative");
        assertThatThrownBy(() -> service.createTimeEntry(1, null, 1, null, null, "x".repeat(1024), null))
                .hasMessageContaining("1024");

        verifyNoInteractions(mutationClient);
    }

    private RedmineTimeEntry timeEntry(int id) {
        return new RedmineTimeEntry(
                id, new IdName(7, "project"), new IdName(123, "Issue"),
                new IdName(42, "API User"), new IdName(9, "Development"),
                1.5, "AI_EDIT:", "2026-08-19", null, null);
    }
}

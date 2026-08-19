package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.api.TimeEntryMutationResult;
import ru.it_spectrum.ai.redmine.mcp.service.TimeEntryMutationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeEntryWriteToolsTest {

    @Mock
    private TimeEntryMutationService mutationService;

    private TimeEntryWriteTools tools;

    @BeforeEach
    void setUp() {
        tools = new TimeEntryWriteTools(mutationService);
    }

    @Test
    void createTimeEntryReturnsStableMutationResult() {
        when(mutationService.createTimeEntry(
                123, null, 1.5, 9, "2026-08-19", "Implementation", "{\"10\":\"remote\"}"))
                .thenReturn(new TimeEntryMutationResult(321));

        var result = tools.createTimeEntry(
                123, null, 1.5, 9, "2026-08-19", "Implementation", "{\"10\":\"remote\"}");

        assertThat(ToolJsonTestSupport.stringify(result)).isEqualTo("{\"timeEntryId\":321}");
        verify(mutationService).createTimeEntry(
                123, null, 1.5, 9, "2026-08-19", "Implementation", "{\"10\":\"remote\"}");
    }
}

package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.api.IssueMutationResult;
import ru.it_spectrum.ai.redmine.mcp.service.IssueMutationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueWriteToolsTest {

    @Mock
    private IssueMutationService mutationService;

    private IssueWriteTools tools;

    @BeforeEach
    void setUp() {
        tools = new IssueWriteTools(mutationService);
    }

    @Test
    void createIssueReturnsStableMutationResult() {
        when(mutationService.createIssue(any())).thenReturn(
                new IssueMutationResult(101, null, null));

        var result = tools.createIssue(
                "project", "Subject", "Description", 1, null, 2, 42,
                null, null, null, null, null, null, null, false, null);

        assertThat(ToolJsonTestSupport.stringify(result))
                .isEqualTo("{\"issueId\":101}");
    }

    @Test
    void updateIssueReturnsStableMutationResult() {
        when(mutationService.updateIssue(any(Integer.class), any())).thenReturn(
                new IssueMutationResult(102, null, null));

        var result = tools.updateIssue(
                102, null, "New subject", null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);

        assertThat(ToolJsonTestSupport.stringify(result))
                .isEqualTo("{\"issueId\":102}");
    }

    @Test
    void addIssueNoteReturnsJournalId() {
        when(mutationService.addIssueNote(103, "Note")).thenReturn(
                new IssueMutationResult(103, 501, null));

        var result = tools.addIssueNote(103, "Note");

        assertThat(ToolJsonTestSupport.stringify(result))
                .isEqualTo("{\"issueId\":103,\"journalId\":501}");
    }

    @Test
    void attachFileReturnsAttachmentIdAndForwardsArbitraryPath() {
        String path = "C:\\outside\\allowed-directories\\report.txt";
        when(mutationService.attachFileToIssue(104, path, "Report")).thenReturn(
                new IssueMutationResult(104, null, 601));

        var result = tools.attachFileToIssue(104, path, "Report");

        assertThat(ToolJsonTestSupport.stringify(result))
                .isEqualTo("{\"issueId\":104,\"attachmentId\":601}");
        verify(mutationService).attachFileToIssue(104, path, "Report");
    }
}

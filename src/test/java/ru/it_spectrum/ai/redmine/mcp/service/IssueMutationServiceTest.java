package ru.it_spectrum.ai.redmine.mcp.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineCustomFieldValue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueMutation;
import ru.it_spectrum.ai.redmine.mcp.config.JsonConfig;
import ru.it_spectrum.ai.redmine.mcp.service.IssueMutationService.IssueFields;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueMutationServiceTest {

    @TempDir
    private Path tempDir;

    @Mock
    private RedmineMutationClient mutationClient;
    @Mock
    private RedmineClient client;
    @Mock
    private IssueSnapshotService snapshotService;

    private IssueMutationService service;

    @BeforeEach
    void setUp() {
        service = new IssueMutationService(
                mutationClient, client, snapshotService, new AiContentMarker(),
                new CustomFieldValuesParser(new JsonConfig().redmineMcpObjectMapper()));
    }

    @Test
    void shouldCreateMarkedIssueAndParseCustomFields() {
        when(mutationClient.createIssue(any())).thenReturn(issue(123, "Created", null, null));
        var refreshed = issue(123, "Created", null, null);
        when(client.getIssue(123)).thenReturn(refreshed);

        var result = service.createIssue(fields(
                "project", "Subject", "Description", "{\"10\":\"rtk\",\"11\":[\"a\",\"b\"]}"));

        var captor = ArgumentCaptor.forClass(RedmineIssueMutation.Fields.class);
        verify(mutationClient).createIssue(captor.capture());
        assertThat(captor.getValue().description()).isEqualTo("AI_EDIT:\n\nDescription");
        assertThat(captor.getValue().customFields())
                .containsExactly(
                        new RedmineCustomFieldValue(10, "rtk"),
                        new RedmineCustomFieldValue(11, List.of("a", "b")));
        assertThat(result.issueId()).isEqualTo(123);
        verify(snapshotService).snapshotIssue(refreshed, "POST /issues.json");
    }

    @Test
    void shouldRejectEmptyUpdateWithoutCallingRedmine() {
        assertThatThrownBy(() -> service.updateIssue(123, fields(null, null, null, null)))
                .hasMessageContaining("At least one issue field");
        verifyNoInteractions(mutationClient, client, snapshotService);
    }

    @Test
    void shouldMarkUpdatedDescription() {
        var refreshed = issue(123, "Updated", null, null);
        when(client.getIssue(123)).thenReturn(refreshed);

        service.updateIssue(123, fields(null, null, "New description", null));

        var captor = ArgumentCaptor.forClass(RedmineIssueMutation.Fields.class);
        verify(mutationClient).updateIssue(eq(123), captor.capture());
        assertThat(captor.getValue().description()).isEqualTo("AI_EDIT:\n\nNew description");
        verify(snapshotService).snapshotIssue(refreshed, "PUT /issues/123.json");
    }

    @Test
    void shouldReportSuccessfulUpdateWhenPostWriteRefreshFails() {
        when(client.getIssue(123)).thenThrow(new RuntimeException("temporary read failure"));

        var result = service.updateIssue(123, fields(null, "Changed", null, null));

        assertThat(result.issueId()).isEqualTo(123);
        verify(mutationClient).updateIssue(eq(123), any());
    }

    @Test
    void shouldAddMarkedNoteAndResolveNewJournalId() {
        var before = issue(123, "Issue", List.of(journal(10, "Old")), null);
        var after = issue(123, "Issue", List.of(journal(10, "Old"), journal(11, "AI_EDIT:\n\nNew")), null);
        when(client.getIssue(123)).thenReturn(before, after);

        var result = service.addIssueNote(123, "New");

        var captor = ArgumentCaptor.forClass(RedmineIssueMutation.Fields.class);
        verify(mutationClient).updateIssue(eq(123), captor.capture());
        assertThat(captor.getValue().notes()).isEqualTo("AI_EDIT:\n\nNew");
        assertThat(result.journalId()).isEqualTo(11);
        verify(snapshotService).snapshotIssue(after, "PUT /issues/123.json (note)");
    }

    @Test
    void shouldAttachArbitraryReadableFileAndResolveAttachmentId() throws Exception {
        Path file = tempDir.resolve("report.txt");
        Files.writeString(file, "evidence");
        var before = issue(123, "Issue", null, List.of());
        var attachment = attachment(77, "AI_EDIT__report.txt");
        var after = issue(123, "Issue", null, List.of(attachment));
        when(client.getIssue(123)).thenReturn(before, after);
        when(mutationClient.upload("AI_EDIT__report.txt", file.toAbsolutePath().normalize())).thenReturn("77.token");

        var result = service.attachFileToIssue(123, file.toString(), "Test evidence");

        verify(mutationClient).upload("AI_EDIT__report.txt", file.toAbsolutePath().normalize());

        var fields = ArgumentCaptor.forClass(RedmineIssueMutation.Fields.class);
        verify(mutationClient).updateIssue(eq(123), fields.capture());
        assertThat(fields.getValue().uploads()).singleElement().satisfies(upload -> {
            assertThat(upload.token()).isEqualTo("77.token");
            assertThat(upload.filename()).isEqualTo("AI_EDIT__report.txt");
            assertThat(upload.description()).isEqualTo("Test evidence");
        });
        assertThat(result.attachmentId()).isEqualTo(77);
        verify(snapshotService).snapshotIssue(after, "POST /uploads.json + PUT /issues/123.json");
    }

    @Test
    void shouldRejectUnreadableFileBeforeUpload() {
        Path missing = tempDir.resolve("missing.txt");

        assertThatThrownBy(() -> service.attachFileToIssue(123, missing.toString(), null))
                .hasMessageContaining("readable regular file");
        verify(mutationClient, never()).upload(any(), any());
    }

    @Test
    void shouldRejectInvalidCustomFieldsJson() {
        assertThatThrownBy(() -> service.createIssue(fields("project", "Subject", null, "[]")))
                .hasMessageContaining("JSON object");
        verifyNoInteractions(mutationClient);
    }

    private IssueFields fields(String projectId, String subject, String description, String customFieldsJson) {
        return new IssueFields(
                projectId, null, null, null, subject, description,
                null, null, null, null, null, null, null, null, null, customFieldsJson);
    }

    private RedmineIssue issue(int id, String subject, List<RedmineIssue.Journal> journals,
                               List<RedmineAttachment> attachments) {
        return new RedmineIssue(
                id,
                new IdName(1, "project"), new IdName(1, "Bug"), new IdName(1, "Open"),
                new IdName(2, "Normal"), new IdName(3, "Author"), null,
                null, null, null, subject, "description",
                null, null, 0, null, null, false,
                "2025-01-01T00:00:00Z", "2025-01-02T00:00:00Z",
                null, attachments, journals, null, null);
    }

    private RedmineIssue.Journal journal(int id, String notes) {
        return new RedmineIssue.Journal(
                id, new IdName(3, "Author"), notes, "2025-01-02T00:00:00Z", List.of());
    }

    private RedmineAttachment attachment(int id, String filename) {
        return new RedmineAttachment(
                id, filename, 8, "text/plain", "http://redmine/attachments/" + id,
                null, new IdName(3, "Author"), "2025-01-02T00:00:00Z");
    }
}

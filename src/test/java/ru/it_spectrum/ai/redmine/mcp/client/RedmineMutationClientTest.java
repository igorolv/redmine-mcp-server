package ru.it_spectrum.ai.redmine.mcp.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineCustomFieldValue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueMutation;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntryMutation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.FORBIDDEN;

class RedmineMutationClientTest {

    @TempDir
    private Path tempDir;

    private MockRestServiceServer server;
    private RedmineMutationClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl("http://redmine.test")
                .defaultHeader("Content-Type", "application/json");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RedmineMutationClient(builder.build());
    }

    @Test
    void shouldCreateIssueWithJsonBody() {
        var fields = fields("project", "Subject", "AI_EDIT:\n\nDescription", null, null);
        server.expect(once(), requestTo("http://redmine.test/issues.json"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"issue":{"project_id":"project","subject":"Subject","description":"AI_EDIT:\\n\\nDescription"}}
                        """))
                .andRespond(withSuccess(
                        "{\"issue\":{\"id\":123,\"done_ratio\":0,\"is_private\":false}}",
                        MediaType.APPLICATION_JSON));

        var issue = client.createIssue(fields);

        assertThat(issue.id()).isEqualTo(123);
        server.verify();
    }

    @Test
    void shouldUpdateIssueWithNote() {
        var fields = fields(null, null, null, "AI_EDIT:\n\nNote", null);
        server.expect(requestTo("http://redmine.test/issues/123.json"))
                .andExpect(method(PUT))
                .andExpect(content().json("{\"issue\":{\"notes\":\"AI_EDIT:\\n\\nNote\"}}"))
                .andRespond(withSuccess());

        client.updateIssue(123, fields);

        server.verify();
    }

    @Test
    void shouldCreateTimeEntryWithJsonBody() {
        var fields = new RedmineTimeEntryMutation.Fields(
                null, 123, 1.5, 9, "2026-08-19", "AI_EDIT:\n\nImplementation",
                List.of(new RedmineCustomFieldValue(10, "remote")));
        server.expect(once(), requestTo("http://redmine.test/time_entries.json"))
                .andExpect(method(POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {"time_entry":{"issue_id":123,"hours":1.5,"activity_id":9,
                        "spent_on":"2026-08-19","comments":"AI_EDIT:\\n\\nImplementation",
                        "custom_fields":[{"id":10,"value":"remote"}]}}
                        """))
                .andRespond(withSuccess("""
                        {"time_entry":{"id":321,"issue":{"id":123},"hours":1.5,
                        "comments":"AI_EDIT:\\n\\nImplementation","spent_on":"2026-08-19"}}
                        """, MediaType.APPLICATION_JSON));

        var timeEntry = client.createTimeEntry(fields);

        assertThat(timeEntry.id()).isEqualTo(321);
        server.verify();
    }

    @Test
    void shouldUploadBinaryAndReturnToken() throws Exception {
        byte[] bytes = {1, 2, 3};
        Path file = tempDir.resolve("report.txt");
        Files.write(file, bytes);
        server.expect(requestTo("http://redmine.test/uploads.json?filename=AI_EDIT__report.txt"))
                .andExpect(method(POST))
                .andExpect(header("Content-Type", "application/octet-stream"))
                .andExpect(content().bytes(bytes))
                .andRespond(withSuccess("{\"upload\":{\"token\":\"123.token\"}}", MediaType.APPLICATION_JSON));

        assertThat(client.upload("AI_EDIT__report.txt", file)).isEqualTo("123.token");
        server.verify();
    }

    @Test
    void shouldExposeRedmineErrorStatusAndBody() {
        server.expect(requestTo("http://redmine.test/issues/123.json"))
                .andRespond(withStatus(FORBIDDEN).body("forbidden by role"));

        assertThatThrownBy(() -> client.updateIssue(123, fields(null, "x", null, null, null)))
                .isInstanceOf(RedmineMutationException.class)
                .hasMessageContaining("HTTP 403")
                .hasMessageContaining("forbidden by role");
    }

    private RedmineIssueMutation.Fields fields(String projectId, String subject, String description,
                                                String notes,
                                                List<RedmineIssueMutation.UploadReference> uploads) {
        return new RedmineIssueMutation.Fields(
                projectId, null, null, null, subject, description,
                null, null, null, null, null, null, null, null, null,
                null, notes, uploads);
    }
}

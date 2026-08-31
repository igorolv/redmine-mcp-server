package ru.it_spectrum.ai.redmine.mcp.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueMutation;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntryMutation;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPageMutation;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class RedmineMutationClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public RedmineMutationClient(RestClient redmineRestClient, ObjectMapper objectMapper) {
        this.restClient = redmineRestClient;
        this.objectMapper = objectMapper;
    }

    public RedmineIssue createIssue(RedmineIssueMutation.Fields fields) {
        byte[] body = jsonBody(new RedmineIssueMutation.Request(fields));
        var response = execute(() -> restClient.post()
                .uri("/issues.json")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body)
                .retrieve()
                .body(RedmineIssue.Single.class));
        return response != null ? response.issue() : null;
    }

    public RedmineTimeEntry createTimeEntry(RedmineTimeEntryMutation.Fields fields) {
        byte[] body = jsonBody(new RedmineTimeEntryMutation.Request(fields));
        var response = execute(() -> restClient.post()
                .uri("/time_entries.json")
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body)
                .retrieve()
                .body(RedmineTimeEntryMutation.CreateResponse.class));
        return response != null ? response.timeEntry() : null;
    }

    public void updateIssue(int issueId, RedmineIssueMutation.Fields fields) {
        byte[] body = jsonBody(new RedmineIssueMutation.Request(fields));
        execute(() -> restClient.put()
                .uri("/issues/{id}.json", issueId)
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public void putWikiPage(String projectId, String pageTitle, RedmineWikiPageMutation.Fields fields) {
        byte[] body = jsonBody(new RedmineWikiPageMutation.Request(fields));
        execute(() -> restClient.put()
                .uri("/projects/{projectId}/wiki/{pageTitle}.json", projectId, pageTitle)
                .contentType(MediaType.APPLICATION_JSON)
                .contentLength(body.length)
                .body(body)
                .retrieve()
                .toBodilessEntity());
    }

    public String upload(String filename, Path path) {
        var response = execute(() -> restClient.post()
                .uri(uriBuilder -> uriBuilder.path("/uploads.json")
                        .queryParam("filename", filename)
                        .build())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new FileSystemResource(path))
                .retrieve()
                .body(RedmineIssueMutation.UploadResponse.class));
        return response != null && response.upload() != null ? response.upload().token() : null;
    }

    private byte[] jsonBody(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize Redmine mutation request", e);
        }
    }

    private <T> T execute(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw new RedmineMutationException(
                    e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }
}

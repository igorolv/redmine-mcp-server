package ru.it_spectrum.ai.redmine.mcp.client;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueMutation;

import java.nio.file.Path;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class RedmineMutationClient {

    private final RestClient restClient;

    public RedmineMutationClient(RestClient redmineRestClient) {
        this.restClient = redmineRestClient;
    }

    public RedmineIssue createIssue(RedmineIssueMutation.Fields fields) {
        var response = execute(() -> restClient.post()
                .uri("/issues.json")
                .body(new RedmineIssueMutation.Request(fields))
                .retrieve()
                .body(RedmineIssue.Single.class));
        return response != null ? response.issue() : null;
    }

    public void updateIssue(int issueId, RedmineIssueMutation.Fields fields) {
        execute(() -> restClient.put()
                .uri("/issues/{id}.json", issueId)
                .body(new RedmineIssueMutation.Request(fields))
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

    private <T> T execute(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            throw new RedmineMutationException(
                    e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }
}

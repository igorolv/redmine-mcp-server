package ru.it_spectrum.ai.redmine.mcp.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class RedmineClientWikiTest {

    private MockRestServiceServer server;
    private RedmineClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://redmine.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RedmineClient(builder.build());
    }

    @Test
    void missingWikiPageReturnsNull() {
        server.expect(requestTo("http://redmine.test/projects/backend/wiki/Missing.json?include=attachments"))
                .andRespond(withStatus(NOT_FOUND));

        assertThat(client.getWikiPage("backend", "Missing")).isNull();
        server.verify();
    }
}

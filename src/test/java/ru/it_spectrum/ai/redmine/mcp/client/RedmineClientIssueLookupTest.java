package ru.it_spectrum.ai.redmine.mcp.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssueSummary;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RedmineClientIssueLookupTest {

    private MockRestServiceServer server;
    private RedmineClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://redmine.test");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RedmineClient(builder.build());
    }

    @Test
    void searchIssuesKeepsClosedIssuesInRelevanceOrder() {
        server.expect(requestTo(startsWith("http://redmine.test/search.json")))
                .andRespond(withSuccess("""
                        {"results":[
                          {"id":7,"title":"Closed match","type":"issue-closed"},
                          {"id":3,"title":"Open match","type":"issue"}
                        ],"total_count":2,"offset":0,"limit":25}
                        """, APPLICATION_JSON));
        server.expect(requestTo(startsWith("http://redmine.test/issues.json")))
                // The comma is sent percent-encoded; Redmine decodes it back into the ID list.
                .andExpect(queryParam("issue_id", "7%2C3"))
                .andExpect(queryParam("status_id", "*"))
                .andRespond(withSuccess(page(List.of(3, 7)), APPLICATION_JSON));

        var result = client.searchIssues("match", null, 0, 25);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.issues()).extracting(RedmineIssueSummary::id).containsExactly(7, 3);
        server.verify();
    }

    @Test
    void issueSummariesAreFetchedInBatchesOfOneHundred() {
        var ids = IntStream.rangeClosed(1, 150).boxed().toList();
        server.expect(requestTo(startsWith("http://redmine.test/issues.json")))
                .andExpect(queryParam("limit", "100"))
                .andRespond(withSuccess(page(ids.subList(0, 100)), APPLICATION_JSON));
        server.expect(requestTo(startsWith("http://redmine.test/issues.json")))
                .andExpect(queryParam("limit", "50"))
                .andRespond(withSuccess(page(ids.subList(100, 150)), APPLICATION_JSON));

        List<RedmineIssueSummary> summaries = client.getIssueSummariesByIds(ids);

        assertThat(summaries).hasSize(150);
        server.verify();
    }

    private static String page(List<Integer> ids) {
        var issues = ids.stream()
                .map(id -> """
                        {"id":%d,"subject":"Issue %d","done_ratio":0,"is_private":false}"""
                        .formatted(id, id))
                .toList();
        return "{\"issues\":[" + String.join(",", issues) + "],\"total_count\":" + issues.size()
                + ",\"offset\":0,\"limit\":" + issues.size() + "}";
    }
}

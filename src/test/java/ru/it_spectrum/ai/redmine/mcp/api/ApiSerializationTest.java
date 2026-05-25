package ru.it_spectrum.ai.redmine.mcp.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.util.json.JsonParser;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSerializationTest {

    @Test
    void issueMappingDropsNullListElementsAndApiPackageOmitsNullFields() throws Exception {
        var source = new RedmineIssue(
                100,
                new IdName(1, "project"),
                new IdName(2, "Task"),
                new IdName(3, "Open"),
                new IdName(4, "Normal"),
                new IdName(5, "Author"),
                null, null, null, null,
                "Subject", null,
                null, null, 0,
                null, null, false,
                "2026-01-01T00:00:00Z", "2026-01-02T00:00:00Z",
                Arrays.asList(null, new RedmineIssue.CustomField(10, "apps", true, List.of("rtk"))),
                Arrays.asList(null, new RedmineAttachment(11, "log.txt", 10, "text/plain",
                        "https://redmine.example.com/attachments/11", null, null, null)),
                Arrays.asList(null, new RedmineIssue.Journal(12, null, "note", null,
                        Arrays.asList(null, new RedmineIssue.Detail("attr", "status_id", null, "2")))),
                Arrays.asList(null, new RedmineIssue.Relation(13, 100, 101, "relates", null)),
                Arrays.asList(null, new RedmineIssue.Child(14, null, "child")),
                Arrays.asList(null, new RedmineIssue.Changeset("abc123", null, null, null))
        );

        var issue = Issue.from(source);
        var jacksonJson = new ObjectMapper().writeValueAsString(issue);
        var springAiJson = JsonParser.toJson(issue);

        assertThat(issue.customFields()).hasSize(1);
        assertThat(issue.attachments()).hasSize(1);
        assertThat(issue.journals()).hasSize(1);
        assertThat(issue.journals().getFirst().details()).hasSize(1);
        assertThat(issue.changesets()).hasSize(1);
        assertThat(jacksonJson).doesNotContain(":null");
        assertThat(jacksonJson).doesNotContain("[null");
        assertThat(springAiJson).doesNotContain(":null");
        assertThat(springAiJson).doesNotContain("[null");
    }
}

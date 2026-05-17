package ru.it_spectrum.ai.redmine.mcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncMcpPromptProvider;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentPromptsTest {

    private final IncidentPrompts prompts = new IncidentPrompts();

    @Test
    void incidentBriefIsAnnotatedAsMcpPrompt() throws Exception {
        Method m = IncidentPrompts.class.getMethod("incidentBrief", int.class);
        var annotation = m.getAnnotation(McpPrompt.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("incident-brief");
        assertThat(annotation.description()).isNotBlank();

        var arg = m.getParameters()[0].getAnnotation(McpArg.class);
        assertThat(arg).isNotNull();
        assertThat(arg.name()).isEqualTo("issueId");
        assertThat(arg.required()).isTrue();
    }

    @Test
    void investigateIncidentIsAnnotatedAsMcpPrompt() throws Exception {
        Method m = IncidentPrompts.class.getMethod("investigateIncident", int.class);
        var annotation = m.getAnnotation(McpPrompt.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.name()).isEqualTo("investigate-incident");
        assertThat(annotation.description()).isNotBlank();

        var arg = m.getParameters()[0].getAnnotation(McpArg.class);
        assertThat(arg).isNotNull();
        assertThat(arg.name()).isEqualTo("issueId");
        assertThat(arg.required()).isTrue();
    }

    @Test
    void incidentBriefSubstitutesIssueIdAndConstrainsToolUsage() {
        String text = prompts.incidentBrief(12345);
        assertThat(text).contains("12345");
        assertThat(text).contains("getIssue");
        assertThat(text).contains("getAttachment");
        assertThat(text).contains("maxChars=300");
        assertThat(text).contains("partLimit=300");
        // Brief must explicitly cap previews to the first 10 of >15 attachments.
        assertThat(text).contains("15");
        assertThat(text).contains("first 10");
        // Brief must NOT mention heavier tools — that's what investigate-incident is for.
        assertThat(text).doesNotContain("getIssueFullContext");
        assertThat(text).doesNotContain("getBlockerChain");
    }

    @Test
    void investigateIncidentSubstitutesIssueIdAndOrchestratesExpectedTools() {
        String text = prompts.investigateIncident(7777);
        assertThat(text).contains("7777");
        assertThat(text).contains("getIssueFullContext");
        assertThat(text).contains("getAttachment");
        assertThat(text).contains("getBlockerChain");
        assertThat(text).contains("getIssueTree");
        // Must steer away from redundant getIssue calls (getIssueFullContext covers it).
        assertThat(text).contains("Do NOT call getIssue separately");
        // Must demand a structured report with the named sections.
        assertThat(text).contains("### What happened");
        assertThat(text).contains("### Timeline");
        assertThat(text).contains("### Hypotheses");
        assertThat(text).contains("### Open questions");
    }

    @Test
    void syncProviderDiscoversBothPrompts() {
        var provider = new SyncMcpPromptProvider(List.of(prompts));
        var specs = provider.getPromptSpecifications();
        var names = specs.stream().map(s -> s.prompt().name()).toList();
        assertThat(names).containsExactlyInAnyOrder("incident-brief", "investigate-incident");
    }
}

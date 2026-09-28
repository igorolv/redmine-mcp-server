package ru.it_spectrum.ai.redmine.mcp.tools;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema.GetPromptRequest;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.provider.prompt.SyncMcpPromptProvider;
import ru.it_spectrum.ai.redmine.mcp.focus.ResponseFocus;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncidentPromptsTest {

    private static final List<String> PROMPT_NAMES = List.of(
            "incident-brief", "incident-implementation", "incident-timeline", "issue-remaining-work");

    private static final List<Class<?>> TOOL_CLASSES = List.of(
            IssueTools.class, IssueStructureTools.class, ProjectTools.class, SearchTools.class,
            AttachmentTools.class, WikiTools.class, TimeEntryTools.class, ReferenceDataTools.class,
            UserTools.class, IssueAnalyticsTools.class, ReleaseAnalyticsTools.class,
            IssueWriteTools.class, TimeEntryWriteTools.class, WikiWriteTools.class);

    private static final Pattern TOOL_CALL = Pattern.compile("\\b(\\w+)\\(([^()]*)\\)");
    private static final Pattern NAMED_ARG = Pattern.compile("\\b(\\w+)=");
    private static final Pattern TOOL_LIKE_NAME = Pattern.compile("\\b(?:get|list|search|create|update|add|attach)[A-Z]\\w*");
    private static final Pattern FOCUS_VALUE = Pattern.compile("focus=\"(\\w+)\"");

    private final IncidentPrompts prompts = new IncidentPrompts();

    private final Map<String, Function<String, String>> renderers = Map.of(
            "incident-brief", prompts::incidentBrief,
            "incident-implementation", prompts::incidentImplementation,
            "incident-timeline", prompts::incidentTimeline,
            "issue-remaining-work", prompts::issueRemainingWork);

    @Test
    void everyPromptIsAnnotatedWithRequiredStringIssueId() {
        var found = Arrays.stream(IncidentPrompts.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(McpPrompt.class))
                .toList();
        assertThat(found).extracting(m -> m.getAnnotation(McpPrompt.class).name())
                .containsExactlyInAnyOrderElementsOf(PROMPT_NAMES);

        for (Method m : found) {
            assertThat(m.getAnnotation(McpPrompt.class).description()).isNotBlank();
            assertThat(m.getParameterTypes()).containsExactly(String.class);
            var arg = m.getParameters()[0].getAnnotation(McpArg.class);
            assertThat(arg.name()).isEqualTo("issueId");
            assertThat(arg.required()).isTrue();
        }
    }

    @Test
    void syncProviderDiscoversPrompts() {
        var specs = new SyncMcpPromptProvider(List.of(prompts)).getPromptSpecifications();
        assertThat(specs).extracting(s -> s.prompt().name())
                .containsExactlyInAnyOrderElementsOf(PROMPT_NAMES);
    }

    @ParameterizedTest
    @ValueSource(strings = {"$1", "$ARGUMENTS"})
    void promptsGetWithClientPlaceholderReturnsTemplateWithPlaceholder(String placeholder) {
        for (String name : PROMPT_NAMES) {
            String text = invokeViaProvider(name, placeholder);
            assertThat(text).as(name).contains("#" + placeholder);
        }
    }

    @Test
    void promptsGetWithIssueNumberSubstitutesIt() {
        for (String name : PROMPT_NAMES) {
            assertThat(invokeViaProvider(name, "12345")).as(name)
                    .contains("#12345")
                    .contains("issueId=12345");
        }
    }

    @Test
    void promptsGetWithGarbageFailsWithClearMessage() {
        var spec = specification("incident-brief");
        var request = new GetPromptRequest("incident-brief", Map.of("issueId", "abc"));
        assertThatThrownBy(() -> spec.promptHandler().apply(null, request))
                .isInstanceOf(McpError.class)
                .hasMessageContaining("issueId must be a Redmine issue number");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345", " 12345 ", "#12345"})
    void issueNumberIsNormalized(String input) {
        assertThat(IncidentPrompts.issueIdForTemplate(input)).isEqualTo("12345");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  ", "abc", "12a", "0", "-5", "##1", "$", "$1 extra", "99999999999"})
    void invalidIssueIdIsRejected(String input) {
        assertThatThrownBy(() -> IncidentPrompts.issueIdForTemplate(input))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("issueId must be a Redmine issue number");
    }

    @Test
    void promptTextsReferenceOnlyExistingToolsParametersAndFocusValues() {
        Map<String, Set<String>> tools = toolParameters();

        renderers.forEach((name, render) -> {
            String text = render.apply("12345");

            var toolLike = TOOL_LIKE_NAME.matcher(text);
            while (toolLike.find()) {
                assertThat(tools).as("%s mentions unknown tool", name).containsKey(toolLike.group());
            }

            var call = TOOL_CALL.matcher(text);
            while (call.find()) {
                var params = tools.get(call.group(1));
                if (params == null) {
                    continue;
                }
                var arg = NAMED_ARG.matcher(call.group(2));
                while (arg.find()) {
                    assertThat(params).as("%s: %s parameter", name, call.group(1)).contains(arg.group(1));
                }
            }

            var focus = FOCUS_VALUE.matcher(text);
            while (focus.find()) {
                ResponseFocus.from(focus.group(1));
            }
        });
    }

    @Test
    void attachmentCallsNoLongerPassFocus() {
        renderers.forEach((name, render) ->
                assertThat(render.apply("12345")).as(name).doesNotContainPattern("getAttachment\\([^)]*focus"));
    }

    @Test
    void incidentBriefKeepsShortPreviews() {
        String text = prompts.incidentBrief("12345");
        assertThat(text).contains("getAttachment(issueId=12345, attachmentId=<attachment.id>, maxChars=300, partLimit=300)");
        assertThat(text).contains("first 10");
    }

    @Test
    void timelineUsesHistoryForOmittedJournalData() {
        String text = prompts.incidentTimeline("12345");
        assertThat(text).contains("focus=\"timeline\"");
        assertThat(text).contains("getIssueHistory(issueId=12345)");
        assertThat(text).contains("kept N most recent of M journal entries");
        assertThat(text).contains("Do not call getAttachment");
    }

    @Test
    void implementationReadsAttachmentsWithinBudget() {
        String text = prompts.incidentImplementation("12345");
        assertThat(text).contains("focus=\"implementation\"");
        assertThat(text).contains("maxChars=3000, partLimit=3000");
        assertThat(text).contains("localPath");
        assertThat(text).doesNotContain("Do not call getIssueJournal");
    }

    @Test
    void remainingWorkWalksTreeAndBlockers() {
        String text = prompts.issueRemainingWork("12345");
        assertThat(text).contains("getIssueTree(issueId=12345)");
        assertThat(text).contains("getBlockerChain(issueId=12345)");
        assertThat(text).contains("Do not trust status alone");
    }

    private String invokeViaProvider(String promptName, String issueId) {
        var arguments = new HashMap<String, Object>();
        arguments.put("issueId", issueId);
        GetPromptResult result = specification(promptName).promptHandler()
                .apply(null, new GetPromptRequest(promptName, arguments));
        return result.messages().stream()
                .map(m -> ((TextContent) m.content()).text())
                .collect(Collectors.joining("\n"));
    }

    private io.modelcontextprotocol.server.McpServerFeatures.SyncPromptSpecification specification(String promptName) {
        return new SyncMcpPromptProvider(List.of(prompts)).getPromptSpecifications().stream()
                .filter(s -> s.prompt().name().equals(promptName))
                .findFirst()
                .orElseThrow();
    }

    private static Map<String, Set<String>> toolParameters() {
        var result = new HashMap<String, Set<String>>();
        for (Class<?> type : TOOL_CLASSES) {
            for (Method m : type.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(McpTool.class)) {
                    continue;
                }
                var names = Arrays.stream(m.getParameters()).map(Parameter::getName).collect(Collectors.toSet());
                assertThat(Arrays.stream(m.getParameters()).allMatch(Parameter::isNamePresent))
                        .as("compiled with -parameters: %s", m).isTrue();
                result.put(m.getName(), names);
            }
        }
        return result;
    }
}

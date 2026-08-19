package ru.it_spectrum.ai.redmine.mcp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.WikiMutationResult;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationClient;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineMutationException;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineWikiPageMutation;

@Service
@ConditionalOnProperty(prefix = "redmine-mcp.write", name = "enabled", havingValue = "true")
public class WikiMutationService {

    private static final Logger log = LoggerFactory.getLogger(WikiMutationService.class);

    private final RedmineMutationClient mutationClient;
    private final RedmineClient client;
    private final AiContentMarker marker;

    public WikiMutationService(RedmineMutationClient mutationClient,
                               RedmineClient client,
                               AiContentMarker marker) {
        this.mutationClient = mutationClient;
        this.client = client;
        this.marker = marker;
    }

    public WikiMutationResult createPage(String projectId, String pageTitle, String text,
                                         String parentTitle, String comments) {
        requireText(projectId, "projectId");
        requireText(pageTitle, "pageTitle");
        requireText(text, "text");

        if (client.getWikiPage(projectId, pageTitle) != null) {
            throw new WikiPageAlreadyExistsException(projectId, pageTitle);
        }

        mutationClient.putWikiPage(projectId, pageTitle,
                new RedmineWikiPageMutation.Fields(text, marker.markText(comments), parentTitle, null));
        return refreshedResult(projectId, pageTitle, 1);
    }

    public WikiMutationResult updatePage(String projectId, String pageTitle, String text,
                                         int version, String comments) {
        requireText(projectId, "projectId");
        requireText(pageTitle, "pageTitle");
        requireText(text, "text");
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }

        var current = client.getWikiPage(projectId, pageTitle);
        if (current == null) {
            throw new ResourceNotFoundException("wiki page", pageTitle);
        }
        if (current.version() != version) {
            throw new WikiVersionConflictException(pageTitle, version);
        }
        if (text.equals(current.text())) {
            return new WikiMutationResult(projectId, pageTitle, current.version());
        }

        try {
            mutationClient.putWikiPage(projectId, pageTitle,
                    new RedmineWikiPageMutation.Fields(text, marker.markText(comments), null, version));
        } catch (RedmineMutationException e) {
            if (e.statusCode() == 409) {
                throw new WikiVersionConflictException(pageTitle, version);
            }
            throw e;
        }
        return refreshedResult(projectId, pageTitle, version + 1);
    }

    private WikiMutationResult refreshedResult(String projectId, String pageTitle, int fallbackVersion) {
        try {
            var refreshed = client.getWikiPage(projectId, pageTitle);
            if (refreshed != null) {
                return new WikiMutationResult(projectId, pageTitle, refreshed.version());
            }
            log.warn("Redmine write succeeded but wiki page {} in project {} could not be refreshed",
                    pageTitle, projectId);
        } catch (RuntimeException e) {
            log.warn("Redmine write succeeded but wiki page {} in project {} could not be refreshed: {}",
                    pageTitle, projectId, e.getMessage());
        }
        return new WikiMutationResult(projectId, pageTitle, fallbackVersion);
    }

    private void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

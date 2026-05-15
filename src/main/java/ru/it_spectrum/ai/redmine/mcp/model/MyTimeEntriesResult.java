package ru.it_spectrum.ai.redmine.mcp.model;

import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineTimeEntry;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineUser;

public record MyTimeEntriesResult(
        RedmineUser user,
        RedmineTimeEntry.Page page
) {
}

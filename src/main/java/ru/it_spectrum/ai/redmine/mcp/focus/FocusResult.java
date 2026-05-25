package ru.it_spectrum.ai.redmine.mcp.focus;

import java.util.List;

public record FocusResult<T>(T value, List<String> notes) {
}

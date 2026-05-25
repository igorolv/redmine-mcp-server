package ru.it_spectrum.ai.redmine.mcp.focus;

import java.util.ArrayList;
import java.util.List;

final class FocusSupport {

    private FocusSupport() {
    }

    static <T> FocusResult<T> applySteps(T value, List<? extends FocusStep<T>> steps) {
        T current = value;
        var notes = new ArrayList<String>();
        for (var step : steps) {
            var maybe = step.apply(current);
            if (maybe.isEmpty()) {
                continue;
            }
            current = maybe.get().value();
            notes.add(maybe.get().note());
        }
        return new FocusResult<>(current, List.copyOf(notes));
    }
}

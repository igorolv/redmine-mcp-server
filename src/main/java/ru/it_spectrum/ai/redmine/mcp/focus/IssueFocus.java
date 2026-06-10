package ru.it_spectrum.ai.redmine.mcp.focus;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.Issue;
import ru.it_spectrum.ai.redmine.mcp.focus.steps.ChangesetsRevisionOnlyFocusStep;
import ru.it_spectrum.ai.redmine.mcp.focus.steps.IssueChangesetsFocusStep;
import ru.it_spectrum.ai.redmine.mcp.focus.steps.IssueTimelineFocusStep;
import ru.it_spectrum.ai.redmine.mcp.focus.steps.JournalsImplementationFocusStep;

import java.util.List;

@Service
public class IssueFocus {

    public Issue apply(Issue issue, ResponseFocus focus) {
        if (issue == null) {
            return null;
        }
        var actualFocus = focus != null ? focus : ResponseFocus.DEFAULT;
        var result = FocusSupport.applySteps(issue, buildSteps(actualFocus));
        if (result.notes().isEmpty()) {
            return result.value();
        }
        return result.value().withFocusNotes(result.notes());
    }

    List<FocusStep<Issue>> buildSteps(ResponseFocus focus) {
        return switch (focus) {
            case IMPLEMENTATION -> List.of(
                    new ChangesetsRevisionOnlyFocusStep(),
                    new JournalsImplementationFocusStep()
            );
            case TIMELINE -> List.of(new IssueTimelineFocusStep());
            case CHANGESETS -> List.of(new IssueChangesetsFocusStep());
            case DEFAULT, FULL -> List.of();
        };
    }
}

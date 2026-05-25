package ru.it_spectrum.ai.redmine.mcp.focus;

import org.springframework.stereotype.Service;
import ru.it_spectrum.ai.redmine.mcp.api.AttachmentContent;

@Service
public class AttachmentContentFocus {

    public AttachmentContent apply(AttachmentContent content, ResponseFocus focus) {
        return content;
    }
}

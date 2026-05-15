package ru.it_spectrum.ai.redmine.mcp.service;

public class AttachmentNotFoundException extends AttachmentServiceException {

    public AttachmentNotFoundException(int attachmentId) {
        super(attachmentId, "Attachment #%d not found".formatted(attachmentId));
    }
}

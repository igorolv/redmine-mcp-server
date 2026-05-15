package ru.it_spectrum.ai.redmine.mcp.service;

public class NotAnImageAttachmentException extends AttachmentServiceException {

    private final String filename;

    public NotAnImageAttachmentException(int attachmentId, String filename) {
        super(attachmentId, "Attachment #%d (%s) is not an image".formatted(attachmentId, filename));
        this.filename = filename;
    }

    public String filename() {
        return filename;
    }
}

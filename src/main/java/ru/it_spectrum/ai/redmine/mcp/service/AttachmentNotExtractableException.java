package ru.it_spectrum.ai.redmine.mcp.service;

public class AttachmentNotExtractableException extends AttachmentServiceException {

    private final String filename;

    public AttachmentNotExtractableException(int attachmentId, String filename) {
        super(attachmentId, "Attachment #%d (%s) is not text-extractable".formatted(attachmentId, filename));
        this.filename = filename;
    }

    public String filename() {
        return filename;
    }
}

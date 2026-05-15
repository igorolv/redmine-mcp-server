package ru.it_spectrum.ai.redmine.mcp.service;

public class AttachmentDownloadFailedException extends AttachmentServiceException {

    public AttachmentDownloadFailedException(int attachmentId) {
        super(attachmentId, "Failed to download attachment #%d".formatted(attachmentId));
    }
}

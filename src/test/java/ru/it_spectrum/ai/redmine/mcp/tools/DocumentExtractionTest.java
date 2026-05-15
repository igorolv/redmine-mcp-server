package ru.it_spectrum.ai.redmine.mcp.tools;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import ru.it_spectrum.ai.redmine.mcp.client.AttachmentTextCache;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextChunk;
import ru.it_spectrum.ai.redmine.mcp.model.AttachmentTextInfo;
import ru.it_spectrum.ai.redmine.mcp.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.service.chunking.FixedSizeTextChunker;

import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExtractionTest {

    @Mock
    private RedmineClient client;

    private AttachmentTools tools;

    @BeforeEach
    void setUp() {
        tools = new AttachmentTools(client, new DocumentTextExtractor(client, new AttachmentTextCache()), new FixedSizeTextChunker());
    }

    // --- PDF ---

    @Test
    void shouldExtractTextFromPdf() throws Exception {
        byte[] pdfBytes = generatePdf("Hello from PDF document");
        var attachment = attachment(1, "report.pdf", "application/pdf", pdfBytes.length);

        when(client.getAttachment(1)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(pdfBytes);

        String result = tools.getAttachmentContent(1);

        assertThat(result).contains("report.pdf");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("Hello from PDF document");
    }

    @Test
    void shouldDetectPdfByExtensionEvenWithGenericContentType() throws Exception {
        byte[] pdfBytes = generatePdf("Extension-detected PDF");
        var attachment = attachment(2, "file.pdf", "application/octet-stream", pdfBytes.length);

        when(client.getAttachment(2)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(pdfBytes);

        String result = tools.getAttachmentContent(2);

        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("Extension-detected PDF");
    }

    // --- DOCX ---

    @Test
    void shouldExtractTextFromDocx() throws Exception {
        byte[] docxBytes = generateDocx("Hello from Word document", "Second paragraph");
        var attachment = attachment(3, "document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);

        when(client.getAttachment(3)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        String result = tools.getAttachmentContent(3);

        assertThat(result).contains("document.docx");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("Hello from Word document");
        assertThat(result).contains("Second paragraph");
    }

    @Test
    void shouldExtractTableFromDocx() throws Exception {
        byte[] docxBytes = generateDocxWithTable(
                new String[][]{{"Name", "Age"}, {"Alice", "30"}, {"Bob", "25"}});
        var attachment = attachment(4, "table.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);

        when(client.getAttachment(4)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        String result = tools.getAttachmentContent(4);

        assertThat(result).contains("[Table]");
        assertThat(result).contains("Name | Age");
        assertThat(result).contains("Alice | 30");
        assertThat(result).contains("Bob | 25");
    }

    @Test
    void shouldReturnAttachmentTextInfoForDocx() throws Exception {
        byte[] docxBytes = generateDocx("Hello from Word document", "Second paragraph");
        var attachment = attachment(13, "document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);

        when(client.getAttachment(13)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        AttachmentTextInfo info = tools.getAttachmentTextInfo(13);

        assertThat(info.attachmentId()).isEqualTo(13);
        assertThat(info.filename()).isEqualTo("document.docx");
        assertThat(info.extractable()).isTrue();
        assertThat(info.extractionType()).isEqualTo("docx");
        assertThat(info.totalChars()).isGreaterThan(10);
        assertThat(info.chunkCount()).isEqualTo(1);
        assertThat(info.previewTruncated()).isFalse();
    }

    @Test
    void shouldReturnChunkedAttachmentTextForLargeDocx() throws Exception {
        byte[] docxBytes = generateDocx(generateParagraphs("Paragraph", 40, 800));
        var attachment = attachment(14, "large.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);

        when(client.getAttachment(14)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        AttachmentTextInfo info = tools.getAttachmentTextInfo(14);
        AttachmentTextChunk chunk0 = tools.getAttachmentTextChunk(14, 0, 4_000);
        AttachmentTextChunk chunk1 = tools.getAttachmentTextChunk(14, 1, 4_000);

        assertThat(info.chunkCount()).isGreaterThan(1);
        assertThat(chunk0.chunkIndex()).isEqualTo(0);
        assertThat(chunk0.chunkCount()).isGreaterThan(1);
        assertThat(chunk0.text()).contains("Paragraph 1:");
        assertThat(chunk1.chunkIndex()).isEqualTo(1);
        assertThat(chunk1.chunkCount()).isEqualTo(chunk0.chunkCount());
        assertThat(chunk1.startChar()).isLessThan(chunk1.endChar());
    }

    @Test
    void shouldRejectOutOfRangeChunkIndex() throws Exception {
        byte[] docxBytes = generateDocx("Short doc");
        var attachment = attachment(15, "small.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);

        when(client.getAttachment(15)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        assertThatThrownBy(() -> tools.getAttachmentTextChunk(15, 5, 4_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("out of range");
    }

    // --- XLSX ---

    @Test
    void shouldExtractTextFromXlsx() throws Exception {
        byte[] xlsxBytes = generateXlsx("Sheet1",
                new String[][]{{"Product", "Price"}, {"Widget", "9.99"}, {"Gadget", "19.99"}});
        var attachment = attachment(5, "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes.length);

        when(client.getAttachment(5)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(xlsxBytes);

        String result = tools.getAttachmentContent(5);

        assertThat(result).contains("data.xlsx");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("Sheet: Sheet1");
        assertThat(result).contains("Product | Price");
        assertThat(result).contains("Widget | 9.99");
    }

    @Test
    void shouldExtractMultipleSheets() throws Exception {
        byte[] xlsxBytes = generateXlsxMultiSheet();
        var attachment = attachment(6, "multi.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes.length);

        when(client.getAttachment(6)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(xlsxBytes);

        String result = tools.getAttachmentContent(6);

        assertThat(result).contains("Sheet: Sales");
        assertThat(result).contains("Sheet: Expenses");
        assertThat(result).contains("Revenue | 1000");
        assertThat(result).contains("Rent | 500");
    }

    // --- PPTX ---

    @Test
    void shouldExtractTextFromPptx() throws Exception {
        byte[] pptxBytes = generatePptx("Title Slide", "Bullet point content");
        var attachment = attachment(7, "presentation.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation", pptxBytes.length);

        when(client.getAttachment(7)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(pptxBytes);

        String result = tools.getAttachmentContent(7);

        assertThat(result).contains("presentation.pptx");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("Slide 1");
        assertThat(result).contains("Title Slide");
        assertThat(result).contains("Bullet point content");
    }

    // --- Text files ---

    @Test
    void shouldReturnTextFileContent() {
        byte[] textBytes = "log line 1\nlog line 2\n".getBytes();
        var attachment = attachment(8, "app.log", "text/plain", textBytes.length);

        when(client.getAttachment(8)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(textBytes);

        String result = tools.getAttachmentContent(8);

        assertThat(result).contains("app.log");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("log line 1");
        assertThat(result).contains("log line 2");
    }

    @Test
    void shouldReturnJsonFileContent() {
        byte[] jsonBytes = "{\"key\": \"value\"}".getBytes();
        var attachment = attachment(9, "config.json", "application/json", jsonBytes.length);

        when(client.getAttachment(9)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(jsonBytes);

        String result = tools.getAttachmentContent(9);

        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("{\"key\": \"value\"}");
    }

    @Test
    void shouldReturnXsdFileContentWithGenericContentType() {
        byte[] xsdBytes = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="order" type="xs:string"/>
                </xs:schema>
                """.getBytes();
        var attachment = attachment(16, "schema.xsd", "application/octet-stream", xsdBytes.length);

        when(client.getAttachment(16)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(xsdBytes);

        String result = tools.getAttachmentContent(16);

        assertThat(result).contains("schema.xsd");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("<xs:element name=\"order\" type=\"xs:string\"/>");
    }

    @Test
    void shouldExtractSupportedFilesFromZipWithGenericContentType() throws Exception {
        byte[] docxBytes = generateDocx("Architecture decision from Word document");
        byte[] zipBytes = generateZip(Map.of(
                "config/service.yaml", "service: billing\nfeature: archive extraction".getBytes(),
                "schema/order.xsd", """
                        <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                            <xs:element name="orderId" type="xs:string"/>
                        </xs:schema>
                        """.getBytes(),
                "docs/decision.docx", docxBytes,
                "images/screenshot.png", new byte[]{1, 2, 3}
        ));
        var attachment = attachment(17, "bundle.zip", "application/octet-stream", zipBytes.length);

        when(client.getAttachment(17)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(zipBytes);

        String result = tools.getAttachmentContent(17);

        assertThat(result).contains("bundle.zip");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("ZIP archive: bundle.zip");
        assertThat(result).contains("config/service.yaml (extracted");
        assertThat(result).contains("schema/order.xsd (extracted");
        assertThat(result).contains("docs/decision.docx (extracted");
        assertThat(result).contains("images/screenshot.png (skipped, not text-extractable");
        assertThat(result).contains("--- config/service.yaml ---");
        assertThat(result).contains("feature: archive extraction");
        assertThat(result).contains("--- schema/order.xsd ---");
        assertThat(result).contains("<xs:element name=\"orderId\" type=\"xs:string\"/>");
        assertThat(result).contains("--- docs/decision.docx ---");
        assertThat(result).contains("Architecture decision from Word document");
    }

    @Test
    void shouldReturnAttachmentTextInfoForZip() throws Exception {
        byte[] zipBytes = generateZip(Map.of(
                "config/service.yaml", "service: billing\n".getBytes()
        ));
        var attachment = attachment(18, "bundle.zip", "application/zip", zipBytes.length);

        when(client.getAttachment(18)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(zipBytes);

        AttachmentTextInfo info = tools.getAttachmentTextInfo(18);

        assertThat(info.attachmentId()).isEqualTo(18);
        assertThat(info.filename()).isEqualTo("bundle.zip");
        assertThat(info.extractable()).isTrue();
        assertThat(info.extractionType()).isEqualTo("zip");
        assertThat(info.totalChars()).isGreaterThan(10);
    }

    // --- Binary files ---

    @Test
    void shouldReturnMetadataForBinaryFile() {
        var attachment = attachment(10, "photo.png", "image/png", 50_000);

        when(client.getAttachment(10)).thenReturn(attachment);

        String result = tools.getAttachmentContent(10);

        assertThat(result).contains("photo.png");
        assertThat(result).contains("Image file");
        assertThat(result).contains("getImageAttachment");
        assertThat(result).doesNotContain("--- Content ---");
    }

    // --- Truncation ---

    @Test
    void shouldTruncateLargeTextContent() {
        String longText = "x".repeat(60_000);
        byte[] textBytes = longText.getBytes();
        var attachment = attachment(11, "huge.txt", "text/plain", textBytes.length);

        when(client.getAttachment(11)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(textBytes);

        String result = tools.getAttachmentContent(11);

        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("truncated");
        // Total output should be limited — the extracted text part must be <= 50000 + truncation message
        String contentPart = result.substring(result.indexOf("--- Content ---") + "--- Content ---\n".length());
        assertThat(contentPart.length()).isLessThan(60_000);
    }

    // --- Attachment not found ---

    @Test
    void shouldHandleAttachmentNotFound() {
        when(client.getAttachment(999)).thenReturn(null);

        String result = tools.getAttachmentContent(999);

        assertThat(result).contains("not found");
    }

    @Test
    void shouldReportProgressForAttachmentContent() throws Exception {
        byte[] pdfBytes = generatePdf("Hello from PDF document");
        var attachment = attachment(101, "report.pdf", "application/pdf", pdfBytes.length);
        var context = progressContext("token");

        when(client.getAttachment(101)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(pdfBytes);

        tools.getAttachmentContent(101, context);

        verify(context, atLeastOnce()).progress(any(java.util.function.Consumer.class));
    }

    @Test
    void shouldReportProgressForAttachmentTextInfo() throws Exception {
        byte[] docxBytes = generateDocx("Hello from Word document", "Second paragraph");
        var attachment = attachment(102, "document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);
        var context = progressContext("token");

        when(client.getAttachment(102)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        tools.getAttachmentTextInfo(102, context);

        verify(context, atLeastOnce()).progress(any(java.util.function.Consumer.class));
    }

    @Test
    void shouldReportProgressForAttachmentTextChunk() throws Exception {
        byte[] docxBytes = generateDocx(generateParagraphs("Paragraph", 10, 600));
        var attachment = attachment(103, "large.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);
        var context = progressContext("token");

        when(client.getAttachment(103)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        tools.getAttachmentTextChunk(103, 0, 4_000, context);

        verify(context, atLeastOnce()).progress(any(java.util.function.Consumer.class));
    }

    // --- Corrupt document ---

    @Test
    void shouldHandleCorruptDocument() {
        byte[] garbage = "this is not a valid pdf".getBytes();
        var attachment = attachment(12, "corrupt.pdf", "application/pdf", garbage.length);

        when(client.getAttachment(12)).thenReturn(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(garbage);

        String result = tools.getAttachmentContent(12);

        assertThat(result).contains("corrupt.pdf");
        assertThat(result).contains("--- Content ---");
        assertThat(result).contains("failed to extract text");
    }

    // ===== Document generators =====

    private static byte[] generatePdf(String text) throws Exception {
        try (var doc = new PDDocument()) {
            var page = new PDPage();
            doc.addPage(page);
            try (var cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(50, 700);
                cs.showText(text);
                cs.endText();
            }
            var baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] generateDocx(String... paragraphs) throws Exception {
        try (var doc = new XWPFDocument()) {
            for (String text : paragraphs) {
                doc.createParagraph().createRun().setText(text);
            }
            var baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private static String[] generateParagraphs(String prefix, int count, int repeatCount) {
        var paragraphs = new String[count];
        String repeated = "x".repeat(repeatCount);
        for (int i = 0; i < count; i++) {
            paragraphs[i] = "%s %d: %s".formatted(prefix, i + 1, repeated);
        }
        return paragraphs;
    }

    private static byte[] generateDocxWithTable(String[][] data) throws Exception {
        try (var doc = new XWPFDocument()) {
            var table = doc.createTable(data.length, data[0].length);
            for (int r = 0; r < data.length; r++) {
                for (int c = 0; c < data[r].length; c++) {
                    table.getRow(r).getCell(c).setText(data[r][c]);
                }
            }
            var baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] generateXlsx(String sheetName, String[][] data) throws Exception {
        try (var wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet(sheetName);
            for (int r = 0; r < data.length; r++) {
                var row = sheet.createRow(r);
                for (int c = 0; c < data[r].length; c++) {
                    row.createCell(c).setCellValue(data[r][c]);
                }
            }
            var baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] generateXlsxMultiSheet() throws Exception {
        try (var wb = new XSSFWorkbook()) {
            var sales = wb.createSheet("Sales");
            sales.createRow(0).createCell(0).setCellValue("Revenue");
            sales.getRow(0).createCell(1).setCellValue("1000");

            var expenses = wb.createSheet("Expenses");
            expenses.createRow(0).createCell(0).setCellValue("Rent");
            expenses.getRow(0).createCell(1).setCellValue("500");

            var baos = new ByteArrayOutputStream();
            wb.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] generatePptx(String... slideTexts) throws Exception {
        try (var pptx = new XMLSlideShow()) {
            var layout = pptx.getSlideMasters().getFirst().getSlideLayouts()[0];
            var slide = pptx.createSlide(layout);
            // Add text to existing shapes or create a text box
            int shapeIdx = 0;
            for (String text : slideTexts) {
                boolean written = false;
                if (shapeIdx < slide.getShapes().size()
                        && slide.getShapes().get(shapeIdx) instanceof XSLFTextShape ts) {
                    ts.setText(text);
                    written = true;
                    shapeIdx++;
                }
                if (!written) {
                    var textBox = slide.createTextBox();
                    textBox.setText(text);
                    textBox.setAnchor(new java.awt.Rectangle(50, 50 + shapeIdx * 100, 400, 80));
                    shapeIdx++;
                }
            }
            var baos = new ByteArrayOutputStream();
            pptx.write(baos);
            return baos.toByteArray();
        }
    }

    private static byte[] generateZip(Map<String, byte[]> entries) throws Exception {
        var baos = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(baos)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static RedmineAttachment attachment(int id, String filename, String contentType, long size) {
        return new RedmineAttachment(
                id, filename, size, contentType,
                "http://redmine.example.com/attachments/download/" + id + "/" + filename,
                null,
                new IdName(1, "Test User"),
                "2025-01-01T00:00:00Z"
        );
    }

    private static McpSyncRequestContext progressContext(Object token) {
        var context = mock(McpSyncRequestContext.class);
        var request = io.modelcontextprotocol.spec.McpSchema.CallToolRequest.builder()
                .name("attachmentTool")
                .arguments(Map.of())
                .progressToken(token)
                .build();
        when(context.request()).thenReturn(request);
        return context;
    }
}

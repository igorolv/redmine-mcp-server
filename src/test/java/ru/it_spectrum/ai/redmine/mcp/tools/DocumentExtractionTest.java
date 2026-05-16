package ru.it_spectrum.ai.redmine.mcp.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.it_spectrum.ai.redmine.mcp.client.DocumentTextExtractor;
import ru.it_spectrum.ai.redmine.mcp.client.RedmineClient;
import ru.it_spectrum.ai.redmine.mcp.client.model.IdName;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineAttachment;
import ru.it_spectrum.ai.redmine.mcp.client.model.RedmineIssue;
import ru.it_spectrum.ai.redmine.mcp.config.RedmineMcpProperties;
import ru.it_spectrum.ai.redmine.mcp.service.IssueSnapshotService;
import ru.it_spectrum.ai.redmine.mcp.service.AttachmentService;

import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentExtractionTest {

    private static final int ISSUE_ID = 100;

    @Mock
    private RedmineClient client;

    @TempDir
    private Path dataDir;

    private AttachmentTools tools;

    @BeforeEach
    void setUp() {
        var snapshot = new IssueSnapshotService(client, new ObjectMapper(), new RedmineMcpProperties(dataDir.toString()));
        var service = new AttachmentService(client,
                new DocumentTextExtractor(), snapshot);
        tools = new AttachmentTools(service, ToolJsonTestSupport.json(), ToolJsonTestSupport.errors());
    }

    // --- PDF ---

    @Test
    void shouldExtractTextFromPdf() throws Exception {
        byte[] pdfBytes = generatePdf("Hello from PDF document");
        var attachment = attachment(1, "report.pdf", "application/pdf", pdfBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(pdfBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 1);

        assertThat(result).contains("report.pdf");
        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("Hello from PDF document");
    }

    @Test
    void shouldDetectPdfByExtensionEvenWithGenericContentType() throws Exception {
        byte[] pdfBytes = generatePdf("Extension-detected PDF");
        var attachment = attachment(2, "file.pdf", "application/octet-stream", pdfBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(pdfBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 2);

        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("Extension-detected PDF");
    }

    // --- DOCX ---

    @Test
    void shouldExtractTextFromDocx() throws Exception {
        byte[] docxBytes = generateDocx("Hello from Word document", "Second paragraph");
        var attachment = attachment(3, "document.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 3);

        assertThat(result).contains("document.docx");
        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("Hello from Word document");
        assertThat(result).contains("Second paragraph");
    }

    @Test
    void shouldExtractTableFromDocx() throws Exception {
        byte[] docxBytes = generateDocxWithTable(
                new String[][]{{"Name", "Age"}, {"Alice", "30"}, {"Bob", "25"}});
        var attachment = attachment(4, "table.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(docxBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 4);

        assertThat(result).contains("[Table]");
        assertThat(result).contains("Name | Age");
        assertThat(result).contains("Alice | 30");
        assertThat(result).contains("Bob | 25");
    }

    // --- XLSX ---

    @Test
    void shouldExtractTextFromXlsx() throws Exception {
        byte[] xlsxBytes = generateXlsx("Sheet1",
                new String[][]{{"Product", "Price"}, {"Widget", "9.99"}, {"Gadget", "19.99"}});
        var attachment = attachment(5, "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(xlsxBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 5);

        assertThat(result).contains("data.xlsx");
        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("Sheet: Sheet1");
        assertThat(result).contains("Product | Price");
        assertThat(result).contains("Widget | 9.99");
    }

    @Test
    void shouldExtractMultipleSheets() throws Exception {
        byte[] xlsxBytes = generateXlsxMultiSheet();
        var attachment = attachment(6, "multi.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(xlsxBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 6);

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

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(pptxBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 7);

        assertThat(result).contains("presentation.pptx");
        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("Slide 1");
        assertThat(result).contains("Title Slide");
        assertThat(result).contains("Bullet point content");
    }

    // --- Text files ---

    @Test
    void shouldReturnTextFileContent() {
        byte[] textBytes = "log line 1\nlog line 2\n".getBytes();
        var attachment = attachment(8, "app.log", "text/plain", textBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(textBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 8);

        assertThat(result).contains("app.log");
        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("log line 1");
        assertThat(result).contains("log line 2");
    }

    @Test
    void shouldReturnJsonFileContent() throws Exception {
        byte[] jsonBytes = "{\"key\": \"value\"}".getBytes();
        var attachment = attachment(9, "config.json", "application/json", jsonBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(jsonBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 9);

        assertThat(result).contains("\"textExtracted\":true");
        assertThat(ToolJsonTestSupport.parse(result).get("content").asText()).contains("\"key\": \"value\"");
    }

    @Test
    void shouldReturnXsdFileContentWithGenericContentType() throws Exception {
        byte[] xsdBytes = """
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                    <xs:element name="order" type="xs:string"/>
                </xs:schema>
                """.getBytes();
        var attachment = attachment(16, "schema.xsd", "application/octet-stream", xsdBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(xsdBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 16);

        assertThat(result).contains("schema.xsd");
        assertThat(result).contains("\"textExtracted\":true");
        assertThat(ToolJsonTestSupport.parse(result).get("content").asText())
                .contains("<xs:element name=\"order\" type=\"xs:string\"/>");
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

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(zipBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 17);

        assertThat(result).contains("bundle.zip");
        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("ZIP archive: bundle.zip");
        assertThat(result).contains("config/service.yaml (extracted");
        assertThat(result).contains("schema/order.xsd (extracted");
        assertThat(result).contains("docs/decision.docx (extracted");
        assertThat(result).contains("images/screenshot.png (skipped, not text-extractable");
        assertThat(result).contains("--- config/service.yaml ---");
        assertThat(result).contains("feature: archive extraction");
        assertThat(result).contains("--- schema/order.xsd ---");
        assertThat(ToolJsonTestSupport.parse(result).get("content").asText())
                .contains("<xs:element name=\"orderId\" type=\"xs:string\"/>");
        assertThat(result).contains("--- docs/decision.docx ---");
        assertThat(result).contains("Architecture decision from Word document");
    }

    // --- Binary files ---

    @Test
    void shouldReturnMetadataForBinaryFile() {
        var attachment = attachment(10, "photo.png", "image/png", 50_000);

        stubIssueAttachment(attachment);

        String result = tools.getAttachmentContent(ISSUE_ID, 10);

        assertThat(result).contains("photo.png");
        assertThat(result).contains("Image file");
        assertThat(result).contains("getImageAttachment");
        assertThat(result).contains("\"textExtracted\":false");
    }

    // --- Truncation ---

    @Test
    void shouldTruncateLargeTextContent() throws Exception {
        String longText = "x".repeat(60_000);
        byte[] textBytes = longText.getBytes();
        var attachment = attachment(11, "huge.txt", "text/plain", textBytes.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(textBytes);

        String result = tools.getAttachmentContent(ISSUE_ID, 11);

        assertThat(result).contains("\"textExtracted\":true");
        assertThat(result).contains("\"truncated\":true");
        // Total output should be limited — the extracted text part must be <= 50000 + truncation message
        String contentPart = ToolJsonTestSupport.parse(result).get("content").asText();
        assertThat(contentPart.length()).isLessThan(60_000);
    }

    // --- Attachment not found ---

    @Test
    void shouldHandleAttachmentNotFound() {
        when(client.getIssue(ISSUE_ID)).thenReturn(issueWithAttachments(ISSUE_ID, java.util.List.of()));

        String result = tools.getAttachmentContent(ISSUE_ID, 999);

        assertThat(result).contains("not found");
    }

    // --- Corrupt document ---

    @Test
    void shouldHandleCorruptDocument() {
        byte[] garbage = "this is not a valid pdf".getBytes();
        var attachment = attachment(12, "corrupt.pdf", "application/pdf", garbage.length);

        stubIssueAttachment(attachment);
        when(client.downloadAttachment(attachment.contentUrl())).thenReturn(garbage);

        String result = tools.getAttachmentContent(ISSUE_ID, 12);

        assertThat(result).contains("corrupt.pdf");
        assertThat(result).contains("\"textExtracted\":true");
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

    private void stubIssueAttachment(RedmineAttachment attachment) {
        when(client.getIssue(ISSUE_ID)).thenReturn(issueWithAttachments(ISSUE_ID, List.of(attachment)));
    }

    private static RedmineIssue issueWithAttachments(int id, List<RedmineAttachment> attachments) {
        return new RedmineIssue(
                id,
                new IdName(1, "test-project"),
                new IdName(1, "Bug"),
                new IdName(1, "Open"),
                new IdName(2, "Normal"),
                new IdName(1, "Author"),
                null,
                null, null, null,
                "Test issue", null,
                null, null, 0,
                null, null, false,
                "2025-01-01", "2025-01-02",
                null, attachments, null, null, null
        );
    }

}





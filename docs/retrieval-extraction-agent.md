# docs/retrieval-extraction-agent.md — recoverable responses, snapshots and attachment parsing

Read this when changing issue history, tree reads, snapshots, attachments, response limits or document parsers.

## Recoverable bounded responses

Optimize the model's context and follow-up choices, not Redmine caching. When a tool omits or
shortens data, its response must identify the missing portion and give the model a way to
request it. `getIssueHistory.nextOffset` leads to remaining events, `journalId` to the full
entry via `getIssueJournal`, and compact tree nodes retain issue IDs for `getIssue`. A path to
raw `issue.json` is not a substitute for these structured follow-up paths. `getAttachment`
saves the original file and returns its `localPath`/`fileUri` with bounded extracted text and
truncation metadata, so clients with filesystem access can inspect the original. Issue
snapshots are local artifacts, not a general Redmine cache or the primary LLM retrieval
interface; do not eagerly download related issues or all attachments to fill the snapshot
directory.

## Document extraction pipeline

Located in `extraction/`. Implementations of `DocumentParser` are registered into
`ExtractionPipeline` and tried in order based on content type detected by `FileTypeDetector`.
Existing parsers (under `extraction/parser/`):

| Parser | Purpose |
|---|---|
| `PlainTextParser` | txt, log, csv, json, xml — direct UTF-8 read. |
| `PdfTextParser` | PDF via PDFBox (text-layer only; scans without OCR yield empty text). |
| `DocxPandocParser`, `DocxTextParser`, `DocxMediaExtractor`, `DocxEmbeddedExtractor` | DOCX yields **one** text part: pandoc markdown when `extraction.pandoc.enabled` and `pandoc` is found; `DocxTextParser` (POI) runs after it and skips itself via `ParseSink#hasTextPart()`, so it is only the fallback. Do not reintroduce a second text part for the same DOCX — it doubled the response and split the text budget. |
| `XlsxTextParser`, `PptxTextParser` | XLSX / PPTX via POI. |
| `ZipParser` | ZIP — recursive but **depth-bounded** by `extraction.limits.max-depth` (default 1). |
| `ImagePassthroughParser` | Images — no text extracted; only `localPath`/`fileUri` exposed. |
| `TikaTextFallbackParser`, `TikaMetadataParser` | Tika fallback when nothing else matched. |
| `BinaryFallbackParser` | Last resort — no text, metadata only. |

When you add a parser:

- Implement `DocumentParser` (typically extending `AbstractDocumentParser`).
- Register it in the parser list inside `ExtractionPipeline` (order matters — first
  `canParse(...) == true` wins).
- **Respect `ExtractionLimits`**: `maxTotalBytes`, `maxTotalParts`, `maxEntryBytes`,
  `maxDepth`. Use `ParseSink#shouldStop()` to bail out early; do not buffer entire archives
  into memory.
- Apply the per-part char budget (`AttachmentExtraction.perPartChars`) before returning text.
  This is the layer that protects MCP clients from being flooded by a single huge document.

`PandocAvailability` probes for pandoc once at startup with a short timeout and caches the
result. Don't call `pandoc` from a parser directly — route through `DocxPandocParser`.

## Issue snapshots and response budgets
- **Issue snapshots persist on disk.** `IssueSnapshotService` writes
  `${dataDir}/issues/<id>/issue.json`, `snapshot.json`, `attachments.json`, and
  `attachments/<id>__<filename>`. Treat the layout as a contract — other tools (especially
  `getAttachment`) return `localPath` values pointing into it. Don't rename directories
  without updating `IssueSnapshotService` and the affected services together.
- **Tree reads keep full snapshots.** `getIssueTree` loads and snapshots every fetched root,
  ancestor, and expanded child in full, then returns compact `Issue` projections in `root` and
  `ancestors`. Never persist a light/partial Redmine response over `issue.json`. Lightweight
  lookups for `getIssue.related` are references only and do not create related-issue snapshots.
- **History compression keeps events.** `getIssueHistory` includes creation and every journal,
  shortens text before constructing `Opaque` fields, and returns `nextOffset` when events need
  another page. Status intervals are page-local. Keep `journalId` so `getIssueJournal` can recover
  full text, and include compression notes in the measured JSON size. An individually oversized
  event is reduced to recoverable metadata; its omitted status intervals are called out explicitly.
- **Attachment text is budget-bounded.** `getAttachment` without caller limits uses
  `attachment.per-part-chars` / `per-attachment-chars`; the default total stays below
  `response.max-chars` so a default response does not hit the response compressor on text alone.
  Explicit `maxChars` / `partLimit` are capped by `attachment.max-request-chars` and then honored:
  `AttachmentContentCompression` only collapses image parts for such calls and never truncates text
  below the request. The applied values are returned in `limits`, a cut part carries `totalChars`.
  New tools that surface attachment text must reuse this budget rather than inventing a parallel one.

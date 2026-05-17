package ru.it_spectrum.ai.redmine.mcp.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates {@link DocumentParser}s for a single attachment. For each input file
 * it runs every applicable PRIMARY parser, then every applicable FALLBACK parser.
 * Parsers may recursively process children via {@link ParseSink#processNow},
 * keeping output order deterministic.
 *
 * <p>The pipeline back-fills {@code producer} (parser class simple name) and {@code parent}
 * (parent input's logical name) on every emitted Part, so parsers can leave those fields
 * {@code null}.</p>
 */
@Service
public class ExtractionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ExtractionPipeline.class);

    private final List<DocumentParser> parsers;

    public ExtractionPipeline(List<DocumentParser> parsers) {
        this.parsers = List.copyOf(parsers);
    }

    /**
     * Process a single file through every applicable parser.
     * {@code logicalName} should be the file's own name (used by parsers for type detection
     * and as the Part name when nested).
     */
    public List<ExtractedPart> extract(Path file, String logicalName, String contentType, Path workDir) {
        var ctx = ParseContext.defaults();
        var collected = new ArrayList<ExtractedPart>();
        var input = new ParseInput(file, logicalName, null, contentType, workDir, 0, ctx);
        runOnInput(input, collected);
        return collected;
    }

    private void runOnInput(ParseInput input, List<ExtractedPart> collected) {
        if (collected.size() >= input.ctx().limits().maxTotalParts()) {
            return;
        }
        var sink = new InputSink(input, collected);

        for (var parser : parsers) {
            if (parser.phase() == DocumentParser.Phase.PRIMARY && parser.applies(input)) {
                sink.primaryApplied = true;
                runParserSafely(parser, input, sink);
            }
        }
        for (var parser : parsers) {
            if (parser.phase() == DocumentParser.Phase.FALLBACK && parser.applies(input)) {
                runParserSafely(parser, input, sink);
            }
        }
    }

    private void runParserSafely(DocumentParser parser, ParseInput input, InputSink sink) {
        sink.currentProducer = parser.getClass().getSimpleName();
        try {
            parser.parse(input, sink);
        } catch (Exception e) {
            log.warn("Parser {} failed for {}: {}",
                    parser.getClass().getSimpleName(), input.logicalName(), e.getMessage());
        } finally {
            sink.currentProducer = null;
        }
    }

    private final class InputSink implements ParseSink {
        private final ParseInput input;
        private final List<ExtractedPart> collected;
        private int textPartsForThisInput;
        private int partsForThisInput;
        private boolean primaryApplied;
        private String currentProducer;

        InputSink(ParseInput input, List<ExtractedPart> collected) {
            this.input = input;
            this.collected = collected;
        }

        @Override
        public void emit(ExtractedPart part) {
            if (collected.size() >= input.ctx().limits().maxTotalParts()) {
                return;
            }
            var enriched = part
                    .withProducerIfAbsent(currentProducer)
                    .withParentIfAbsent(input.parentLogicalName());
            collected.add(enriched);
            partsForThisInput++;
            if (enriched.textExtracted()) {
                textPartsForThisInput++;
            }
        }

        @Override
        public void processNow(Path childFile, String childLogicalName, String contentType) {
            var child = new ParseInput(childFile, childLogicalName, input.logicalName(),
                    contentType, input.workDir(), input.depth() + 1, input.ctx());
            runOnInput(child, collected);
        }

        @Override
        public boolean hasTextPart() {
            return textPartsForThisInput > 0;
        }

        @Override
        public int emittedCount() {
            return partsForThisInput;
        }

        @Override
        public boolean primaryApplied() {
            return primaryApplied;
        }
    }
}

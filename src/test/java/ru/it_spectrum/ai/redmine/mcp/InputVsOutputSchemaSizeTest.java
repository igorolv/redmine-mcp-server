package ru.it_spectrum.ai.redmine.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.ai.util.json.schema.JsonSchemaGenerator;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Diagnostic (not an assertion test): for every {@code @McpTool} method, measures the size of the
 * generated MCP <em>input</em> schema (what every MCP client puts in the model context at
 * {@code tools/list} time) alongside the <em>output</em> schema. Prints per-tool and totals.
 * Run: {@code ./gradlew test --tests InputVsOutputSchemaSizeTest -i} and read the printed report.
 */
class InputVsOutputSchemaSizeTest {

    record Row(String tool, int descChars, int inChars, int inWire, int outChars) {
        int promptChars() { return descChars + inChars; }
    }

    /**
     * Length of the schema as it actually crosses the wire: the MCP SDK stores inputSchema as a
     * {@code Map<String,Object>} (parsed from this string) and the transport re-serializes it with a
     * default ObjectMapper, i.e. compact, no pretty-print indentation. {@code generateForMethodInput}
     * returns a {@code toPrettyString()} form, so its raw length over-counts by all the whitespace.
     */
    private static int wireLen(ObjectMapper mapper, String prettyJson) {
        try {
            return mapper.writeValueAsString(mapper.readTree(prettyJson)).length();
        } catch (Exception e) {
            return prettyJson.length();
        }
    }

    @Test
    void measureInputVsOutputSchemaSizes() {
        var mapper = new ObjectMapper();
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*")));
        var candidates = scanner.findCandidateComponents("ru.it_spectrum.ai.redmine.mcp.tools");

        List<Row> rows = new ArrayList<>();
        int totalDesc = 0, totalIn = 0, totalInWire = 0, totalOut = 0;
        for (var bd : candidates) {
            Class<?> cls;
            try {
                cls = Class.forName(((AnnotatedBeanDefinition) bd).getMetadata().getClassName());
            } catch (ClassNotFoundException e) {
                continue;
            }
            for (Method m : cls.getDeclaredMethods()) {
                McpTool t = m.getAnnotation(McpTool.class);
                if (t == null) continue;

                int descLen = t.description().length();
                // McpJsonSchemaGenerator is the generator the MCP server actually uses to build the
                // advertised inputSchema: it reads @McpToolParam descriptions and honours required=false.
                String inSchema = McpJsonSchemaGenerator.generateForMethodInput(m);
                int inLen = inSchema.length();
                int inWire = wireLen(mapper, inSchema);

                int outLen = 0;
                Type rt = m.getGenericReturnType();
                if (t.generateOutputSchema() && rt != void.class) {
                    outLen = JsonSchemaGenerator.generateForType(rt).length();
                }

                totalDesc += descLen;
                totalIn += inLen;
                totalInWire += inWire;
                totalOut += outLen;
                rows.add(new Row(m.getName(), descLen, inLen, inWire, outLen));
            }
        }

        rows.sort(Comparator.comparingInt(Row::promptChars).reversed());
        System.out.println("\n===== INPUT vs OUTPUT SCHEMA SIZE REPORT =====");
        System.out.printf("tools: %d%n", rows.size());
        System.out.printf("TOTAL tool-description chars : %d%n", totalDesc);
        System.out.printf("TOTAL input-schema (pretty)  : %d%n", totalIn);
        System.out.printf("TOTAL input-schema (wire)    : %d  <- compact, what clients actually receive%n", totalInWire);
        System.out.printf("=> in-context per connect    : %d (desc + input wire)%n", totalDesc + totalInWire);
        System.out.printf("TOTAL output-schema chars    : %d%n%n", totalOut);
        System.out.printf("%-7s  %-7s  %-7s  %-7s  %s%n", "desc", "in", "inWire", "out", "tool");
        for (Row r : rows) {
            System.out.printf("%-7d  %-7d  %-7d  %-7d  %s%n", r.descChars(), r.inChars(), r.inWire(), r.outChars(), r.tool());
        }
        System.out.println("==============================================\n");
    }
}

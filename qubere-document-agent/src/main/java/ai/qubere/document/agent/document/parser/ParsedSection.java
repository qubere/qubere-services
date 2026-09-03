package ai.qubere.document.agent.document.parser;

import java.util.List;

/**
 * @param id          deterministic id derived from position + content hash; stable across reruns
 * @param headingPath heading trail from the document root, outermost first
 * @param content     section text, or compact Markdown when the section carries structure
 */
public record ParsedSection(String id, List<String> headingPath, String content, List<Provenance> provenance) {
    public ParsedSection {
        headingPath = headingPath == null ? List.of() : List.copyOf(headingPath);
        provenance = provenance == null ? List.of() : List.copyOf(provenance);
    }
}

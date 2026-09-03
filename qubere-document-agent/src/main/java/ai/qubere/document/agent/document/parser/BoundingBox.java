package ai.qubere.document.agent.document.parser;

/**
 * A bounding box in the coordinate space the parser reported, with its origin recorded rather
 * than assumed. Never synthesized: absent provenance is absent, not a fabricated zero box.
 */
public record BoundingBox(double left, double top, double right, double bottom, CoordinateOrigin coordOrigin) {
}

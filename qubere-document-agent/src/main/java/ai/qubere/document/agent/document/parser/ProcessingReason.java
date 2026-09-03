package ai.qubere.document.agent.document.parser;

/** Why a processing run exists. Recorded on the run for audit. */
public enum ProcessingReason {
    INITIAL,
    MANUAL_REPROCESS,
    OCR_RETRY,
    QUALITY_RETRY,
    PARSER_UPGRADE,
    CONFIG_CHANGE
}

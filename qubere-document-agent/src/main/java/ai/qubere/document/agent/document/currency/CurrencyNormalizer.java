package ai.qubere.document.agent.document.currency;

/**
 * Currency-code normalization, ported from {@code extractedCurrency.ts}'s private {@code currencyOf}
 * symbol-matching logic. Pure, no I/O.
 */
public final class CurrencyNormalizer {

    private CurrencyNormalizer() {
    }

    /**
     * Normalizes a raw currency string (symbol, word, or ISO code) to an ISO 4217 code, or
     * {@code null} when nothing recognizable is present. Never guesses: a raw value that isn't a
     * known symbol/word and isn't itself exactly 3 letters returns {@code null} rather than a
     * best-effort code.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (clean.contains("GBP") || clean.contains("POUND") || clean.contains("£")) {
            return "GBP";
        }
        if (clean.contains("EUR") || clean.contains("EURO") || clean.contains("€")) {
            return "EUR";
        }
        if (clean.contains("USD") || clean.contains("DOLLAR") || clean.contains("$")) {
            return "USD";
        }
        if (clean.contains("CAD")) {
            return "CAD";
        }
        if (clean.contains("AUD")) {
            return "AUD";
        }
        if (clean.contains("JPY") || clean.contains("YEN") || clean.contains("¥")) {
            return "JPY";
        }
        if (clean.length() == 3) {
            return clean;
        }
        return null;
    }
}

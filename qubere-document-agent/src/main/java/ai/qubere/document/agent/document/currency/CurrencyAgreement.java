package ai.qubere.document.agent.document.currency;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Currency agreement across a set of documents, ported from {@code extractedCurrency.ts}'s
 * {@code extractedCurrencies}/{@code extractedCurrency}. Pure, no I/O --
 * {@link ai.qubere.document.agent.document.currency.CurrencyExtractionService} is the one caller
 * that resolves raw values from storage before calling in here.
 */
public final class CurrencyAgreement {

    private CurrencyAgreement() {
    }

    /** Returns every distinct normalized currency found, preserving conflicts for filing review. */
    public static List<String> distinctCurrencies(List<String> rawValues) {
        TreeSet<String> found = new TreeSet<>();
        for (String raw : rawValues) {
            String code = CurrencyNormalizer.normalize(raw);
            if (code != null) {
                found.add(code);
            }
        }
        return new ArrayList<>(found);
    }

    /**
     * The single currency these raw values agree on, or {@code null}.
     * <p>
     * Null when no value declared one <strong>and</strong> when two disagree: a guessed currency
     * misstates every amount rendered from it, and picking one of two conflicting codes would be a
     * claim the documents do not support. Callers render a bare number in that case rather than an
     * invented symbol.
     */
    public static String agreedCurrency(List<String> rawValues) {
        List<String> found = distinctCurrencies(rawValues);
        return found.size() == 1 ? found.get(0) : null;
    }
}

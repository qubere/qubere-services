package ai.qubere.document.agent.document.currency;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CurrencyNormalizerTest {

    @Test
    void normalizesSymbolsAndWordsToIsoCodes() {
        assertThat(CurrencyNormalizer.normalize("£100")).isEqualTo("GBP");
        assertThat(CurrencyNormalizer.normalize("100 pounds")).isEqualTo("GBP");
        assertThat(CurrencyNormalizer.normalize("€50")).isEqualTo("EUR");
        assertThat(CurrencyNormalizer.normalize("50 euro")).isEqualTo("EUR");
        assertThat(CurrencyNormalizer.normalize("$25")).isEqualTo("USD");
        assertThat(CurrencyNormalizer.normalize("25 dollars")).isEqualTo("USD");
        assertThat(CurrencyNormalizer.normalize("CAD 10")).isEqualTo("CAD");
        assertThat(CurrencyNormalizer.normalize("AUD 10")).isEqualTo("AUD");
        assertThat(CurrencyNormalizer.normalize("¥1000")).isEqualTo("JPY");
        assertThat(CurrencyNormalizer.normalize("1000 yen")).isEqualTo("JPY");
    }

    @Test
    void acceptsAnExactThreeLetterCodeVerbatim() {
        assertThat(CurrencyNormalizer.normalize("inr")).isEqualTo("INR");
    }

    @Test
    void returnsNullForBlankOrUnrecognizedInput() {
        assertThat(CurrencyNormalizer.normalize(null)).isNull();
        assertThat(CurrencyNormalizer.normalize("")).isNull();
        assertThat(CurrencyNormalizer.normalize("   ")).isNull();
        assertThat(CurrencyNormalizer.normalize("not a currency at all")).isNull();
    }

    @Test
    void distinctCurrenciesPreservesAllConflictingCodesSorted() {
        List<String> result = CurrencyAgreement.distinctCurrencies(Arrays.asList("$100", "€50", null, ""));

        assertThat(result).containsExactly("EUR", "USD");
    }

    @Test
    void agreedCurrencyIsNullWhenNoDocumentDeclaredOne() {
        assertThat(CurrencyAgreement.agreedCurrency(List.of())).isNull();
        assertThat(CurrencyAgreement.agreedCurrency(Arrays.asList((String) null, ""))).isNull();
    }

    @Test
    void agreedCurrencyIsNullWhenTwoDocumentsDisagree() {
        assertThat(CurrencyAgreement.agreedCurrency(List.of("$100", "€50"))).isNull();
    }

    @Test
    void agreedCurrencyIsTheSingleValueEveryDocumentAgreesOn() {
        assertThat(CurrencyAgreement.agreedCurrency(List.of("$100", "USD 200", "$300"))).isEqualTo("USD");
    }
}

package ai.qubere.document.agent.document.parser.config;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential backoff with full jitter, bounded, ported from {@code parser/config.ts}. Jitter is
 * applied because a burst of documents failing against the same provider outage would otherwise
 * retry in lockstep forever.
 */
public final class ParserBackoff {

    private ParserBackoff() {
    }

    public static long backoffDelayMillis(int attempt, long baseMillis, long maxMillis) {
        long exponential = Math.min((long) (baseMillis * Math.pow(2, Math.max(0, attempt - 1))), maxMillis);
        return (long) (exponential / 2.0 + ThreadLocalRandom.current().nextDouble() * (exponential / 2.0));
    }

    public static long pollDelayMillis(int pollAttempt, ParserProperties.ProcessingLimits limits) {
        return backoffDelayMillis(pollAttempt, limits.getPollInitialDelayMillis(), limits.getPollMaxDelayMillis());
    }
}

package com.benggri.example.core.aop;

import org.springframework.core.Ordered;

/**
 * Advice order, smaller = outer. The {@code @Transactional} advisor keeps the default
 * {@link Ordered#LOWEST_PRECEDENCE}; both aspects sit outside it so a failure that only surfaces at
 * commit or flush (after the method body returned) is still seen as a failure.
 *
 * <p>Timing is outermost: its timer then covers the audit write and the transaction, and it sees every
 * outcome. Audit is next, directly around the transaction: it records exactly what the transaction
 * decided (committed or rolled back).
 */
final class AopOrder {

    static final int LOGGING = Ordered.LOWEST_PRECEDENCE - 200;
    static final int AUDIT = Ordered.LOWEST_PRECEDENCE - 100;

    private AopOrder() {
    }
}

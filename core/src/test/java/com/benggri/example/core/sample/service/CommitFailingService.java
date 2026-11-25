package com.benggri.example.core.sample.service;

import com.benggri.example.core.aop.Audited;
import com.benggri.example.core.security.AuthUser;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Test-only service whose body succeeds but whose transaction fails at commit. Created only by
 * {@code AspectOrderingIntegrationTest}'s test configuration (it is not a component).
 */
public class CommitFailingService {

    @Audited("sample.commitfail")
    @Transactional
    public String act(AuthUser actor) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                throw new IllegalStateException("simulated commit failure");
            }
        });
        return "body-ok";
    }
}

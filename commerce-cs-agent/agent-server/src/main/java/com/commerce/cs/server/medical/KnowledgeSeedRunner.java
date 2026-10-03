package com.commerce.cs.server.medical;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
public class KnowledgeSeedRunner implements ApplicationRunner {
    private final KnowledgeLedger ledger;

    public KnowledgeSeedRunner(KnowledgeLedger ledger) {
        this.ledger = ledger;
    }

    @Override
    public void run(ApplicationArguments args) {
        ledger.seedIfEmpty();
    }
}

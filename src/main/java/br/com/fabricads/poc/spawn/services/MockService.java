package br.com.fabricads.poc.spawn.services;

import br.com.fabricads.poc.spawn.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public final class MockService {

    private static final Logger log = LoggerFactory.getLogger(MockService.class);

    private final AtomicInteger reqCounter;

    public MockService() {
        this.reqCounter = new AtomicInteger(0);
    }

    public Optional<Long> counter() {
        Timer timer = new Timer();
        try {
            if(reqCounter.incrementAndGet() > 1000) {
                throw new RuntimeException(String.valueOf(reqCounter.get()));
            }
            Thread.sleep(reqCounter.get() * 100L);
            return Optional.of(timer.getTime());
        } catch (Exception err) {
            log.error("Error counter", err);
            return Optional.empty();
        } finally {
            reqCounter.decrementAndGet();
            log.debug("Running time {}", timer);
        }
    }
}

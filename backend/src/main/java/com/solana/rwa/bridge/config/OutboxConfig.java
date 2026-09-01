package com.solana.rwa.bridge.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Scheduling and clock configuration for the finality confirmation outbox.
 *
 * <p>{@link EnableScheduling} activates the {@code @Scheduled} outbox poller.
 * The {@link Clock} bean is the injectable time source so worker state
 * transitions remain deterministic under test (a fixed clock can be supplied in
 * unit tests), while production uses {@link Clock#systemUTC()}.
 */
@Configuration
@EnableScheduling
public class OutboxConfig {

    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}

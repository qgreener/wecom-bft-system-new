package com.wecombft.shared.id;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

@Component
public class IdGenerator {

    private final AtomicLong sequence = new AtomicLong();

    public long nextId() {
        long current = Instant.now().toEpochMilli() * 1000;
        long suffix = Math.floorMod(sequence.getAndIncrement(), 1000);
        return current + suffix;
    }
}

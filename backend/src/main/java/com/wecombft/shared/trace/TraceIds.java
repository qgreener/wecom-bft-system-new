package com.wecombft.shared.trace;

import java.util.UUID;

public final class TraceIds {

    public static final String HEADER_NAME = "X-Trace-Id";
    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TraceIds() {
    }

    public static String currentOrCreate() {
        String traceId = CURRENT.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = create();
            CURRENT.set(traceId);
        }
        return traceId;
    }

    public static void set(String traceId) {
        CURRENT.set(traceId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    public static String create() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

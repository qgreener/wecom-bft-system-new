package com.wecombft.application.idempotency;

import com.wecombft.domain.model.IdempotencyKey;

public class RequestIdempotencyGuard {

    public IdempotencyKey requireKey(String headerValue) {
        return IdempotencyKey.of(headerValue);
    }

    public IdempotencyKey compose(String sceneCode, String relatedObjectType, Object relatedObjectId, String receiver) {
        return IdempotencyKey.ofParts(
                String.valueOf(sceneCode),
                String.valueOf(relatedObjectType),
                String.valueOf(relatedObjectId),
                String.valueOf(receiver));
    }
}

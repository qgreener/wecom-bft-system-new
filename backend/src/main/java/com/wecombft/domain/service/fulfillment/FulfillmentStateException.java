package com.wecombft.domain.service.fulfillment;

public class FulfillmentStateException extends RuntimeException {

    private final boolean orderStateViolation;

    public FulfillmentStateException(String message, boolean orderStateViolation) {
        super(message);
        this.orderStateViolation = orderStateViolation;
    }

    public boolean orderStateViolation() {
        return orderStateViolation;
    }
}

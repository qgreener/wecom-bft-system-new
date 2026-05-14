package com.wecombft.domain.service.trade;

import java.util.Map;
import java.util.Set;

import com.wecombft.domain.enums.FulfillmentStatus;
import com.wecombft.domain.enums.OrderInvoiceStatus;
import com.wecombft.domain.enums.OrderRefundStatus;
import com.wecombft.domain.enums.PaymentStatus;

public final class OrderStateTransitionRules {

    private static final StateTransitionRules<PaymentStatus> PAYMENT = StateTransitionRules.of(Map.of(
            PaymentStatus.PENDING, Set.of(PaymentStatus.PAID, PaymentStatus.CLOSED)));

    private static final StateTransitionRules<FulfillmentStatus> FULFILLMENT = StateTransitionRules.of(Map.of(
            FulfillmentStatus.PENDING_SHIPMENT, Set.of(FulfillmentStatus.SHIPPED),
            FulfillmentStatus.SHIPPED, Set.of(FulfillmentStatus.SIGNED)));

    private static final StateTransitionRules<OrderRefundStatus> REFUND_SUMMARY = StateTransitionRules.of(Map.of(
            OrderRefundStatus.NONE, Set.of(OrderRefundStatus.REVIEWING),
            OrderRefundStatus.REVIEWING, Set.of(
                    OrderRefundStatus.REJECTED,
                    OrderRefundStatus.PROCESSING,
                    OrderRefundStatus.MANUAL_REQUIRED),
            OrderRefundStatus.PROCESSING, Set.of(
                    OrderRefundStatus.FAILED,
                    OrderRefundStatus.MANUAL_REQUIRED,
                    OrderRefundStatus.REFUNDED),
            OrderRefundStatus.FAILED, Set.of(
                    OrderRefundStatus.PROCESSING,
                    OrderRefundStatus.MANUAL_REQUIRED),
            OrderRefundStatus.MANUAL_REQUIRED, Set.of(OrderRefundStatus.REFUNDED)));

    private static final StateTransitionRules<OrderInvoiceStatus> INVOICE = StateTransitionRules.of(Map.of(
            OrderInvoiceStatus.NOT_APPLIED, Set.of(OrderInvoiceStatus.APPLIED),
            OrderInvoiceStatus.APPLIED, Set.of(OrderInvoiceStatus.TO_BE_ISSUED, OrderInvoiceStatus.ISSUED),
            OrderInvoiceStatus.TO_BE_ISSUED, Set.of(OrderInvoiceStatus.ISSUED),
            OrderInvoiceStatus.ISSUED, Set.of(OrderInvoiceStatus.RED_REVERSED)));

    private OrderStateTransitionRules() {
    }

    public static StateTransitionRules<PaymentStatus> payment() {
        return PAYMENT;
    }

    public static StateTransitionRules<FulfillmentStatus> fulfillment() {
        return FULFILLMENT;
    }

    public static StateTransitionRules<OrderRefundStatus> refundSummary() {
        return REFUND_SUMMARY;
    }

    public static StateTransitionRules<OrderInvoiceStatus> invoice() {
        return INVOICE;
    }
}

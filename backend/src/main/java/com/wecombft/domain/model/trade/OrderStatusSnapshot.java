package com.wecombft.domain.model.trade;

import java.util.Objects;

import com.wecombft.domain.enums.FulfillmentStatus;
import com.wecombft.domain.enums.OrderInvoiceStatus;
import com.wecombft.domain.enums.OrderRefundStatus;
import com.wecombft.domain.enums.PaymentStatus;

public record OrderStatusSnapshot(
        PaymentStatus paymentStatus,
        FulfillmentStatus fulfillmentStatus,
        OrderRefundStatus refundStatus,
        OrderInvoiceStatus invoiceStatus) {

    public OrderStatusSnapshot {
        Objects.requireNonNull(paymentStatus, "paymentStatus must not be null");
        Objects.requireNonNull(fulfillmentStatus, "fulfillmentStatus must not be null");
        Objects.requireNonNull(refundStatus, "refundStatus must not be null");
        Objects.requireNonNull(invoiceStatus, "invoiceStatus must not be null");
    }

    public static OrderStatusSnapshot initialForVirtualOrder() {
        return new OrderStatusSnapshot(
                PaymentStatus.PENDING,
                FulfillmentStatus.NO_SHIPMENT,
                OrderRefundStatus.NONE,
                OrderInvoiceStatus.NOT_APPLIED);
    }

    public static OrderStatusSnapshot initialForPhysicalOrder() {
        return new OrderStatusSnapshot(
                PaymentStatus.PENDING,
                FulfillmentStatus.PENDING_SHIPMENT,
                OrderRefundStatus.NONE,
                OrderInvoiceStatus.NOT_APPLIED);
    }
}

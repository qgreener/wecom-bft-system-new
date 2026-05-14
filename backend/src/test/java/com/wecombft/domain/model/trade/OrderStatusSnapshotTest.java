package com.wecombft.domain.model.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.wecombft.domain.enums.FulfillmentStatus;
import com.wecombft.domain.enums.OrderInvoiceStatus;
import com.wecombft.domain.enums.OrderRefundStatus;
import com.wecombft.domain.enums.PaymentStatus;

class OrderStatusSnapshotTest {

    @Test
    void should_create_initial_virtual_order_state_snapshot() {
        OrderStatusSnapshot snapshot = OrderStatusSnapshot.initialForVirtualOrder();

        assertThat(snapshot.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(snapshot.fulfillmentStatus()).isEqualTo(FulfillmentStatus.NO_SHIPMENT);
        assertThat(snapshot.refundStatus()).isEqualTo(OrderRefundStatus.NONE);
        assertThat(snapshot.invoiceStatus()).isEqualTo(OrderInvoiceStatus.NOT_APPLIED);
    }

    @Test
    void should_create_initial_physical_order_state_snapshot() {
        OrderStatusSnapshot snapshot = OrderStatusSnapshot.initialForPhysicalOrder();

        assertThat(snapshot.paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(snapshot.fulfillmentStatus()).isEqualTo(FulfillmentStatus.PENDING_SHIPMENT);
        assertThat(snapshot.refundStatus()).isEqualTo(OrderRefundStatus.NONE);
        assertThat(snapshot.invoiceStatus()).isEqualTo(OrderInvoiceStatus.NOT_APPLIED);
    }

    @Test
    void should_not_expose_single_order_state_accessor() {
        assertThat(Arrays.stream(OrderStatusSnapshot.class.getDeclaredMethods()).map(method -> method.getName()))
                .doesNotContain("orderStatus", "getOrderStatus");
    }
}

package com.wecombft.domain.service.trade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.wecombft.domain.enums.FulfillmentStatus;
import com.wecombft.domain.enums.OrderInvoiceStatus;
import com.wecombft.domain.enums.OrderRefundStatus;
import com.wecombft.domain.enums.PaymentStatus;

class OrderStateTransitionRulesTest {

    @Test
    void should_allow_payment_only_from_pending_to_paid_or_closed() {
        StateTransitionRules<PaymentStatus> rules = OrderStateTransitionRules.payment();

        assertThat(rules.canTransition(PaymentStatus.PENDING, PaymentStatus.PAID)).isTrue();
        assertThat(rules.canTransition(PaymentStatus.PENDING, PaymentStatus.CLOSED)).isTrue();
        assertThat(rules.canTransition(PaymentStatus.PAID, PaymentStatus.CLOSED)).isFalse();
        assertThat(rules.canTransition(PaymentStatus.PAID, PaymentStatus.PAID)).isTrue();
    }

    @Test
    void should_reject_fulfillment_regression_after_signed() {
        StateTransitionRules<FulfillmentStatus> rules = OrderStateTransitionRules.fulfillment();

        assertThat(rules.canTransition(FulfillmentStatus.PENDING_SHIPMENT, FulfillmentStatus.SHIPPED)).isTrue();
        assertThat(rules.canTransition(FulfillmentStatus.SHIPPED, FulfillmentStatus.SIGNED)).isTrue();

        assertThatThrownBy(() -> rules.requireTransition(FulfillmentStatus.SIGNED, FulfillmentStatus.SHIPPED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FulfillmentStatus");
    }

    @Test
    void should_keep_refunded_terminal_from_failed_callback_overwrite() {
        StateTransitionRules<OrderRefundStatus> rules = OrderStateTransitionRules.refundSummary();

        assertThat(rules.canTransition(OrderRefundStatus.PROCESSING, OrderRefundStatus.REFUNDED)).isTrue();
        assertThat(rules.canTransition(OrderRefundStatus.REFUNDED, OrderRefundStatus.FAILED)).isFalse();
    }

    @Test
    void should_allow_invoice_red_reverse_only_after_issued() {
        StateTransitionRules<OrderInvoiceStatus> rules = OrderStateTransitionRules.invoice();

        assertThat(rules.canTransition(OrderInvoiceStatus.ISSUED, OrderInvoiceStatus.RED_REVERSED)).isTrue();
        assertThat(rules.canTransition(OrderInvoiceStatus.RED_REVERSED, OrderInvoiceStatus.ISSUED)).isFalse();
    }
}

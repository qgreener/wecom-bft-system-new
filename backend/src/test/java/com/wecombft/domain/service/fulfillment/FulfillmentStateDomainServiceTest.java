package com.wecombft.domain.service.fulfillment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.wecombft.domain.model.fulfillment.ShipmentStateSnapshot;

class FulfillmentStateDomainServiceTest {

    private final FulfillmentStateDomainService service = new FulfillmentStateDomainService();

    @Test
    void should_allow_paid_pending_shipment_order_to_ship() {
        ShipmentStateSnapshot snapshot = new ShipmentStateSnapshot(
            "PAID",
            "PENDING_SHIPMENT",
            "NONE",
            "PENDING_SHIPMENT");

        assertThatCode(() -> service.ensureCanShip(snapshot)).doesNotThrowAnyException();
    }

    @Test
    void should_block_ship_when_order_payment_or_refund_state_is_invalid() {
        ShipmentStateSnapshot snapshot = new ShipmentStateSnapshot(
            "PENDING",
            "PENDING_SHIPMENT",
            "NONE",
            "PENDING_SHIPMENT");

        assertThatThrownBy(() -> service.ensureCanShip(snapshot))
            .isInstanceOf(FulfillmentStateException.class)
            .hasMessage("订单状态不允许发货");
    }

    @Test
    void should_block_ship_when_shipment_is_not_pending() {
        ShipmentStateSnapshot snapshot = new ShipmentStateSnapshot(
            "PAID",
            "PENDING_SHIPMENT",
            "NONE",
            "SHIPPED");

        assertThatThrownBy(() -> service.ensureCanShip(snapshot))
            .isInstanceOf(FulfillmentStateException.class)
            .hasMessage("发货单当前状态不允许发货");
    }
}

package com.wecombft.domain.service.fulfillment;

import com.wecombft.domain.model.fulfillment.ShipmentStateSnapshot;

public class FulfillmentStateDomainService {

    public void ensureCanShip(ShipmentStateSnapshot snapshot) {
        if (snapshot == null
            || !"PAID".equals(snapshot.paymentStatus())
            || !"PENDING_SHIPMENT".equals(snapshot.orderFulfillmentStatus())
            || "REFUNDED".equals(snapshot.refundStatus())) {
            throw new FulfillmentStateException("订单状态不允许发货", true);
        }
        if (!"PENDING_SHIPMENT".equals(snapshot.shipmentStatus())) {
            throw new FulfillmentStateException("发货单当前状态不允许发货", false);
        }
    }
}

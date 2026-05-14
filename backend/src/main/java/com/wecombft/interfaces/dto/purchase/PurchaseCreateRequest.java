package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.PurchaseCreateCommand;
import java.time.LocalDate;
import java.util.List;

public record PurchaseCreateRequest(
    Long supplierId,
    List<PurchaseItemRequest> purchaseItems,
    String submitReason,
    LocalDate expectedArrivalDate
) {
    public PurchaseCreateCommand toCommand() {
        return new PurchaseCreateCommand(
            supplierId,
            purchaseItems == null ? null : purchaseItems.stream().map(PurchaseItemRequest::toCommand).toList(),
            submitReason,
            expectedArrivalDate);
    }
}

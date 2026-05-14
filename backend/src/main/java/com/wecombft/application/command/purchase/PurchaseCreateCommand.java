package com.wecombft.application.command.purchase;

import java.time.LocalDate;
import java.util.List;

public record PurchaseCreateCommand(Long supplierId, List<PurchaseItemCommand> purchaseItems, String submitReason, LocalDate expectedArrivalDate) {
}

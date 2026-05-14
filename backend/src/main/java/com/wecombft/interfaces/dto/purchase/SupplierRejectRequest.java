package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.SupplierRejectCommand;

public record SupplierRejectRequest(
    String supplierRejectReason
) {
    public SupplierRejectCommand toCommand() {
        return new SupplierRejectCommand(
            supplierRejectReason);
    }
}

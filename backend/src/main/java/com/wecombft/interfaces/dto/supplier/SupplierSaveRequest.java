package com.wecombft.interfaces.dto.supplier;

import com.wecombft.application.command.purchase.SupplierSaveCommand;

public record SupplierSaveRequest(
    Long supplierId,
    String supplierName,
    String shortName,
    String contactName,
    String contactMobile,
    String contactEmail,
    String taxNo,
    String address,
    String settlementMethod,
    String accessStatus,
    String status
) {
    public SupplierSaveCommand toCommand() {
        return new SupplierSaveCommand(
            supplierId,
            supplierName,
            shortName,
            contactName,
            contactMobile,
            contactEmail,
            taxNo,
            address,
            settlementMethod,
            accessStatus,
            status);
    }
}

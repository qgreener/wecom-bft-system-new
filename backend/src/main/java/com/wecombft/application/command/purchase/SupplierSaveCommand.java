package com.wecombft.application.command.purchase;

public record SupplierSaveCommand(
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
}

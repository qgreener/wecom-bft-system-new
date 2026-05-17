package com.wecombft.interfaces.dto.supplier;

import java.time.LocalDateTime;

public record SupplierResponse(
    long supplierId,
    String supplierNo,
    String supplierName,
    String shortName,
    String contactName,
    String contactMobile,
    String contactEmail,
    String taxNo,
    String address,
    String settlementMethod,
    String accessStatus,
    String status,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}

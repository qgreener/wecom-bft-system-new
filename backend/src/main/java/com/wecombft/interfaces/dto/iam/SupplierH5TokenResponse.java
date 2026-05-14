package com.wecombft.interfaces.dto.iam;

public record SupplierH5TokenResponse(
    String supplierNo,
    String supplierName,
    String sessionToken
) {
}

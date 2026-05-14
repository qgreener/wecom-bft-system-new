package com.wecombft.interfaces.dto.iam;

import com.wecombft.application.command.iam.SupplierH5TokenCommand;

public record SupplierH5TokenRequest(
    String accessToken,
    String supplierNo
) {
    public SupplierH5TokenCommand toCommand() {
        return new SupplierH5TokenCommand(
            accessToken,
            supplierNo);
    }
}

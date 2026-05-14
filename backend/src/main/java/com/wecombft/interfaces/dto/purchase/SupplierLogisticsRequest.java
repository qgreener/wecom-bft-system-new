package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.SupplierLogisticsCommand;

public record SupplierLogisticsRequest(
    String logisticsCompanyName,
    String trackingNo,
    String remark
) {
    public SupplierLogisticsCommand toCommand() {
        return new SupplierLogisticsCommand(
            logisticsCompanyName,
            trackingNo,
            remark);
    }
}

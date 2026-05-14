package com.wecombft.interfaces.dto.fulfillment;

import com.wecombft.application.command.fulfillment.ShipCommand;

public record ShipRequest(
    String logisticsCompanyCode,
    String logisticsCompanyName,
    String trackingNo,
    String waybillFile,
    String remark,
    String mockScenario
) {
    public ShipCommand toCommand() {
        return new ShipCommand(
            logisticsCompanyCode,
            logisticsCompanyName,
            trackingNo,
            waybillFile,
            remark,
            mockScenario);
    }
}

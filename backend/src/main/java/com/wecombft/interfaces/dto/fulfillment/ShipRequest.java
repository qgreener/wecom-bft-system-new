package com.wecombft.interfaces.dto.fulfillment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.wecombft.application.command.fulfillment.ShipCommand;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ShipRequest(
    String logisticsCompanyCode,
    String logisticsCompanyName,
    String trackingNo,
    String waybillFile,
    String remark,
    String mockScenario,
    // 演示口径下保留这些扩展字段，便于前端展示打印方式 / 物流费用 / 包裹重量
    // 后端目前不持久化，待 fulfillment_shipment 扩列后启用
    String printMode,
    Double packageWeightKg,
    Long logisticsFeeCent
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

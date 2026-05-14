package com.wecombft.application.command.fulfillment;

public record ShipCommand(String logisticsCompanyCode, String logisticsCompanyName, String trackingNo, String waybillFile, String remark, String mockScenario) {
}

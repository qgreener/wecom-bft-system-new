package com.wecombft.application.fulfillment.command;

public record ShipCommand(String logisticsCompanyCode, String logisticsCompanyName, String trackingNo, String waybillFile, String remark, String mockScenario) {
}

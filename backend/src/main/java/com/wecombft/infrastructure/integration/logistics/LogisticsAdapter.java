package com.wecombft.infrastructure.integration.logistics;

public interface LogisticsAdapter {

    WaybillResult createWaybill(WaybillRequest request);

    record WaybillRequest(
        long shipmentId,
        String shipmentNo,
        long orderId,
        String orderNo,
        String logisticsCompanyCode,
        String logisticsCompanyName,
        String trackingNo,
        String waybillFile,
        String mockScenario
    ) {
    }

    record WaybillResult(
        String logisticsCompanyCode,
        String logisticsCompanyName,
        String trackingNo,
        String waybillFile,
        boolean externalCreatedInternalFailed,
        boolean waybillFailed,
        String exceptionReason
    ) {
    }
}

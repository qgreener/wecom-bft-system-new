package com.wecombft.infrastructure.integration.logistics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.integration.logistics.mode", havingValue = "mock", matchIfMissing = true)
public class MockLogisticsAdapter implements LogisticsAdapter {

    private static final String WAYBILL_FAILED_REASON = "面单或云打印失败，待补偿";
    private static final String EXTERNAL_CREATED_INTERNAL_FAILED_REASON = "外部已出单内部保存失败，待补偿";

    @Override
    public WaybillResult createWaybill(WaybillRequest request) {
        String scenario = request == null || request.mockScenario() == null
            ? ""
            : request.mockScenario().trim().toUpperCase();
        if ("CREATE_WAYBILL_FAILED".equals(scenario)) {
            throw new LogisticsAdapterException("FULFILLMENT_WAYBILL_FAILED", "物流 Mock 获取运单失败");
        }
        if ("EXTERNAL_CREATED_INTERNAL_FAILED".equals(scenario)) {
            return result(request, true, false, EXTERNAL_CREATED_INTERNAL_FAILED_REASON);
        }
        if ("WAYBILL_FAILED".equals(scenario)) {
            return result(request, false, true, WAYBILL_FAILED_REASON);
        }
        return result(request, false, false, null);
    }

    private WaybillResult result(
        WaybillRequest request,
        boolean externalCreatedInternalFailed,
        boolean waybillFailed,
        String exceptionReason
    ) {
        return new WaybillResult(
            request == null ? null : request.logisticsCompanyCode(),
            request == null ? null : request.logisticsCompanyName(),
            request == null ? null : request.trackingNo(),
            request == null ? null : request.waybillFile(),
            externalCreatedInternalFailed,
            waybillFailed,
            exceptionReason);
    }
}

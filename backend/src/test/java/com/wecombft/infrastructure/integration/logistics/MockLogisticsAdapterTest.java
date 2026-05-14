package com.wecombft.infrastructure.integration.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MockLogisticsAdapterTest {

    private final MockLogisticsAdapter adapter = new MockLogisticsAdapter();

    @Test
    void should_return_standard_waybill_result_for_successful_mock_request() {
        LogisticsAdapter.WaybillResult result = adapter.createWaybill(new LogisticsAdapter.WaybillRequest(
            1001L,
            "SHP1001",
            2001L,
            "ORD2001",
            "mock-express",
            "Mock Express",
            "TRACK1001",
            "FILE_WAYBILL_1001",
            null));

        assertThat(result.trackingNo()).isEqualTo("TRACK1001");
        assertThat(result.waybillFile()).isEqualTo("FILE_WAYBILL_1001");
        assertThat(result.externalCreatedInternalFailed()).isFalse();
        assertThat(result.waybillFailed()).isFalse();
    }

    @Test
    void should_mark_waybill_failure_without_blocking_external_waybill_creation() {
        LogisticsAdapter.WaybillResult result = adapter.createWaybill(new LogisticsAdapter.WaybillRequest(
            1002L,
            "SHP1002",
            2002L,
            "ORD2002",
            "mock-express",
            "Mock Express",
            "TRACK1002",
            null,
            "WAYBILL_FAILED"));

        assertThat(result.trackingNo()).isEqualTo("TRACK1002");
        assertThat(result.waybillFailed()).isTrue();
        assertThat(result.exceptionReason()).isEqualTo("面单或云打印失败，待补偿");
    }

    @Test
    void should_mark_external_created_internal_failed_for_compensation() {
        LogisticsAdapter.WaybillResult result = adapter.createWaybill(new LogisticsAdapter.WaybillRequest(
            1003L,
            "SHP1003",
            2003L,
            "ORD2003",
            "mock-express",
            "Mock Express",
            "TRACK1003",
            "FILE_WAYBILL_1003",
            "EXTERNAL_CREATED_INTERNAL_FAILED"));

        assertThat(result.externalCreatedInternalFailed()).isTrue();
        assertThat(result.exceptionReason()).isEqualTo("外部已出单内部保存失败，待补偿");
    }

    @Test
    void should_throw_standard_exception_when_mock_create_waybill_fails() {
        LogisticsAdapter.WaybillRequest request = new LogisticsAdapter.WaybillRequest(
            1004L,
            "SHP1004",
            2004L,
            "ORD2004",
            "mock-express",
            "Mock Express",
            "TRACK1004",
            null,
            "CREATE_WAYBILL_FAILED");

        assertThatThrownBy(() -> adapter.createWaybill(request))
            .isInstanceOf(LogisticsAdapterException.class)
            .hasMessage("物流 Mock 获取运单失败");
    }
}

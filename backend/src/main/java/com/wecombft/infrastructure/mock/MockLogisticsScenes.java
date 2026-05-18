package com.wecombft.infrastructure.mock;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class MockLogisticsScenes {

    private final MockSceneRegistry registry;

    public MockLogisticsScenes(MockSceneRegistry registry) {
        this.registry = registry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerScenes() {
        registry.register(new MockSceneDefinition(
            "LOGISTICS_WAYBILL_FAILED",
            "LOGISTICS",
            "物流面单失败",
            "调用物流适配器时返回 waybillFailed=true，等待补偿重试",
            "{\"mock_scenario\":\"WAYBILL_FAILED\"}",
            true,
            context -> new MockDispatchResult(
                null,
                "PROCESSED",
                context.eventNo(),
                "Scenario flag broadcast; client should re-invoke /api/admin/shipments/{id}/ship with mock_scenario=WAYBILL_FAILED")
        ));
        registry.register(new MockSceneDefinition(
            "LOGISTICS_EXTERNAL_OK_INTERNAL_FAILED",
            "LOGISTICS",
            "物流外部已出单内部失败",
            "外部物流系统已经创建运单，但内部保存失败，进入补偿待处理",
            "{\"mock_scenario\":\"EXTERNAL_CREATED_INTERNAL_FAILED\"}",
            true,
            context -> new MockDispatchResult(
                null,
                "PROCESSED",
                context.eventNo(),
                "Scenario flag broadcast; client should re-invoke /api/admin/shipments/{id}/ship with mock_scenario=EXTERNAL_CREATED_INTERNAL_FAILED")
        ));
        registry.register(new MockSceneDefinition(
            "LOGISTICS_CREATE_FAILED",
            "LOGISTICS",
            "物流创建运单失败",
            "调用物流适配器抛 LogisticsAdapterException，业务层拒绝发货",
            "{\"mock_scenario\":\"CREATE_WAYBILL_FAILED\"}",
            true,
            context -> new MockDispatchResult(
                null,
                "PROCESSED",
                context.eventNo(),
                "Scenario flag broadcast; client should re-invoke /api/admin/shipments/{id}/ship with mock_scenario=CREATE_WAYBILL_FAILED")
        ));
    }
}

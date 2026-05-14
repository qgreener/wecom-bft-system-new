package com.wecombft.application.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class FulfillmentApplicationServiceStructureTest {

    @Test
    void should_not_expose_inventory_management_payloads() {
        assertThat(Arrays.stream(FulfillmentApplicationService.class.getDeclaredClasses())
            .map(Class::getSimpleName))
            .doesNotContain(
                "CreationResult",
                "SkuCommand",
                "SkuPage",
                "SkuResponse",
                "StockFlowCommand",
                "StockFlowPage",
                "SkuRow");
    }

    @Test
    void should_not_keep_inventory_management_helpers() {
        assertThat(Arrays.stream(FulfillmentApplicationService.class.getDeclaredMethods())
            .map(Method::getName))
            .doesNotContain(
                "findSku",
                "findSkuByIdempotencyKey",
                "fromJson",
                "sha256Hex");
    }
}

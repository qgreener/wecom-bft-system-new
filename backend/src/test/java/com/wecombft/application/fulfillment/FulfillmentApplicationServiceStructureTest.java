package com.wecombft.application.fulfillment;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

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

    @Test
    void should_not_embed_fulfillment_command_or_response_payloads() {
        assertThat(Arrays.stream(FulfillmentApplicationService.class.getDeclaredClasses())
            .map(Class::getSimpleName))
            .doesNotContain(
                "ShipCommand",
                "SignCommand",
                "LogisticsTraceCommand",
                "ShipmentPage",
                "ShipmentListItem",
                "ShipmentActionResponse",
                "ShipmentDetailResponse",
                "ShipmentItemResponse",
                "LogisticsTraceResponse",
                "LogisticsCallbackResponse",
                "StockFlowResponse",
                "DocumentLinkResponse");
    }

    @Test
    void should_place_fulfillment_commands_and_dtos_under_documented_packages() {
        assertThat(packageOf("com.wecombft.application.command.fulfillment.ShipCommand"))
            .contains("com.wecombft.application.command.fulfillment");
        assertThat(packageOf("com.wecombft.application.command.fulfillment.SignCommand"))
            .contains("com.wecombft.application.command.fulfillment");
        assertThat(packageOf("com.wecombft.application.command.fulfillment.LogisticsTraceCommand"))
            .contains("com.wecombft.application.command.fulfillment");
        assertThat(packageOf("com.wecombft.interfaces.dto.fulfillment.ShipmentPage"))
            .contains("com.wecombft.interfaces.dto.fulfillment");
        assertThat(packageOf("com.wecombft.interfaces.dto.fulfillment.ShipmentActionResponse"))
            .contains("com.wecombft.interfaces.dto.fulfillment");
        assertThat(packageOf("com.wecombft.interfaces.dto.fulfillment.LogisticsCallbackResponse"))
            .contains("com.wecombft.interfaces.dto.fulfillment");
        assertThat(packageOf("com.wecombft.application.fulfillment.response.ShipmentPage")).isEmpty();
        assertThat(packageOf("com.wecombft.application.fulfillment.command.ShipCommand")).isEmpty();
    }

    private Optional<String> packageOf(String className) {
        try {
            return Optional.of(Class.forName(className).getPackageName());
        } catch (ClassNotFoundException exception) {
            return Optional.empty();
        }
    }
}

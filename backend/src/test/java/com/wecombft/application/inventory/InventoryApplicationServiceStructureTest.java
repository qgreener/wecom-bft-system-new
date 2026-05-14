package com.wecombft.application.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class InventoryApplicationServiceStructureTest {

    @Test
    void should_not_embed_inventory_command_or_response_payloads() {
        assertThat(Arrays.stream(InventoryApplicationService.class.getDeclaredClasses())
            .map(Class::getSimpleName))
            .doesNotContain(
                "SkuCommand",
                "SkuPage",
                "SkuResponse",
                "StockFlowCommand",
                "StockFlowPage",
                "StockFlowResponse");
    }

    @Test
    void should_place_inventory_commands_and_dtos_under_documented_packages() {
        assertThat(packageOf("com.wecombft.application.command.inventory.SkuCommand"))
            .contains("com.wecombft.application.command.inventory");
        assertThat(packageOf("com.wecombft.application.command.inventory.StockFlowCommand"))
            .contains("com.wecombft.application.command.inventory");
        assertThat(packageOf("com.wecombft.interfaces.dto.inventory.SkuPage"))
            .contains("com.wecombft.interfaces.dto.inventory");
        assertThat(packageOf("com.wecombft.interfaces.dto.inventory.SkuResponse"))
            .contains("com.wecombft.interfaces.dto.inventory");
        assertThat(packageOf("com.wecombft.interfaces.dto.inventory.StockFlowPage"))
            .contains("com.wecombft.interfaces.dto.inventory");
        assertThat(packageOf("com.wecombft.interfaces.dto.inventory.StockFlowResponse"))
            .contains("com.wecombft.interfaces.dto.inventory");
    }

    private Optional<String> packageOf(String className) {
        try {
            return Optional.of(Class.forName(className).getPackageName());
        } catch (ClassNotFoundException exception) {
            return Optional.empty();
        }
    }
}

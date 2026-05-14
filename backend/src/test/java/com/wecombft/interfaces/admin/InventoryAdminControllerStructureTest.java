package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.wecombft.application.inventory.InventoryApplicationService;

class InventoryAdminControllerStructureTest {

    @Test
    void should_depend_on_inventory_application_service_instead_of_supply_chain_service() {
        assertThat(Arrays.stream(InventoryAdminController.class.getDeclaredConstructors())
            .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
            .contains(InventoryApplicationService.class)
            .noneMatch(type -> type.getSimpleName().equals("SupplyChainApplicationService"));

        assertThat(Arrays.stream(InventoryAdminController.class.getDeclaredFields()).map(Field::getType))
            .contains(InventoryApplicationService.class)
            .noneMatch(type -> type.getSimpleName().equals("SupplyChainApplicationService"));
    }
}

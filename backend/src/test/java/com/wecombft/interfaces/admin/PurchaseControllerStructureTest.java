package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.wecombft.interfaces.supplier.SupplierPurchaseController;

class PurchaseControllerStructureTest {

    @Test
    void admin_purchase_controller_should_depend_on_purchase_application_service() {
        assertUsesPurchaseService(PurchaseAdminController.class);
    }

    @Test
    void supplier_purchase_controller_should_depend_on_purchase_application_service() {
        assertUsesPurchaseService(SupplierPurchaseController.class);
    }

    private static void assertUsesPurchaseService(Class<?> controllerType) {
        assertThat(dependenciesOf(controllerType))
            .anyMatch(type -> type.getSimpleName().equals("PurchaseApplicationService"))
            .noneMatch(type -> type.getSimpleName().equals("SupplyChainApplicationService"));
    }

    private static Stream<Class<?>> dependenciesOf(Class<?> type) {
        return Stream.concat(
            Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())),
            Arrays.stream(type.getDeclaredFields()).map(Field::getType));
    }
}

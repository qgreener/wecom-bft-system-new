package com.wecombft.interfaces.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.wecombft.interfaces.callback.LogisticsCallbackController;

class FulfillmentControllerStructureTest {

    @Test
    void shipment_admin_controller_should_depend_on_fulfillment_application_service() {
        assertUsesFulfillmentService(ShipmentAdminController.class);
    }

    @Test
    void logistics_callback_controller_should_depend_on_fulfillment_application_service() {
        assertUsesFulfillmentService(LogisticsCallbackController.class);
    }

    private static void assertUsesFulfillmentService(Class<?> controllerType) {
        assertThat(dependenciesOf(controllerType))
            .anyMatch(type -> type.getSimpleName().equals("FulfillmentApplicationService"))
            .noneMatch(type -> type.getSimpleName().equals("SupplyChainApplicationService"));
    }

    private static Stream<Class<?>> dependenciesOf(Class<?> type) {
        return Stream.concat(
            Arrays.stream(type.getDeclaredConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())),
            Arrays.stream(type.getDeclaredFields()).map(Field::getType));
    }
}

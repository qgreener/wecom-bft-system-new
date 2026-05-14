package com.wecombft.application.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class PurchaseApplicationServiceStructureTest {

    @Test
    void should_not_embed_purchase_command_or_response_payloads() {
        assertThat(Arrays.stream(PurchaseApplicationService.class.getDeclaredClasses())
            .map(Class::getSimpleName))
            .doesNotContain(
                "PurchaseCreateCommand",
                "PurchaseItemCommand",
                "ApprovalActionCommand",
                "SupplierConfirmCommand",
                "SupplierRejectCommand",
                "SupplierLogisticsCommand",
                "PurchaseReceiveCommand",
                "ReceivedItemCommand",
                "PurchaseInputInvoiceCommand",
                "PurchasePage",
                "PurchaseSummary",
                "PurchaseResponse",
                "PurchaseItemResponse",
                "PurchaseReceiptResponse",
                "StockFlowResponse",
                "ReceiptRow");
    }

    @Test
    void should_place_purchase_commands_and_dtos_under_documented_packages() {
        assertThat(packageOf("com.wecombft.application.command.purchase.PurchaseCreateCommand"))
            .contains("com.wecombft.application.command.purchase");
        assertThat(packageOf("com.wecombft.application.command.purchase.ApprovalActionCommand"))
            .contains("com.wecombft.application.command.purchase");
        assertThat(packageOf("com.wecombft.application.command.purchase.SupplierConfirmCommand"))
            .contains("com.wecombft.application.command.purchase");
        assertThat(packageOf("com.wecombft.interfaces.dto.purchase.PurchasePage"))
            .contains("com.wecombft.interfaces.dto.purchase");
        assertThat(packageOf("com.wecombft.interfaces.dto.purchase.PurchaseResponse"))
            .contains("com.wecombft.interfaces.dto.purchase");
        assertThat(packageOf("com.wecombft.interfaces.dto.purchase.PurchaseReceiptResponse"))
            .contains("com.wecombft.interfaces.dto.purchase");
        assertThat(packageOf("com.wecombft.application.purchase.response.PurchasePage")).isEmpty();
        assertThat(packageOf("com.wecombft.application.purchase.command.PurchaseCreateCommand")).isEmpty();
    }

    private Optional<String> packageOf(String className) {
        try {
            return Optional.of(Class.forName(className).getPackageName());
        } catch (ClassNotFoundException exception) {
            return Optional.empty();
        }
    }
}

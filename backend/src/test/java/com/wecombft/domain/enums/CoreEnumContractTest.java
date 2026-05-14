package com.wecombft.domain.enums;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class CoreEnumContractTest {

    @Test
    void should_keep_order_state_codes_split_by_business_dimension() {
        assertCodes(PaymentStatus.class, "PENDING", "PAID", "CLOSED");
        assertCodes(FulfillmentStatus.class, "NO_SHIPMENT", "PENDING_SHIPMENT", "SHIPPED", "SIGNED");
        assertCodes(OrderRefundStatus.class,
                "NONE",
                "REVIEWING",
                "REJECTED",
                "PROCESSING",
                "MANUAL_REQUIRED",
                "FAILED",
                "REFUNDED");
        assertCodes(OrderInvoiceStatus.class, "NOT_APPLIED", "APPLIED", "TO_BE_ISSUED", "ISSUED", "RED_REVERSED");
    }

    @Test
    void should_keep_child_document_status_codes_aligned_with_documents() {
        assertCodes(PaymentResult.class, "SUCCESS", "FAILED", "PROCESSING", "CLOSED");
        assertCodes(ShipmentStatus.class, "PENDING_SHIPMENT", "SHIPPED", "SIGNED");
        assertCodes(EntitlementStatus.class, "ACTIVE", "FROZEN", "REVOKED");
        assertCodes(PurchaseStatus.class,
                "APPROVING",
                "APPROVAL_REJECTED",
                "WAIT_CONFIRM",
                "CONFIRMED",
                "SHIPPED",
                "REJECTED",
                "COMPLETED",
                "CANCELED");
        assertCodes(InputInvoiceStatus.class, "NOT_INVOICED", "INVOICED");
        assertCodes(ReconciliationResult.class, "MATCHED", "AMOUNT_DIFF", "FEE_DIFF", "UNMATCHED", "DUPLICATE");
        assertCodes(AccountingMaterialStatus.class, "PENDING_SUPPLEMENT", "UPLOADED", "CONFIRMED", "CLOSED");
    }

    @Test
    void should_keep_collaboration_and_audit_status_codes_aligned_with_documents() {
        assertCodes(LeadStatus.class, "PENDING_FOLLOW", "CONTACTED", "CONVERTED", "ABANDONED");
        assertCodes(CourseStatus.class, "DRAFT", "PENDING_REVIEW", "ON_SHELF", "OFF_SHELF", "DELETE_PENDING", "DELETED");
        assertCodes(SpecStatus.class, "ENABLED", "DISABLED");
        assertCodes(LessonNodeType.class, "CHAPTER", "LESSON");
        assertCodes(LessonStatus.class, "DRAFT", "PUBLISHED", "HIDDEN");
        assertCodes(NotificationChannel.class, "IN_APP", "WECHAT_SUBSCRIBE", "WECOM_CARD");
        assertCodes(SendStatus.class, "PENDING", "SENT", "FAILED", "CANCELED");
        assertCodes(ReadStatus.class, "UNREAD", "READ");
        assertCodes(ApprovalStatus.class, "PENDING", "APPROVED", "REJECTED", "CANCELED");
        assertCodes(OperationResult.class, "SUCCESS", "FAILED");
    }

    private <E extends Enum<E> & CodedEnum> void assertCodes(Class<E> enumType, String... expectedCodes) {
        List<String> actualCodes = Arrays.stream(enumType.getEnumConstants()).map(CodedEnum::code).toList();
        assertThat(actualCodes).containsExactly(expectedCodes);
        assertThat(actualCodes).allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
    }
}

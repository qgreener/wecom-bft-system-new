package com.wecombft.infrastructure.mock;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Function;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.wecombft.application.command.finance.InvoiceIssueCallbackCommand;
import com.wecombft.application.command.finance.RefundCallbackCommand;
import com.wecombft.application.finance.AfterSalesFinanceApplicationService;
import com.wecombft.interfaces.dto.finance.InvoiceCallbackResponse;
import com.wecombft.interfaces.dto.finance.RefundCallbackResponse;

@Component
public class MockFinanceScenes {

    private final MockSceneRegistry registry;
    private final AfterSalesFinanceApplicationService financeService;

    public MockFinanceScenes(MockSceneRegistry registry, AfterSalesFinanceApplicationService financeService) {
        this.registry = registry;
        this.financeService = financeService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void registerScenes() {
        registry.register(new MockSceneDefinition(
            "REFUND_CALLBACK_SUCCESS",
            "REFUND",
            "退款回调成功",
            "向退款单投递成功回调，触发权益冻结/撤销和单据链回写",
            "{\"refund_no\":\"REPLACE_WITH_REFUND_NO\",\"refunded_amount_cent\":0}",
            true,
            refundSuccessDispatcher()
        ));
        registry.register(new MockSceneDefinition(
            "REFUND_CALLBACK_FAILED",
            "REFUND",
            "退款回调失败",
            "向退款单投递失败回调，状态置 FAILED 等待补偿",
            "{\"refund_no\":\"REPLACE_WITH_REFUND_NO\",\"failure_reason\":\"Mock 失败\"}",
            true,
            refundFailedDispatcher()
        ));
        registry.register(new MockSceneDefinition(
            "INVOICE_ISSUE_FAILED",
            "INVOICE_ISSUE",
            "开票回调失败",
            "向发票申请投递自动开票失败回调，状态转 TO_BE_ISSUED",
            "{\"invoice_apply_no\":\"REPLACE_WITH_INVOICE_APPLY_NO\",\"failure_reason\":\"Mock 开票失败\"}",
            true,
            invoiceFailedDispatcher()
        ));
    }

    private Function<MockTriggerContext, MockDispatchResult> refundSuccessDispatcher() {
        return context -> {
            Map<String, Object> override = context.payloadOverride();
            String refundNo = stringOrTarget(override, "refund_no", context.targetNo());
            Long amount = longValue(override, "refunded_amount_cent");
            RefundCallbackCommand command = new RefundCallbackCommand(
                context.eventNo(),
                refundNo,
                "MOCK_EXT_" + context.eventNo(),
                "SUCCESS",
                amount == null ? 0L : amount,
                LocalDateTime.now(),
                null,
                Map.of("scenario", "mock_refund_success", "mock_event_no", context.eventNo()));
            RefundCallbackResponse response = financeService.handleWechatRefundCallback(command);
            return new MockDispatchResult(
                "/api/callbacks/refunds/wechat",
                response.processingStatus(),
                context.eventNo(),
                "Refund success delivered");
        };
    }

    private Function<MockTriggerContext, MockDispatchResult> refundFailedDispatcher() {
        return context -> {
            Map<String, Object> override = context.payloadOverride();
            String refundNo = stringOrTarget(override, "refund_no", context.targetNo());
            String failureReason = stringValue(override, "failure_reason", "Mock 退款失败");
            RefundCallbackCommand command = new RefundCallbackCommand(
                context.eventNo(),
                refundNo,
                "MOCK_EXT_" + context.eventNo(),
                "FAILED",
                0L,
                LocalDateTime.now(),
                failureReason,
                Map.of("scenario", "mock_refund_failed", "mock_event_no", context.eventNo()));
            RefundCallbackResponse response = financeService.handleWechatRefundCallback(command);
            return new MockDispatchResult(
                "/api/callbacks/refunds/wechat",
                response.processingStatus(),
                context.eventNo(),
                "Refund failure delivered");
        };
    }

    private Function<MockTriggerContext, MockDispatchResult> invoiceFailedDispatcher() {
        return context -> {
            Map<String, Object> override = context.payloadOverride();
            String invoiceApplyNo = stringOrTarget(override, "invoice_apply_no", context.targetNo());
            String failureReason = stringValue(override, "failure_reason", "Mock 开票失败");
            InvoiceIssueCallbackCommand command = new InvoiceIssueCallbackCommand(
                context.eventNo(),
                invoiceApplyNo,
                "FAILED",
                null,
                null,
                LocalDateTime.now(),
                failureReason,
                Map.of("scenario", "mock_invoice_failed", "mock_event_no", context.eventNo()));
            InvoiceCallbackResponse response = financeService.handleInvoiceIssueCallback(command);
            return new MockDispatchResult(
                "/api/callbacks/invoices/issue",
                response.processingStatus(),
                context.eventNo(),
                "Invoice issue failure delivered");
        };
    }

    private static String stringOrTarget(Map<String, Object> override, String key, String fallback) {
        Object value = override == null ? null : override.get(key);
        return value == null ? fallback : value.toString();
    }

    private static String stringValue(Map<String, Object> override, String key, String fallback) {
        Object value = override == null ? null : override.get(key);
        return value == null ? fallback : value.toString();
    }

    private static Long longValue(Map<String, Object> override, String key) {
        Object value = override == null ? null : override.get(key);
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s); } catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }
}

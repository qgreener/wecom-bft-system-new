package com.wecombft.application.trade;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wecombft.application.audit.AuditLogService;
import com.wecombft.application.crm.LeadApplicationService;
import com.wecombft.application.crm.LeadApplicationService.LeadPaidConversionCommand;
import com.wecombft.application.learning.LearningEntitlementService;
import com.wecombft.application.learning.LearningEntitlementService.EntitlementEventResult;
import com.wecombft.application.learning.LearningEntitlementService.PaymentSuccessEntitlementCommand;
import com.wecombft.application.student.AppStudentApplicationService;
import com.wecombft.application.student.AppStudentApplicationService.StudentSession;
import com.wecombft.domain.model.MoneyCent;
import com.wecombft.domain.service.finance.TaxAmountCalculator;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRecord;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository;
import com.wecombft.infrastructure.persistence.integration.CallbackEventRepository.CallbackEventCommand;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRecord;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository;
import com.wecombft.infrastructure.persistence.trade.OrderDocumentLinkRepository.DocumentLinkCommand;
import com.wecombft.infrastructure.security.AdminPrincipal;
import com.wecombft.shared.id.IdGenerator;
import com.wecombft.shared.web.ApiException;

@Service
public class OrderPaymentApplicationService {

    private static final int DEFAULT_PAGE_NO = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;
    private final ObjectMapper objectMapper;
    private final AppStudentApplicationService appStudentApplicationService;
    private final LearningEntitlementService learningEntitlementService;
    private final LeadApplicationService leadApplicationService;
    private final CallbackEventRepository callbackEventRepository;
    private final OrderDocumentLinkRepository documentLinkRepository;
    private final AuditLogService auditLogService;

    public OrderPaymentApplicationService(
        JdbcTemplate jdbcTemplate,
        IdGenerator idGenerator,
        ObjectMapper objectMapper,
        AppStudentApplicationService appStudentApplicationService,
        LearningEntitlementService learningEntitlementService,
        LeadApplicationService leadApplicationService,
        CallbackEventRepository callbackEventRepository,
        OrderDocumentLinkRepository documentLinkRepository,
        AuditLogService auditLogService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
        this.objectMapper = objectMapper;
        this.appStudentApplicationService = appStudentApplicationService;
        this.learningEntitlementService = learningEntitlementService;
        this.leadApplicationService = leadApplicationService;
        this.callbackEventRepository = callbackEventRepository;
        this.documentLinkRepository = documentLinkRepository;
        this.auditLogService = auditLogService;
    }

    public OrderConfirmResponse confirm(String authorizationHeader, OrderConfirmCommand command) {
        StudentSession student = requireTradeReadyStudent(authorizationHeader);
        OrderConfirmation confirmation = buildConfirmation(student, command);
        return confirmation.toResponse();
    }

    @Transactional
    public OrderCreateResponse create(String authorizationHeader, String idempotencyKey, OrderCreateCommand command) {
        StudentSession student = requireTradeReadyStudent(authorizationHeader);
        String normalizedIdempotencyKey = normalizeRequired(idempotencyKey, "缺少 Idempotency-Key");
        Optional<OrderRow> existing = findOrderByStudentAndIdempotency(student.studentId(), normalizedIdempotencyKey);
        if (existing.isPresent()) {
            assertSameCreateRequest(existing.get(), command);
            return toCreateResponse(existing.get());
        }

        OrderConfirmation confirmation = buildConfirmation(student, command.toConfirmCommand());
        if (command.confirmToken() == null || !command.confirmToken().equals(confirmation.confirmToken())) {
            auditLogService.writeSystemFailure(
                "TRADE",
                "ORDER_CREATE_PRICE_CHANGED",
                "TRADE_ORDER",
                null,
                null,
                null,
                "价格或快照已变化，需重新确认");
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "价格或快照已变化，请重新确认订单");
        }
        if (command.confirmedPayableAmountCent() == null
            || command.confirmedPayableAmountCent().longValue() != confirmation.payableAmountCent()) {
            auditLogService.writeSystemFailure(
                "TRADE",
                "ORDER_CREATE_AMOUNT_CHANGED",
                "TRADE_ORDER",
                null,
                null,
                null,
                "确认金额与当前金额不一致");
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "订单金额已变化，请重新确认订单");
        }

        long orderId = idGenerator.nextId();
        long orderItemId = idGenerator.nextId();
        String orderNo = "ORD" + orderId;
        String merchantOrderNo = "MOCKORD" + orderId;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.plusMinutes(30);
        String fulfillmentStatus = confirmation.containsPhysical() ? "PENDING_SHIPMENT" : "NO_SHIPMENT";

        try {
            jdbcTemplate.update(
                """
                insert into trade_order (
                    id, order_no, merchant_order_no, student_id, user_id, lead_id, source_channel,
                    client_request_no, idempotency_key, confirm_token,
                    total_amount_cent, discount_amount_cent, payable_amount_cent, paid_amount_cent,
                    course_snapshot, price_snapshot, tax_snapshot, receiver_snapshot,
                    payment_status, fulfillment_status, refund_status, invoice_status,
                    payment_expire_at, created_by, updated_by
                ) values (?, ?, ?, ?, ?, null, ?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?, ?, 'PENDING', ?, 'NONE',
                    'NOT_APPLIED', ?, 0, 0)
                """,
                orderId,
                orderNo,
                merchantOrderNo,
                student.studentId(),
                student.userId(),
                normalizeSourceChannel(command.sourceChannel()),
                command.clientRequestNo(),
                normalizedIdempotencyKey,
                confirmation.confirmToken(),
                confirmation.totalAmountCent(),
                confirmation.discountAmountCent(),
                confirmation.payableAmountCent(),
                confirmation.courseSnapshotJson(),
                confirmation.priceSnapshotJson(),
                confirmation.taxSnapshotJson(),
                confirmation.receiverSnapshotJson(),
                fulfillmentStatus,
                expireAt);
            jdbcTemplate.update(
                """
                insert into trade_order_item (
                    id, order_id, order_no, line_no, course_id, spec_id, item_name, quantity,
                    unit_price_cent, total_amount_cent, discount_amount_cent, payable_amount_cent,
                    paid_amount_cent, contains_physical, sku_id, gift_sku_id,
                    course_snapshot, spec_snapshot, tax_snapshot, created_by, updated_by
                ) values (?, ?, ?, 1, ?, ?, ?, ?, ?, ?, 0, ?, null, ?, ?, ?, ?, ?, ?, 0, 0)
                """,
                orderItemId,
                orderId,
                orderNo,
                confirmation.course().courseId(),
                confirmation.spec().specId(),
                confirmation.spec().specName(),
                confirmation.quantity(),
                confirmation.spec().salePriceCent(),
                confirmation.totalAmountCent(),
                confirmation.payableAmountCent(),
                confirmation.containsPhysical() ? 1 : 0,
                confirmation.spec().skuId(),
                confirmation.spec().giftSkuId(),
                confirmation.courseSnapshotJson(),
                confirmation.specSnapshotJson(),
                confirmation.taxSnapshotJson());
        } catch (DuplicateKeyException duplicateKeyException) {
            return findOrderByStudentAndIdempotency(student.studentId(), normalizedIdempotencyKey)
                .map(this::toCreateResponse)
                .orElseThrow(() -> duplicateKeyException);
        }

        auditLogService.writeSystemSuccess(
            "TRADE",
            "ORDER_CREATE",
            "TRADE_ORDER",
            orderId,
            orderNo,
            orderId,
            "{\"payment_status\":\"PENDING\",\"payable_amount_cent\":" + confirmation.payableAmountCent() + "}");
        return toCreateResponse(requireOrder(orderId));
    }

    @Transactional
    public PaymentPrepareResponse preparePayment(String authorizationHeader, long orderId, PayCommand command) {
        StudentSession student = requireTradeReadyStudent(authorizationHeader);
        OrderRow order = requireStudentOrder(orderId, student.studentId());
        order = closeExpiredIfNeeded(order);
        if (!"PENDING".equals(order.paymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "订单已关闭或已支付，不能继续支付");
        }

        Map<String, Object> paymentParams = new LinkedHashMap<>();
        paymentParams.put("mode", "MOCK");
        paymentParams.put("payment_channel", normalizePaymentChannel(command == null ? null : command.paymentChannel()));
        paymentParams.put("merchant_order_no", order.merchantOrderNo());
        paymentParams.put("payable_amount_cent", order.payableAmountCent());
        paymentParams.put("callback_path", "/api/callbacks/payments/wechat");
        paymentParams.put("mock_pay_path", "/api/app/orders/" + order.id() + "/mock-pay");
        paymentParams.put("nonce", "MOCK-" + order.id());

        auditLogService.writeSystemSuccess(
            "PAYMENT",
            "PAYMENT_PREPARE",
            "TRADE_ORDER",
            order.id(),
            order.orderNo(),
            order.id(),
            "{\"merchant_order_no\":\"" + order.merchantOrderNo() + "\"}");
        return new PaymentPrepareResponse(
            order.id(),
            order.orderNo(),
            order.merchantOrderNo(),
            paymentParams,
            order.paymentExpireAt(),
            LocalDateTime.now());
    }

    @Transactional
    public OrderCloseResponse cancel(String authorizationHeader, long orderId, CancelOrderCommand command) {
        StudentSession student = requireTradeReadyStudent(authorizationHeader);
        OrderRow order = requireStudentOrder(orderId, student.studentId());
        if ("PAID".equals(order.paymentStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "已支付订单不可取消");
        }
        if ("CLOSED".equals(order.paymentStatus())) {
            return new OrderCloseResponse(order.id(), order.orderNo(), order.paymentStatus(), order.closedAt(), order.closeReason());
        }

        String reason = command == null || command.closeReason() == null || command.closeReason().isBlank()
            ? "USER_CANCEL"
            : command.closeReason().trim();
        LocalDateTime closedAt = LocalDateTime.now();
        jdbcTemplate.update(
            """
            update trade_order
            set payment_status = 'CLOSED',
                closed_at = ?,
                close_reason = ?,
                updated_by = 0,
                version = version + 1
            where id = ? and payment_status = 'PENDING'
            """,
            closedAt,
            reason,
            order.id());
        auditLogService.writeSystemSuccess(
            "TRADE",
            "ORDER_CANCEL",
            "TRADE_ORDER",
            order.id(),
            order.orderNo(),
            order.id(),
            "{\"payment_status\":\"CLOSED\",\"close_reason\":\"" + jsonSafe(reason) + "\"}");
        return new OrderCloseResponse(order.id(), order.orderNo(), "CLOSED", closedAt, reason);
    }

    @Transactional
    public PaymentCallbackResponse mockPay(String authorizationHeader, long orderId, MockPayCommand command) {
        StudentSession student = requireTradeReadyStudent(authorizationHeader);
        OrderRow order = requireStudentOrder(orderId, student.studentId());
        PaymentCallbackCommand callbackCommand = new PaymentCallbackCommand(
            defaultString(command == null ? null : command.eventNo(), "MOCK_PAY_" + order.id()),
            order.merchantOrderNo(),
            defaultString(command == null ? null : command.externalPaymentNo(), "MOCKPAY-" + order.id()),
            command == null || command.paidAmountCent() == null ? order.payableAmountCent() : command.paidAmountCent(),
            defaultString(command == null ? null : command.paymentResult(), "SUCCESS"),
            command == null || command.paidAt() == null ? LocalDateTime.now() : command.paidAt(),
            command == null ? Map.of("mock", true) : command.rawSnapshot());
        return handlePaymentCallback(callbackCommand, "WECHAT_PAY_MOCK");
    }

    @Transactional
    public PaymentCallbackResponse handleWechatPaymentCallback(PaymentCallbackCommand command) {
        return handlePaymentCallback(command, "WECHAT_PAY");
    }

    @Transactional
    public PaymentCallbackResponse handlePaymentCallback(PaymentCallbackCommand command, String sourceSystem) {
        validateCallback(command);
        String eventNo = command.eventNo().trim();
        String merchantOrderNo = command.merchantOrderNo().trim();
        String externalPaymentNo = command.externalPaymentNo().trim();
        String idempotencyKey = paymentIdempotencyKey(merchantOrderNo, externalPaymentNo);
        Optional<OrderRow> orderOptional = findOrderByMerchantOrderNo(merchantOrderNo);
        Long orderId = orderOptional.map(OrderRow::id).orElse(null);
        String rawSnapshot = toJson(command.rawSnapshot() == null ? Map.of("event_no", eventNo) : command.rawSnapshot());

        CallbackEventRecord callbackEvent = callbackEventRepository.recordReceived(new CallbackEventCommand(
            idGenerator.nextId(),
            eventNo,
            sourceSystem,
            "PAYMENT_RESULT",
            idempotencyKey,
            orderId,
            "PAYMENT",
            orderId,
            merchantOrderNo,
            rawSnapshot));
        if (!"PENDING".equals(callbackEvent.processingStatus())) {
            return responseFromCallbackRecord(callbackEvent, orderOptional.orElse(null), idempotencyKey);
        }
        if (orderOptional.isEmpty()) {
            callbackEventRepository.markFailed(callbackEvent.id(), "商户订单号不存在");
            auditLogService.writeSystemFailure(
                "PAYMENT",
                "PAYMENT_CALLBACK_ORDER_NOT_FOUND",
                "PAYMENT",
                null,
                merchantOrderNo,
                null,
                "商户订单号不存在");
            return responseFromCallbackRecord(
                callbackEventRepository.findBySourceAndEventNo(sourceSystem, eventNo).orElseThrow(),
                null,
                idempotencyKey);
        }

        OrderRow order = orderOptional.get();
        Optional<PaymentRow> existingPayment = findPaymentByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            callbackEventRepository.markProcessed(callbackEvent.id());
            return responseFromCallbackRecord(
                callbackEventRepository.findBySourceAndEventNo(sourceSystem, eventNo).orElseThrow(),
                requireOrder(order.id()),
                idempotencyKey);
        }
        if (!"SUCCESS".equalsIgnoreCase(command.paymentResult())) {
            callbackEventRepository.markProcessed(callbackEvent.id());
            auditLogService.writeSystemSuccess(
                "PAYMENT",
                "PAYMENT_CALLBACK_NON_SUCCESS",
                "TRADE_ORDER",
                order.id(),
                order.orderNo(),
                order.id(),
                "{\"payment_result\":\"" + jsonSafe(command.paymentResult()) + "\"}");
            return responseFromCallbackRecord(
                callbackEventRepository.findBySourceAndEventNo(sourceSystem, eventNo).orElseThrow(),
                order,
                idempotencyKey);
        }
        if (!"PENDING".equals(order.paymentStatus())) {
            callbackEventRepository.markFailed(callbackEvent.id(), "订单状态不是待支付，需人工核对");
            auditLogService.writeSystemFailure(
                "PAYMENT",
                "PAYMENT_CALLBACK_STATE_CONFLICT",
                "TRADE_ORDER",
                order.id(),
                order.orderNo(),
                order.id(),
                "订单状态不是待支付");
            return responseFromCallbackRecord(
                callbackEventRepository.findBySourceAndEventNo(sourceSystem, eventNo).orElseThrow(),
                order,
                idempotencyKey);
        }
        if (command.paidAmountCent() == null || command.paidAmountCent().longValue() != order.payableAmountCent()) {
            callbackEventRepository.markFailed(callbackEvent.id(), "支付金额不一致，需人工核对");
            auditLogService.writeSystemFailure(
                "PAYMENT",
                "PAYMENT_AMOUNT_MISMATCH",
                "TRADE_ORDER",
                order.id(),
                order.orderNo(),
                order.id(),
                "支付金额不一致");
            return responseFromCallbackRecord(
                callbackEventRepository.findBySourceAndEventNo(sourceSystem, eventNo).orElseThrow(),
                order,
                idempotencyKey);
        }

        PaymentRow payment = insertSuccessPayment(order, command, callbackEvent, idempotencyKey, sourceSystem, rawSnapshot);
        LocalDateTime paidAt = command.paidAt() == null ? LocalDateTime.now() : command.paidAt();
        jdbcTemplate.update(
            """
            update trade_order
            set payment_status = 'PAID',
                paid_amount_cent = ?,
                paid_at = ?,
                updated_by = 0,
                version = version + 1
            where id = ? and payment_status = 'PENDING'
            """,
            command.paidAmountCent(),
            paidAt,
            order.id());
        jdbcTemplate.update(
            """
            update trade_order_item
            set paid_amount_cent = payable_amount_cent,
                updated_by = 0,
                version = version + 1
            where order_id = ?
            """,
            order.id());
        auditLogService.writeSystemSuccess(
            "PAYMENT",
            "PAYMENT_CALLBACK_SUCCESS",
            "TRADE_ORDER",
            order.id(),
            order.orderNo(),
            order.id(),
            "{\"payment_status\":\"PAID\",\"payment_no\":\"" + payment.paymentNo() + "\"}");
        String sideEffectFailure = runPaymentSuccessSideEffects(requireOrder(order.id()), payment, paidAt);
        if (sideEffectFailure == null) {
            callbackEventRepository.markProcessed(callbackEvent.id());
        } else {
            callbackEventRepository.markFailed(callbackEvent.id(), sideEffectFailure);
        }
        return responseFromCallbackRecord(
            callbackEventRepository.findBySourceAndEventNo(sourceSystem, eventNo).orElseThrow(),
            requireOrder(order.id()),
            idempotencyKey);
    }

    public OrderPage appOrders(
        String authorizationHeader,
        String paymentStatus,
        String fulfillmentStatus,
        String refundStatus,
        String invoiceStatus,
        Integer pageNo,
        Integer pageSize
    ) {
        StudentSession student = requireTradeReadyStudent(authorizationHeader);
        return queryOrders(
            "o.student_id = ?",
            List.of(student.studentId()),
            paymentStatus,
            fulfillmentStatus,
            refundStatus,
            invoiceStatus,
            pageNo,
            pageSize);
    }

    public OrderDetailResponse appOrderDetail(String authorizationHeader, long orderId) {
        StudentSession student = requireTradeReadyStudent(authorizationHeader);
        OrderRow order = requireStudentOrder(orderId, student.studentId());
        return toDetailResponse(order, false);
    }

    public OrderPage adminOrders(
        AdminPrincipal principal,
        String keyword,
        Long studentId,
        String paymentStatus,
        String fulfillmentStatus,
        String refundStatus,
        String invoiceStatus,
        Integer pageNo,
        Integer pageSize
    ) {
        List<Object> args = new ArrayList<>();
        StringBuilder condition = new StringBuilder("1 = 1");
        if (studentId != null) {
            condition.append(" and o.student_id = ?");
            args.add(studentId);
        }
        if (keyword != null && !keyword.isBlank()) {
            condition.append(" and (o.order_no like ? or o.merchant_order_no like ?)");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (principal != null && principal.roleCodes().contains("WAREHOUSE")
            && !principal.roleCodes().contains("SUPER_ADMIN")) {
            condition.append(" and o.payment_status = 'PAID' and o.fulfillment_status <> 'NO_SHIPMENT'");
        }
        return queryOrders(
            condition.toString(),
            args,
            paymentStatus,
            fulfillmentStatus,
            refundStatus,
            invoiceStatus,
            pageNo,
            pageSize);
    }

    public OrderDetailResponse adminOrderDetail(AdminPrincipal principal, long orderId) {
        OrderRow order = requireOrder(orderId);
        if (principal != null && principal.roleCodes().contains("WAREHOUSE")
            && !principal.roleCodes().contains("SUPER_ADMIN")
            && (!"PAID".equals(order.paymentStatus()) || "NO_SHIPMENT".equals(order.fulfillmentStatus()))) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权查看该订单");
        }
        return toDetailResponse(order, true);
    }

    private OrderPage queryOrders(
        String baseCondition,
        List<Object> baseArgs,
        String paymentStatus,
        String fulfillmentStatus,
        String refundStatus,
        String invoiceStatus,
        Integer pageNo,
        Integer pageSize
    ) {
        StringBuilder sql = new StringBuilder(
            """
            select o.id, o.order_no, o.merchant_order_no, o.student_id, o.user_id, o.lead_id,
                   o.source_channel, o.client_request_no, o.idempotency_key, o.confirm_token,
                   o.total_amount_cent, o.discount_amount_cent, o.payable_amount_cent, o.paid_amount_cent,
                   o.course_snapshot, o.price_snapshot, o.tax_snapshot, o.receiver_snapshot,
                   o.payment_status, o.fulfillment_status, o.refund_status, o.invoice_status,
                   o.payment_expire_at, o.paid_at, o.closed_at, o.close_reason, o.created_at
            from trade_order o
            where
            """).append(' ').append(baseCondition);
        List<Object> args = new ArrayList<>(baseArgs);
        appendEnumFilter(sql, args, "o.payment_status", paymentStatus, List.of("PENDING", "PAID", "CLOSED"));
        appendEnumFilter(sql, args, "o.fulfillment_status", fulfillmentStatus, List.of("NO_SHIPMENT", "PENDING_SHIPMENT", "SHIPPED", "SIGNED"));
        appendEnumFilter(sql, args, "o.refund_status", refundStatus, List.of("NONE", "REVIEWING", "REJECTED", "PROCESSING", "MANUAL_REQUIRED", "FAILED", "REFUNDED"));
        appendEnumFilter(sql, args, "o.invoice_status", invoiceStatus, List.of("NOT_APPLIED", "APPLIED", "TO_BE_ISSUED", "ISSUED", "RED_REVERSED"));
        sql.append(" order by o.created_at desc, o.id desc limit ? offset ?");
        int size = pageSize == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(pageSize, 100));
        int page = pageNo == null ? DEFAULT_PAGE_NO : Math.max(1, pageNo);
        args.add(size);
        args.add((page - 1) * size);
        List<OrderListItem> records = jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            OrderRow order = mapOrder(rs, rowNum);
            return new OrderListItem(
                order.id(),
                order.orderNo(),
                order.merchantOrderNo(),
                order.studentId(),
                order.userId(),
                order.leadId(),
                fromJson(order.courseSnapshotJson()),
                order.paidAmountCent(),
                order.payableAmountCent(),
                order.paymentStatus(),
                order.fulfillmentStatus(),
                order.refundStatus(),
                order.invoiceStatus(),
                order.paymentExpireAt(),
                LocalDateTime.now(),
                order.createdAt());
        }, args.toArray());
        return new OrderPage(records, page, size, records.size());
    }

    private OrderDetailResponse toDetailResponse(OrderRow order, boolean includeAuditLogs) {
        return new OrderDetailResponse(
            order.id(),
            order.orderNo(),
            order.merchantOrderNo(),
            order.studentId(),
            order.userId(),
            order.leadId(),
            findOrderItems(order.id()),
            fromJson(order.courseSnapshotJson()),
            fromJson(order.priceSnapshotJson()),
            fromJson(order.taxSnapshotJson()),
            fromJson(order.receiverSnapshotJson()),
            order.totalAmountCent(),
            order.discountAmountCent(),
            order.payableAmountCent(),
            order.paidAmountCent(),
            order.paymentStatus(),
            order.fulfillmentStatus(),
            order.refundStatus(),
            order.invoiceStatus(),
            order.paymentExpireAt(),
            order.paidAt(),
            order.closedAt(),
            order.closeReason(),
            findPayments(order.id()),
            findEntitlements(order.id()),
            findShipments(order.id()),
            findNotifications(order.id()),
            documentLinkRepository.findByOrderId(order.id()).stream().map(this::toDocumentLinkResponse).toList(),
            includeAuditLogs ? findAuditLogs(order.id()) : List.of());
    }

    private OrderConfirmation buildConfirmation(StudentSession student, OrderConfirmCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        long courseId = requirePositive(command.courseId(), "课程不能为空");
        long specId = requirePositive(command.specId(), "规格不能为空");
        int quantity = command.quantity() == null ? 1 : command.quantity();
        if (quantity <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "购买数量必须大于 0");
        }
        CourseSpecRow courseSpec = findSellableCourseSpec(courseId, specId)
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "课程或规格不可售"));
        TaxRuleRow taxRule = findTaxRule(courseSpec.taxRuleId() == null ? courseSpec.defaultTaxRuleId() : courseSpec.taxRuleId())
            .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "税务规则缺失"));
        AddressRow address = null;
        if (courseSpec.containsPhysical()) {
            if (command.addressId() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "含实物规格必须选择收货地址");
            }
            address = findStudentAddress(student.studentId(), command.addressId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "收货地址不存在或不可用"));
        } else if (command.addressId() != null) {
            address = findStudentAddress(student.studentId(), command.addressId()).orElse(null);
        }

        long totalAmountCent = courseSpec.salePriceCent() * quantity;
        long discountAmountCent = 0;
        long payableAmountCent = totalAmountCent - discountAmountCent;
        long taxAmountCent = TaxAmountCalculator.inclusiveTax(MoneyCent.of(payableAmountCent), taxRule.taxRate()).cent();
        boolean stockWarning = hasStockWarning(courseSpec, quantity);

        Map<String, Object> courseSnapshot = new LinkedHashMap<>();
        courseSnapshot.put("course_id", courseSpec.courseId());
        courseSnapshot.put("course_no", courseSpec.courseNo());
        courseSnapshot.put("course_title", courseSpec.courseTitle());
        courseSnapshot.put("course_type", courseSpec.courseType());
        courseSnapshot.put("teacher_user_id", courseSpec.teacherUserId());
        courseSnapshot.put("category_code", courseSpec.categoryCode());
        courseSnapshot.put("spec_id", courseSpec.specId());
        courseSnapshot.put("spec_no", courseSpec.specNo());
        courseSnapshot.put("spec_name", courseSpec.specName());
        courseSnapshot.put("contains_physical", courseSpec.containsPhysical());
        courseSnapshot.put("sku_id", courseSpec.skuId());
        courseSnapshot.put("gift_sku_id", courseSpec.giftSkuId());

        Map<String, Object> priceSnapshot = new LinkedHashMap<>();
        priceSnapshot.put("origin_price_cent", courseSpec.originPriceCent());
        priceSnapshot.put("sale_price_cent", courseSpec.salePriceCent());
        priceSnapshot.put("quantity", quantity);
        priceSnapshot.put("discount_amount_cent", discountAmountCent);
        priceSnapshot.put("shipping_amount_cent", 0);
        priceSnapshot.put("total_amount_cent", totalAmountCent);
        priceSnapshot.put("payable_amount_cent", payableAmountCent);
        priceSnapshot.put("gift_sku_id", courseSpec.giftSkuId());
        priceSnapshot.put("amount_split_snapshot", fromJson(courseSpec.amountSplitSnapshot()));

        Map<String, Object> taxSnapshot = new LinkedHashMap<>();
        taxSnapshot.put("tax_rule_id", taxRule.id());
        taxSnapshot.put("tax_rule_no", taxRule.ruleNo());
        taxSnapshot.put("tax_category", taxRule.taxCategory());
        taxSnapshot.put("tax_rate", taxRule.taxRate());
        taxSnapshot.put("invoice_item_name", taxRule.invoiceItemName());
        taxSnapshot.put("tax_amount_cent", taxAmountCent);
        taxSnapshot.put("tax_included_amount_cent", payableAmountCent);
        taxSnapshot.put("tax_excluded_amount_cent", payableAmountCent - taxAmountCent);

        Map<String, Object> receiverSnapshot = address == null ? null : address.toSnapshot();
        String token = confirmToken(courseSpec, taxRule, address, quantity, payableAmountCent);
        return new OrderConfirmation(
            courseSpec,
            taxRule,
            address,
            quantity,
            totalAmountCent,
            discountAmountCent,
            payableAmountCent,
            taxAmountCent,
            courseSpec.containsPhysical(),
            stockWarning,
            token,
            courseSnapshot,
            priceSnapshot,
            taxSnapshot,
            receiverSnapshot,
            toJson(courseSnapshot),
            toJson(priceSnapshot),
            toJson(taxSnapshot),
            receiverSnapshot == null ? null : toJson(receiverSnapshot),
            toJson(courseSpec.toSpecSnapshot()));
    }

    private String runPaymentSuccessSideEffects(OrderRow order, PaymentRow payment, LocalDateTime paidAt) {
        List<String> failures = new ArrayList<>();
        upsertDocumentLink(
            order,
            "PAYMENT",
            payment.paymentId(),
            payment.paymentNo(),
            payment.paymentResult(),
            payment.paidAmountCent(),
            "PAYMENT_SUCCESS",
            "pay_payment",
            "支付成功");
        for (OrderItemRow item : findRawOrderItems(order.id())) {
            try {
                EntitlementEventResult entitlement = learningEntitlementService.openForPaidOrder(new PaymentSuccessEntitlementCommand(
                    order.studentId(),
                    order.userId(),
                    order.id(),
                    order.orderNo(),
                    item.orderItemId(),
                    item.courseId(),
                    item.specId(),
                    item.courseSnapshotJson(),
                    paidAt));
                upsertDocumentLink(
                    order,
                    "ENTITLEMENT",
                    entitlement.entitlementId(),
                    entitlement.entitlementNo(),
                    entitlement.status(),
                    null,
                    "PAYMENT_SUCCESS",
                    "learning_entitlement",
                    "支付成功开通学习权益");
            } catch (RuntimeException exception) {
                failures.add("权益开通失败：" + exception.getMessage());
            }
        }
        if ("PENDING_SHIPMENT".equals(order.fulfillmentStatus())) {
            try {
                ShipmentRow shipment = createShipmentIfNeeded(order);
                upsertDocumentLink(
                    order,
                    "SHIPMENT",
                    shipment.shipmentId(),
                    shipment.shipmentNo(),
                    shipment.status(),
                    null,
                    "PAYMENT_SUCCESS",
                    "fulfillment_shipment",
                    "支付成功生成待发货任务");
            } catch (RuntimeException exception) {
                failures.add("发货任务失败：" + exception.getMessage());
            }
        }
        try {
            NotificationRow notification = createPaidNotificationIfNeeded(order);
            upsertDocumentLink(
                order,
                "NOTIFICATION",
                notification.notificationId(),
                notification.notificationNo(),
                notification.sendStatus(),
                null,
                "PAYMENT_SUCCESS",
                "notify_message",
                "支付成功站内通知");
        } catch (RuntimeException exception) {
            failures.add("通知生成失败：" + exception.getMessage());
        }
        try {
            StudentSnapshot student = findStudentSnapshot(order.studentId()).orElse(null);
            if (student != null) {
                var conversion = leadApplicationService.convertPaidLead(new LeadPaidConversionCommand(
                    order.studentId(),
                    student.mobile(),
                    student.wecomExternalUserId(),
                    order.id(),
                    paidAt));
                if (conversion.matched() && conversion.leadId() != null) {
                    jdbcTemplate.update("update trade_order set lead_id = coalesce(lead_id, ?) where id = ?", conversion.leadId(), order.id());
                    upsertDocumentLink(
                        order,
                        "LEAD",
                        conversion.leadId(),
                        conversion.leadNo(),
                        conversion.status(),
                        null,
                        "PAYMENT_SUCCESS",
                        "crm_lead",
                        "支付成功线索转化");
                }
            }
        } catch (RuntimeException exception) {
            failures.add("线索转化失败：" + exception.getMessage());
        }
        if (failures.isEmpty()) {
            return null;
        }
        String reason = String.join("; ", failures);
        auditLogService.writeSystemFailure(
            "PAYMENT",
            "PAYMENT_SUCCESS_SIDE_EFFECT_FAILED",
            "TRADE_ORDER",
            order.id(),
            order.orderNo(),
            order.id(),
            reason);
        return reason;
    }

    private PaymentRow insertSuccessPayment(
        OrderRow order,
        PaymentCallbackCommand command,
        CallbackEventRecord callbackEvent,
        String idempotencyKey,
        String sourceSystem,
        String rawSnapshot
    ) {
        Optional<PaymentRow> existing = findPaymentByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        long paymentId = idGenerator.nextId();
        String paymentNo = "PAY" + paymentId;
        LocalDateTime paidAt = command.paidAt() == null ? LocalDateTime.now() : command.paidAt();
        jdbcTemplate.update(
            """
            insert into pay_payment (
                id, payment_no, order_id, order_no, merchant_order_no, channel,
                payment_method, payment_result, paid_amount_cent, external_payment_no,
                paid_at, callback_event_no, idempotency_key, raw_callback_snapshot,
                created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, 'MINIPROGRAM', 'SUCCESS', ?, ?, ?, ?, ?, ?, 0, 0)
            """,
            paymentId,
            paymentNo,
            order.id(),
            order.orderNo(),
            order.merchantOrderNo(),
            "WECHAT_PAY_MOCK".equals(sourceSystem) ? "MOCK" : "WECHAT",
            command.paidAmountCent(),
            command.externalPaymentNo(),
            paidAt,
            callbackEvent.eventNo(),
            idempotencyKey,
            rawSnapshot);
        auditLogService.writeSystemSuccess(
            "PAYMENT",
            "PAYMENT_RECORD_CREATE",
            "PAY_PAYMENT",
            paymentId,
            paymentNo,
            order.id(),
            "{\"paid_amount_cent\":" + command.paidAmountCent() + "}");
        return findPaymentByIdempotencyKey(idempotencyKey).orElseThrow();
    }

    private ShipmentRow createShipmentIfNeeded(OrderRow order) {
        Optional<ShipmentRow> existing = findShipmentByOrderId(order.id());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (order.receiverSnapshotJson() == null || order.receiverSnapshotJson().isBlank()) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "含实物订单缺少收货地址快照");
        }
        long shipmentId = idGenerator.nextId();
        String shipmentNo = "SHP" + shipmentId;
        jdbcTemplate.update(
            """
            insert into fulfillment_shipment (
                id, shipment_no, order_id, order_no, student_id, status,
                receiver_snapshot, exception_flag, created_by, updated_by
            ) values (?, ?, ?, ?, ?, 'PENDING_SHIPMENT', ?, 0, 0, 0)
            """,
            shipmentId,
            shipmentNo,
            order.id(),
            order.orderNo(),
            order.studentId(),
            order.receiverSnapshotJson());
        for (OrderItemRow item : findRawOrderItems(order.id())) {
            insertShipmentSkuLine(shipmentId, shipmentNo, order.id(), item.orderItemId(), item.skuId(), "MAIN", item.quantity());
            insertShipmentSkuLine(shipmentId, shipmentNo, order.id(), item.orderItemId(), item.giftSkuId(), "GIFT", item.quantity());
        }
        auditLogService.writeSystemSuccess(
            "FULFILLMENT",
            "SHIPMENT_TASK_CREATE",
            "FULFILLMENT_SHIPMENT",
            shipmentId,
            shipmentNo,
            order.id(),
            "{\"status\":\"PENDING_SHIPMENT\"}");
        return findShipmentByOrderId(order.id()).orElseThrow();
    }

    private void insertShipmentSkuLine(
        long shipmentId,
        String shipmentNo,
        long orderId,
        long orderItemId,
        Long skuId,
        String lineType,
        int quantity
    ) {
        if (skuId == null) {
            return;
        }
        SkuRow sku = findSku(skuId).orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "SKU 不存在"));
        jdbcTemplate.update(
            """
            insert into fulfillment_shipment_item (
                id, shipment_id, shipment_no, order_id, order_item_id, sku_id, sku_no,
                sku_name, line_type, quantity, created_by, updated_by
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0)
            """,
            idGenerator.nextId(),
            shipmentId,
            shipmentNo,
            orderId,
            orderItemId,
            sku.skuId(),
            sku.skuNo(),
            sku.skuName(),
            lineType,
            quantity);
    }

    private NotificationRow createPaidNotificationIfNeeded(OrderRow order) {
        String idempotencyKey = "ORDER_PAID:" + order.id() + ":" + order.userId();
        Optional<NotificationRow> existing = findNotificationByIdempotency(idempotencyKey, order.userId());
        if (existing.isPresent()) {
            return existing.get();
        }
        long notificationId = idGenerator.nextId();
        String notificationNo = "NTF" + notificationId;
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(
            """
            insert into notify_message (
                id, notification_no, receiver_user_id, receiver_student_id, channel,
                scene_code, template_code, title, content, order_id,
                related_object_type, related_object_id, send_status, read_status,
                sent_at, retry_count, idempotency_key, created_by, updated_by
            ) values (?, ?, ?, ?, 'IN_APP', 'ORDER_PAID', 'ORDER_PAID_IN_APP',
                '支付成功', ?, ?, 'TRADE_ORDER', ?, 'SENT', 'UNREAD', ?, 0, ?, 0, 0)
            """,
            notificationId,
            notificationNo,
            order.userId(),
            order.studentId(),
            "订单 " + order.orderNo() + " 支付成功，学习权益已开通。",
            order.id(),
            order.id(),
            now,
            idempotencyKey);
        auditLogService.writeSystemSuccess(
            "NOTIFICATION",
            "ORDER_PAID_NOTIFICATION",
            "NOTIFY_MESSAGE",
            notificationId,
            notificationNo,
            order.id(),
            "{\"scene_code\":\"ORDER_PAID\"}");
        return findNotificationByIdempotency(idempotencyKey, order.userId()).orElseThrow();
    }

    private void upsertDocumentLink(
        OrderRow order,
        String documentType,
        long documentId,
        String documentNo,
        String documentStatus,
        Long amountCent,
        String relationType,
        String sourceTable,
        String remark
    ) {
        documentLinkRepository.upsert(new DocumentLinkCommand(
            idGenerator.nextId(),
            order.id(),
            order.orderNo(),
            documentType,
            documentId,
            documentNo,
            documentStatus,
            amountCent,
            relationType,
            LocalDateTime.now(),
            sourceTable,
            remark,
            0L));
    }

    private OrderRow closeExpiredIfNeeded(OrderRow order) {
        if ("PENDING".equals(order.paymentStatus()) && order.paymentExpireAt().isBefore(LocalDateTime.now())) {
            LocalDateTime closedAt = LocalDateTime.now();
            jdbcTemplate.update(
                """
                update trade_order
                set payment_status = 'CLOSED',
                    closed_at = ?,
                    close_reason = 'PAYMENT_TIMEOUT',
                    updated_by = 0,
                    version = version + 1
                where id = ? and payment_status = 'PENDING'
                """,
                closedAt,
                order.id());
            auditLogService.writeSystemSuccess(
                "TRADE",
                "ORDER_TIMEOUT_CLOSE",
                "TRADE_ORDER",
                order.id(),
                order.orderNo(),
                order.id(),
                "{\"payment_status\":\"CLOSED\",\"close_reason\":\"PAYMENT_TIMEOUT\"}");
            return requireOrder(order.id());
        }
        return order;
    }

    private StudentSession requireTradeReadyStudent(String authorizationHeader) {
        StudentSession student = appStudentApplicationService.requireStudent(authorizationHeader);
        if (student.mobile() == null || student.mobile().isBlank()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_BLOCKED", "完成手机号授权后可继续交易");
        }
        return student;
    }

    private OrderRow requireOrder(long orderId) {
        return findOrderById(orderId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "订单不存在"));
    }

    private OrderRow requireStudentOrder(long orderId, long studentId) {
        OrderRow order = requireOrder(orderId);
        if (order.studentId() != studentId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问该订单");
        }
        return order;
    }

    private void assertSameCreateRequest(OrderRow existing, OrderCreateCommand command) {
        List<OrderItemRow> items = findRawOrderItems(existing.id());
        if (items.size() != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "幂等订单明细异常");
        }
        OrderItemRow item = items.get(0);
        long courseId = requirePositive(command.courseId(), "课程不能为空");
        long specId = requirePositive(command.specId(), "规格不能为空");
        int quantity = command.quantity() == null ? 1 : command.quantity();
        Long addressId = command.addressId();
        Map<String, Object> receiver = fromJson(existing.receiverSnapshotJson());
        Long existingAddressId = receiver == null ? null : asLong(receiver.get("address_id"));
        if (item.courseId() != courseId
            || item.specId() != specId
            || item.quantity() != quantity
            || !Objects.equals(existingAddressId, addressId)
            || (command.confirmedPayableAmountCent() != null
                && command.confirmedPayableAmountCent().longValue() != existing.payableAmountCent())) {
            throw new ApiException(HttpStatus.CONFLICT, "STATE_CONFLICT", "幂等键已被不同订单参数使用");
        }
    }

    private Optional<CourseSpecRow> findSellableCourseSpec(long courseId, long specId) {
        return jdbcTemplate.query(
            """
            select c.id as course_id, c.course_no, c.course_title, c.course_type,
                   c.teacher_user_id, c.category_code, c.default_tax_rule_id, c.version as course_version,
                   s.id as spec_id, s.spec_no, s.spec_name, s.sale_price_cent,
                   s.origin_price_cent, s.stock_mode, s.contains_physical, s.sku_id,
                   s.gift_sku_id, s.tax_rule_id, s.amount_split_snapshot, s.version as spec_version
            from course c
            join course_spec s on s.course_id = c.id and s.deleted_flag = 0
            where c.id = ?
              and s.id = ?
              and c.status = 'ON_SHELF'
              and s.status = 'ENABLED'
              and c.deleted_flag = 0
              and (c.sale_start_at is null or c.sale_start_at <= ?)
              and (c.sale_end_at is null or c.sale_end_at >= ?)
            """,
            (rs, rowNum) -> new CourseSpecRow(
                rs.getLong("course_id"),
                rs.getString("course_no"),
                rs.getString("course_title"),
                rs.getString("course_type"),
                nullableLong(rs, "teacher_user_id"),
                rs.getString("category_code"),
                nullableLong(rs, "default_tax_rule_id"),
                rs.getLong("course_version"),
                rs.getLong("spec_id"),
                rs.getString("spec_no"),
                rs.getString("spec_name"),
                rs.getLong("sale_price_cent"),
                nullableLong(rs, "origin_price_cent"),
                rs.getString("stock_mode"),
                rs.getBoolean("contains_physical"),
                nullableLong(rs, "sku_id"),
                nullableLong(rs, "gift_sku_id"),
                nullableLong(rs, "tax_rule_id"),
                rs.getString("amount_split_snapshot"),
                rs.getLong("spec_version")),
            courseId,
            specId,
            LocalDateTime.now(),
            LocalDateTime.now())
            .stream()
            .findFirst();
    }

    private Optional<TaxRuleRow> findTaxRule(Long taxRuleId) {
        if (taxRuleId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            """
            select id, rule_no, rule_name, tax_category, tax_rate, invoice_item_name
            from tax_rule
            where id = ? and status = 'ACTIVE' and deleted_flag = 0
            """,
            (rs, rowNum) -> new TaxRuleRow(
                rs.getLong("id"),
                rs.getString("rule_no"),
                rs.getString("rule_name"),
                rs.getString("tax_category"),
                rs.getBigDecimal("tax_rate"),
                rs.getString("invoice_item_name")),
            taxRuleId)
            .stream()
            .findFirst();
    }

    private Optional<AddressRow> findStudentAddress(long studentId, long addressId) {
        return jdbcTemplate.query(
            """
            select id, student_id, receiver_name, receiver_mobile, province, city, district,
                   detail_address, postal_code
            from student_address
            where id = ? and student_id = ? and status = 'ACTIVE' and deleted_flag = 0
            """,
            (rs, rowNum) -> new AddressRow(
                rs.getLong("id"),
                rs.getLong("student_id"),
                rs.getString("receiver_name"),
                rs.getString("receiver_mobile"),
                rs.getString("province"),
                rs.getString("city"),
                rs.getString("district"),
                rs.getString("detail_address"),
                rs.getString("postal_code")),
            addressId,
            studentId)
            .stream()
            .findFirst();
    }

    private boolean hasStockWarning(CourseSpecRow spec, int quantity) {
        return hasSkuStockWarning(spec.skuId(), quantity) || hasSkuStockWarning(spec.giftSkuId(), quantity);
    }

    private boolean hasSkuStockWarning(Long skuId, int quantity) {
        if (skuId == null) {
            return false;
        }
        Integer available = jdbcTemplate.queryForObject(
            "select available_stock from inventory_sku where id = ? and deleted_flag = 0",
            Integer.class,
            skuId);
        return available != null && available < quantity;
    }

    private Optional<OrderRow> findOrderById(long orderId) {
        return queryOrder("o.id = ?", orderId);
    }

    private Optional<OrderRow> findOrderByMerchantOrderNo(String merchantOrderNo) {
        return queryOrder("o.merchant_order_no = ?", merchantOrderNo);
    }

    private Optional<OrderRow> findOrderByStudentAndIdempotency(long studentId, String idempotencyKey) {
        return queryOrder("o.student_id = ? and o.idempotency_key = ?", studentId, idempotencyKey);
    }

    private Optional<OrderRow> queryOrder(String condition, Object... args) {
        return jdbcTemplate.query(
            """
            select o.id, o.order_no, o.merchant_order_no, o.student_id, o.user_id, o.lead_id,
                   o.source_channel, o.client_request_no, o.idempotency_key, o.confirm_token,
                   o.total_amount_cent, o.discount_amount_cent, o.payable_amount_cent, o.paid_amount_cent,
                   o.course_snapshot, o.price_snapshot, o.tax_snapshot, o.receiver_snapshot,
                   o.payment_status, o.fulfillment_status, o.refund_status, o.invoice_status,
                   o.payment_expire_at, o.paid_at, o.closed_at, o.close_reason, o.created_at
            from trade_order o
            where
            """ + " " + condition,
            this::mapOrder,
            args)
            .stream()
            .findFirst();
    }

    private OrderRow mapOrder(ResultSet rs, int rowNum) throws SQLException {
        return new OrderRow(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getString("merchant_order_no"),
            rs.getLong("student_id"),
            rs.getLong("user_id"),
            nullableLong(rs, "lead_id"),
            rs.getString("source_channel"),
            rs.getString("client_request_no"),
            rs.getString("idempotency_key"),
            rs.getString("confirm_token"),
            rs.getLong("total_amount_cent"),
            rs.getLong("discount_amount_cent"),
            rs.getLong("payable_amount_cent"),
            nullableLong(rs, "paid_amount_cent"),
            rs.getString("course_snapshot"),
            rs.getString("price_snapshot"),
            rs.getString("tax_snapshot"),
            rs.getString("receiver_snapshot"),
            rs.getString("payment_status"),
            rs.getString("fulfillment_status"),
            rs.getString("refund_status"),
            rs.getString("invoice_status"),
            rs.getObject("payment_expire_at", LocalDateTime.class),
            rs.getObject("paid_at", LocalDateTime.class),
            rs.getObject("closed_at", LocalDateTime.class),
            rs.getString("close_reason"),
            rs.getObject("created_at", LocalDateTime.class));
    }

    private List<OrderItemRow> findRawOrderItems(long orderId) {
        return jdbcTemplate.query(
            """
            select id, order_id, order_no, line_no, course_id, spec_id, item_name, quantity,
                   unit_price_cent, total_amount_cent, discount_amount_cent, payable_amount_cent,
                   paid_amount_cent, contains_physical, sku_id, gift_sku_id,
                   course_snapshot, spec_snapshot, tax_snapshot
            from trade_order_item
            where order_id = ?
            order by line_no
            """,
            (rs, rowNum) -> new OrderItemRow(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getInt("line_no"),
                rs.getLong("course_id"),
                rs.getLong("spec_id"),
                rs.getString("item_name"),
                rs.getInt("quantity"),
                rs.getLong("unit_price_cent"),
                rs.getLong("total_amount_cent"),
                rs.getLong("discount_amount_cent"),
                rs.getLong("payable_amount_cent"),
                nullableLong(rs, "paid_amount_cent"),
                rs.getBoolean("contains_physical"),
                nullableLong(rs, "sku_id"),
                nullableLong(rs, "gift_sku_id"),
                rs.getString("course_snapshot"),
                rs.getString("spec_snapshot"),
                rs.getString("tax_snapshot")),
            orderId);
    }

    private List<OrderItemResponse> findOrderItems(long orderId) {
        return findRawOrderItems(orderId).stream()
            .map(item -> new OrderItemResponse(
                item.orderItemId(),
                item.lineNo(),
                item.courseId(),
                item.specId(),
                item.itemName(),
                item.quantity(),
                item.unitPriceCent(),
                item.totalAmountCent(),
                item.discountAmountCent(),
                item.payableAmountCent(),
                item.paidAmountCent(),
                item.containsPhysical(),
                item.skuId(),
                item.giftSkuId(),
                fromJson(item.courseSnapshotJson()),
                fromJson(item.specSnapshotJson()),
                fromJson(item.taxSnapshotJson())))
            .toList();
    }

    private List<PaymentRecordResponse> findPayments(long orderId) {
        return jdbcTemplate.query(
            """
            select id, payment_no, order_id, order_no, merchant_order_no, channel,
                   payment_method, payment_result, paid_amount_cent, external_payment_no,
                   paid_at, callback_event_no, idempotency_key, failure_reason
            from pay_payment
            where order_id = ?
            order by paid_at, id
            """,
            (rs, rowNum) -> new PaymentRecordResponse(
                rs.getLong("id"),
                rs.getString("payment_no"),
                rs.getString("channel"),
                rs.getString("payment_method"),
                rs.getString("payment_result"),
                rs.getLong("paid_amount_cent"),
                rs.getString("external_payment_no"),
                rs.getObject("paid_at", LocalDateTime.class),
                rs.getString("callback_event_no"),
                rs.getString("idempotency_key"),
                rs.getString("failure_reason")),
            orderId);
    }

    private Optional<PaymentRow> findPaymentByIdempotencyKey(String idempotencyKey) {
        return jdbcTemplate.query(
            """
            select id, payment_no, order_id, order_no, merchant_order_no, payment_result,
                   paid_amount_cent, external_payment_no, paid_at
            from pay_payment
            where idempotency_key = ?
            """,
            (rs, rowNum) -> new PaymentRow(
                rs.getLong("id"),
                rs.getString("payment_no"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getString("merchant_order_no"),
                rs.getString("payment_result"),
                rs.getLong("paid_amount_cent"),
                rs.getString("external_payment_no"),
                rs.getObject("paid_at", LocalDateTime.class)),
            idempotencyKey)
            .stream()
            .findFirst();
    }

    private Optional<ShipmentRow> findShipmentByOrderId(long orderId) {
        return jdbcTemplate.query(
            """
            select id, shipment_no, order_id, order_no, student_id, status
            from fulfillment_shipment
            where order_id = ?
            order by id
            limit 1
            """,
            (rs, rowNum) -> new ShipmentRow(
                rs.getLong("id"),
                rs.getString("shipment_no"),
                rs.getLong("order_id"),
                rs.getString("order_no"),
                rs.getLong("student_id"),
                rs.getString("status")),
            orderId)
            .stream()
            .findFirst();
    }

    private List<ShipmentResponse> findShipments(long orderId) {
        return jdbcTemplate.query(
            """
            select id, shipment_no, order_id, order_no, student_id, status, receiver_snapshot,
                   exception_flag, exception_reason, created_at
            from fulfillment_shipment
            where order_id = ?
            order by id
            """,
            (rs, rowNum) -> new ShipmentResponse(
                rs.getLong("id"),
                rs.getString("shipment_no"),
                rs.getString("status"),
                fromJson(rs.getString("receiver_snapshot")),
                rs.getBoolean("exception_flag"),
                rs.getString("exception_reason"),
                rs.getObject("created_at", LocalDateTime.class)),
            orderId);
    }

    private List<EntitlementResponse> findEntitlements(long orderId) {
        return jdbcTemplate.query(
            """
            select id, entitlement_no, order_id, course_id, spec_id, status, opened_at, course_snapshot
            from learning_entitlement
            where order_id = ?
            order by id
            """,
            (rs, rowNum) -> new EntitlementResponse(
                rs.getLong("id"),
                rs.getString("entitlement_no"),
                rs.getLong("course_id"),
                rs.getLong("spec_id"),
                rs.getString("status"),
                rs.getObject("opened_at", LocalDateTime.class),
                fromJson(rs.getString("course_snapshot"))),
            orderId);
    }

    private List<NotificationResponse> findNotifications(long orderId) {
        return jdbcTemplate.query(
            """
            select id, notification_no, channel, scene_code, title, content, send_status, read_status, sent_at
            from notify_message
            where order_id = ?
            order by id
            """,
            (rs, rowNum) -> new NotificationResponse(
                rs.getLong("id"),
                rs.getString("notification_no"),
                rs.getString("channel"),
                rs.getString("scene_code"),
                rs.getString("title"),
                rs.getString("content"),
                rs.getString("send_status"),
                rs.getString("read_status"),
                rs.getObject("sent_at", LocalDateTime.class)),
            orderId);
    }

    private List<AuditLogSummary> findAuditLogs(long orderId) {
        return jdbcTemplate.query(
            """
            select id, operation_module, operation_type, target_type, target_no, result, failure_reason, occurred_at
            from audit_operation_log
            where order_id = ?
            order by occurred_at, id
            """,
            (rs, rowNum) -> new AuditLogSummary(
                rs.getLong("id"),
                rs.getString("operation_module"),
                rs.getString("operation_type"),
                rs.getString("target_type"),
                rs.getString("target_no"),
                rs.getString("result"),
                rs.getString("failure_reason"),
                rs.getObject("occurred_at", LocalDateTime.class)),
            orderId);
    }

    private Optional<SkuRow> findSku(long skuId) {
        return jdbcTemplate.query(
            "select id, sku_no, sku_name from inventory_sku where id = ? and deleted_flag = 0",
            (rs, rowNum) -> new SkuRow(rs.getLong("id"), rs.getString("sku_no"), rs.getString("sku_name")),
            skuId)
            .stream()
            .findFirst();
    }

    private Optional<NotificationRow> findNotificationByIdempotency(String idempotencyKey, long receiverUserId) {
        return jdbcTemplate.query(
            """
            select id, notification_no, send_status
            from notify_message
            where idempotency_key = ? and channel = 'IN_APP' and receiver_user_id = ?
            """,
            (rs, rowNum) -> new NotificationRow(
                rs.getLong("id"),
                rs.getString("notification_no"),
                rs.getString("send_status")),
            idempotencyKey,
            receiverUserId)
            .stream()
            .findFirst();
    }

    private Optional<StudentSnapshot> findStudentSnapshot(long studentId) {
        return jdbcTemplate.query(
            """
            select mobile, wecom_external_user_id
            from edu_student
            where id = ?
            """,
            (rs, rowNum) -> new StudentSnapshot(
                rs.getString("mobile"),
                rs.getString("wecom_external_user_id")),
            studentId)
            .stream()
            .findFirst();
    }

    private PaymentCallbackResponse responseFromCallbackRecord(CallbackEventRecord record, OrderRow order, String idempotencyKey) {
        Optional<PaymentRow> payment = findPaymentByIdempotencyKey(idempotencyKey);
        return new PaymentCallbackResponse(
            record.processingStatus(),
            order == null ? null : order.id(),
            payment.map(PaymentRow::paymentId).orElse(null),
            payment.map(PaymentRow::paymentNo).orElse(null),
            order == null ? null : order.paymentStatus(),
            idempotencyKey,
            record.failureReason());
    }

    private OrderCreateResponse toCreateResponse(OrderRow order) {
        return new OrderCreateResponse(
            order.id(),
            order.orderNo(),
            order.merchantOrderNo(),
            order.paymentStatus(),
            order.fulfillmentStatus(),
            order.refundStatus(),
            order.invoiceStatus(),
            order.payableAmountCent(),
            order.paymentExpireAt(),
            LocalDateTime.now());
    }

    private DocumentLinkResponse toDocumentLinkResponse(OrderDocumentLinkRecord record) {
        return new DocumentLinkResponse(
            record.id(),
            record.documentType(),
            record.documentId(),
            record.documentNo(),
            record.documentStatus(),
            record.amountCent(),
            record.relationType(),
            record.occurredAt(),
            record.sourceTable(),
            record.remark());
    }

    private void appendEnumFilter(StringBuilder sql, List<Object> args, String columnName, String value, List<String> allowed) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toUpperCase();
        if (!allowed.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "状态枚举非法：" + value);
        }
        sql.append(" and ").append(columnName).append(" = ?");
        args.add(normalized);
    }

    private String confirmToken(CourseSpecRow spec, TaxRuleRow taxRule, AddressRow address, int quantity, long payableAmountCent) {
        String source = spec.courseId() + "|"
            + spec.specId() + "|"
            + spec.salePriceCent() + "|"
            + spec.originPriceCent() + "|"
            + spec.courseVersion() + "|"
            + spec.specVersion() + "|"
            + spec.containsPhysical() + "|"
            + (address == null ? "" : address.id()) + "|"
            + quantity + "|"
            + payableAmountCent + "|"
            + taxRule.id() + "|"
            + taxRule.taxRate();
        return sha256Hex(source).substring(0, 32);
    }

    private String sha256Hex(String source) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }

    private void validateCallback(PaymentCallbackCommand command) {
        if (command == null
            || command.eventNo() == null || command.eventNo().isBlank()
            || command.merchantOrderNo() == null || command.merchantOrderNo().isBlank()
            || command.externalPaymentNo() == null || command.externalPaymentNo().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "支付回调关键字段不能为空");
        }
    }

    private String paymentIdempotencyKey(String merchantOrderNo, String externalPaymentNo) {
        return merchantOrderNo + ":" + externalPaymentNo;
    }

    private long requirePositive(Long value, String message) {
        if (value == null || value <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        return value;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", message);
        }
        if (value.length() > 128) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "幂等键过长");
        }
        return value.trim();
    }

    private String normalizeSourceChannel(String value) {
        return value == null || value.isBlank() ? "MINIPROGRAM" : value.trim().toUpperCase();
    }

    private String normalizePaymentChannel(String value) {
        return value == null || value.isBlank() ? "MOCK" : value.trim().toUpperCase();
    }

    private String defaultString(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "JSON 快照生成失败");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isTextual()) {
                return fromJson(node.asText());
            }
            return objectMapper.convertValue(node, MAP_TYPE);
        } catch (JsonProcessingException exception) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", json);
            return fallback;
        }
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private String jsonSafe(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record OrderConfirmCommand(Long courseId, Long specId, Integer quantity, Long addressId, String sourceChannel, String sourceCode) {
    }

    public record OrderCreateCommand(
        Long courseId,
        Long specId,
        Integer quantity,
        Long addressId,
        String clientRequestNo,
        String sourceChannel,
        String sourceCode,
        Long confirmedPayableAmountCent,
        String confirmToken
    ) {
        private OrderConfirmCommand toConfirmCommand() {
            return new OrderConfirmCommand(courseId, specId, quantity, addressId, sourceChannel, sourceCode);
        }
    }

    public record PayCommand(String paymentChannel) {
    }

    public record CancelOrderCommand(String closeReason) {
    }

    public record MockPayCommand(
        String eventNo,
        String externalPaymentNo,
        Long paidAmountCent,
        String paymentResult,
        LocalDateTime paidAt,
        Map<String, Object> rawSnapshot
    ) {
    }

    public record PaymentCallbackCommand(
        String eventNo,
        String merchantOrderNo,
        String externalPaymentNo,
        Long paidAmountCent,
        String paymentResult,
        LocalDateTime paidAt,
        Map<String, Object> rawSnapshot
    ) {
    }

    public record OrderConfirmResponse(
        Map<String, Object> courseSnapshot,
        Map<String, Object> priceSnapshot,
        Map<String, Object> taxSnapshot,
        Map<String, Object> receiverSnapshot,
        long totalAmountCent,
        long discountAmountCent,
        long payableAmountCent,
        boolean containsPhysical,
        boolean stockWarning,
        String confirmToken,
        LocalDateTime serverTime
    ) {
    }

    public record OrderCreateResponse(
        long orderId,
        String orderNo,
        String merchantOrderNo,
        String paymentStatus,
        String fulfillmentStatus,
        String refundStatus,
        String invoiceStatus,
        long payableAmountCent,
        LocalDateTime paymentExpireAt,
        LocalDateTime serverTime
    ) {
    }

    public record PaymentPrepareResponse(
        long orderId,
        String orderNo,
        String merchantOrderNo,
        Map<String, Object> paymentParams,
        LocalDateTime paymentExpireAt,
        LocalDateTime serverTime
    ) {
    }

    public record OrderCloseResponse(long orderId, String orderNo, String paymentStatus, LocalDateTime closedAt, String closeReason) {
    }

    public record PaymentCallbackResponse(
        String processingStatus,
        Long orderId,
        Long paymentId,
        String paymentNo,
        String paymentStatus,
        String idempotencyKey,
        String failureReason
    ) {
    }

    public record OrderPage(List<OrderListItem> records, int pageNo, int pageSize, int total) {
    }

    public record OrderListItem(
        long orderId,
        String orderNo,
        String merchantOrderNo,
        long studentId,
        long userId,
        Long leadId,
        Map<String, Object> courseSnapshot,
        Long paidAmountCent,
        long payableAmountCent,
        String paymentStatus,
        String fulfillmentStatus,
        String refundStatus,
        String invoiceStatus,
        LocalDateTime paymentExpireAt,
        LocalDateTime serverTime,
        LocalDateTime createdAt
    ) {
    }

    public record OrderDetailResponse(
        long orderId,
        String orderNo,
        String merchantOrderNo,
        long studentId,
        long userId,
        Long leadId,
        List<OrderItemResponse> items,
        Map<String, Object> courseSnapshot,
        Map<String, Object> priceSnapshot,
        Map<String, Object> taxSnapshot,
        Map<String, Object> receiverSnapshot,
        long totalAmountCent,
        long discountAmountCent,
        long payableAmountCent,
        Long paidAmountCent,
        String paymentStatus,
        String fulfillmentStatus,
        String refundStatus,
        String invoiceStatus,
        LocalDateTime paymentExpireAt,
        LocalDateTime paidAt,
        LocalDateTime closedAt,
        String closeReason,
        List<PaymentRecordResponse> paymentRecords,
        List<EntitlementResponse> entitlements,
        List<ShipmentResponse> shipments,
        List<NotificationResponse> notifications,
        List<DocumentLinkResponse> documentLinks,
        List<AuditLogSummary> auditLogs
    ) {
    }

    public record OrderItemResponse(
        long orderItemId,
        int lineNo,
        long courseId,
        long specId,
        String itemName,
        int quantity,
        long unitPriceCent,
        long totalAmountCent,
        long discountAmountCent,
        long payableAmountCent,
        Long paidAmountCent,
        boolean containsPhysical,
        Long skuId,
        Long giftSkuId,
        Map<String, Object> courseSnapshot,
        Map<String, Object> specSnapshot,
        Map<String, Object> taxSnapshot
    ) {
    }

    public record PaymentRecordResponse(
        long paymentId,
        String paymentNo,
        String channel,
        String paymentMethod,
        String paymentResult,
        long paidAmountCent,
        String externalPaymentNo,
        LocalDateTime paidAt,
        String callbackEventNo,
        String idempotencyKey,
        String failureReason
    ) {
    }

    public record EntitlementResponse(
        long entitlementId,
        String entitlementNo,
        long courseId,
        long specId,
        String status,
        LocalDateTime openedAt,
        Map<String, Object> courseSnapshot
    ) {
    }

    public record ShipmentResponse(
        long shipmentId,
        String shipmentNo,
        String status,
        Map<String, Object> receiverSnapshot,
        boolean exceptionFlag,
        String exceptionReason,
        LocalDateTime createdAt
    ) {
    }

    public record NotificationResponse(
        long notificationId,
        String notificationNo,
        String channel,
        String sceneCode,
        String title,
        String content,
        String sendStatus,
        String readStatus,
        LocalDateTime sentAt
    ) {
    }

    public record DocumentLinkResponse(
        long documentLinkId,
        String documentType,
        long documentId,
        String documentNo,
        String documentStatus,
        Long amountCent,
        String relationType,
        LocalDateTime occurredAt,
        String sourceTable,
        String remark
    ) {
    }

    public record AuditLogSummary(
        long auditId,
        String operationModule,
        String operationType,
        String targetType,
        String targetNo,
        String result,
        String failureReason,
        LocalDateTime occurredAt
    ) {
    }

    private record OrderConfirmation(
        CourseSpecRow course,
        TaxRuleRow taxRule,
        AddressRow address,
        int quantity,
        long totalAmountCent,
        long discountAmountCent,
        long payableAmountCent,
        long taxAmountCent,
        boolean containsPhysical,
        boolean stockWarning,
        String confirmToken,
        Map<String, Object> courseSnapshot,
        Map<String, Object> priceSnapshot,
        Map<String, Object> taxSnapshot,
        Map<String, Object> receiverSnapshot,
        String courseSnapshotJson,
        String priceSnapshotJson,
        String taxSnapshotJson,
        String receiverSnapshotJson,
        String specSnapshotJson
    ) {
        OrderConfirmResponse toResponse() {
            return new OrderConfirmResponse(
                courseSnapshot,
                priceSnapshot,
                taxSnapshot,
                receiverSnapshot,
                totalAmountCent,
                discountAmountCent,
                payableAmountCent,
                containsPhysical,
                stockWarning,
                confirmToken,
                LocalDateTime.now());
        }

        CourseSpecRow spec() {
            return course;
        }
    }

    private record CourseSpecRow(
        long courseId,
        String courseNo,
        String courseTitle,
        String courseType,
        Long teacherUserId,
        String categoryCode,
        Long defaultTaxRuleId,
        long courseVersion,
        long specId,
        String specNo,
        String specName,
        long salePriceCent,
        Long originPriceCent,
        String stockMode,
        boolean containsPhysical,
        Long skuId,
        Long giftSkuId,
        Long taxRuleId,
        String amountSplitSnapshot,
        long specVersion
    ) {
        Map<String, Object> toSpecSnapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("spec_id", specId);
            snapshot.put("spec_no", specNo);
            snapshot.put("spec_name", specName);
            snapshot.put("sale_price_cent", salePriceCent);
            snapshot.put("origin_price_cent", originPriceCent);
            snapshot.put("stock_mode", stockMode);
            snapshot.put("contains_physical", containsPhysical);
            snapshot.put("sku_id", skuId);
            snapshot.put("gift_sku_id", giftSkuId);
            snapshot.put("tax_rule_id", taxRuleId);
            return snapshot;
        }
    }

    private record TaxRuleRow(long id, String ruleNo, String ruleName, String taxCategory, BigDecimal taxRate, String invoiceItemName) {
    }

    private record AddressRow(
        long id,
        long studentId,
        String receiverName,
        String receiverMobile,
        String province,
        String city,
        String district,
        String detailAddress,
        String postalCode
    ) {
        Map<String, Object> toSnapshot() {
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("address_id", id);
            snapshot.put("receiver_name", receiverName);
            snapshot.put("receiver_mobile", receiverMobile);
            snapshot.put("province", province);
            snapshot.put("city", city);
            snapshot.put("district", district);
            snapshot.put("detail_address", detailAddress);
            snapshot.put("postal_code", postalCode);
            return snapshot;
        }
    }

    private record OrderRow(
        long id,
        String orderNo,
        String merchantOrderNo,
        long studentId,
        long userId,
        Long leadId,
        String sourceChannel,
        String clientRequestNo,
        String idempotencyKey,
        String confirmToken,
        long totalAmountCent,
        long discountAmountCent,
        long payableAmountCent,
        Long paidAmountCent,
        String courseSnapshotJson,
        String priceSnapshotJson,
        String taxSnapshotJson,
        String receiverSnapshotJson,
        String paymentStatus,
        String fulfillmentStatus,
        String refundStatus,
        String invoiceStatus,
        LocalDateTime paymentExpireAt,
        LocalDateTime paidAt,
        LocalDateTime closedAt,
        String closeReason,
        LocalDateTime createdAt
    ) {
    }

    private record OrderItemRow(
        long orderItemId,
        long orderId,
        String orderNo,
        int lineNo,
        long courseId,
        long specId,
        String itemName,
        int quantity,
        long unitPriceCent,
        long totalAmountCent,
        long discountAmountCent,
        long payableAmountCent,
        Long paidAmountCent,
        boolean containsPhysical,
        Long skuId,
        Long giftSkuId,
        String courseSnapshotJson,
        String specSnapshotJson,
        String taxSnapshotJson
    ) {
    }

    private record PaymentRow(
        long paymentId,
        String paymentNo,
        long orderId,
        String orderNo,
        String merchantOrderNo,
        String paymentResult,
        long paidAmountCent,
        String externalPaymentNo,
        LocalDateTime paidAt
    ) {
    }

    private record ShipmentRow(long shipmentId, String shipmentNo, long orderId, String orderNo, long studentId, String status) {
    }

    private record SkuRow(long skuId, String skuNo, String skuName) {
    }

    private record NotificationRow(long notificationId, String notificationNo, String sendStatus) {
    }

    private record StudentSnapshot(String mobile, String wecomExternalUserId) {
    }
}

package com.wecombft.interfaces.app;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.wecombft.application.fulfillment.FulfillmentApplicationService;
import com.wecombft.application.student.AppStudentApplicationService;
import com.wecombft.application.student.StudentSession;
import com.wecombft.application.trade.OrderPaymentApplicationService;
import com.wecombft.interfaces.dto.fulfillment.ShipmentDetailResponse;
import com.wecombft.shared.trace.TraceIds;
import com.wecombft.shared.web.ApiResponse;

/**
 * 学员侧物流详情：仅返回当前学员名下订单的发货单 + 物流轨迹，
 * 不暴露管理端的库存流水/单据链等内部字段。
 */
@RestController
public class AppLogisticsController {

    private final FulfillmentApplicationService fulfillmentApplicationService;
    private final OrderPaymentApplicationService orderPaymentApplicationService;
    private final AppStudentApplicationService appStudentApplicationService;

    public AppLogisticsController(
        FulfillmentApplicationService fulfillmentApplicationService,
        OrderPaymentApplicationService orderPaymentApplicationService,
        AppStudentApplicationService appStudentApplicationService
    ) {
        this.fulfillmentApplicationService = fulfillmentApplicationService;
        this.orderPaymentApplicationService = orderPaymentApplicationService;
        this.appStudentApplicationService = appStudentApplicationService;
    }

    @GetMapping("/api/app/orders/{order_id}/logistics")
    public ResponseEntity<ApiResponse<List<ShipmentDetailResponse>>> orderLogistics(
        @RequestHeader("Authorization") String authorization,
        @PathVariable("order_id") long orderId
    ) {
        StudentSession student = appStudentApplicationService.requireStudent(authorization);
        // 通过 appOrderDetail 复用学员归属校验：非本人订单会抛 403
        orderPaymentApplicationService.appOrderDetail(authorization, orderId);
        return ResponseEntity.ok(ApiResponse.ok(
            fulfillmentApplicationService.appShipmentsByOrder(orderId),
            TraceIds.currentOrCreate()));
    }
}

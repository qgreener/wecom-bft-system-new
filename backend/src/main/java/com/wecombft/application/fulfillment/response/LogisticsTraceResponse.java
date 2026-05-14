package com.wecombft.application.fulfillment.response;

import java.time.LocalDateTime;

public record LogisticsTraceResponse(long traceId, long shipmentId, long orderId, String trackingNo, LocalDateTime logisticsNodeTime, String nodeStatus, String nodeDesc) {
}

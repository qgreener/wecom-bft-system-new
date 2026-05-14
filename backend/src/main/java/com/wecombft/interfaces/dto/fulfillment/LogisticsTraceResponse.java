package com.wecombft.interfaces.dto.fulfillment;

import java.time.LocalDateTime;

public record LogisticsTraceResponse(long traceId, long shipmentId, long orderId, String trackingNo, LocalDateTime logisticsNodeTime, String nodeStatus, String nodeDesc) {
}

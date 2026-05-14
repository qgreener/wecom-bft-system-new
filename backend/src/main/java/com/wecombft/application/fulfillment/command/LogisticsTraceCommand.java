package com.wecombft.application.fulfillment.command;

import java.time.LocalDateTime;
import java.util.Map;

public record LogisticsTraceCommand(
    String eventNo,
    String shipmentNo,
    String trackingNo,
    LocalDateTime logisticsNodeTime,
    String nodeStatus,
    String nodeDesc,
    Boolean signedFlag,
    Map<String, Object> rawSnapshot
) {
}

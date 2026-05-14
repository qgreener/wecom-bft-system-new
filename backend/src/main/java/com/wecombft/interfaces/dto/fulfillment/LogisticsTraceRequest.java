package com.wecombft.interfaces.dto.fulfillment;

import com.wecombft.application.command.fulfillment.LogisticsTraceCommand;
import java.time.LocalDateTime;
import java.util.Map;

public record LogisticsTraceRequest(
    String eventNo,
    String shipmentNo,
    String trackingNo,
    LocalDateTime logisticsNodeTime,
    String nodeStatus,
    String nodeDesc,
    Boolean signedFlag,
    Map<String, Object> rawSnapshot
) {
    public LogisticsTraceCommand toCommand() {
        return new LogisticsTraceCommand(
            eventNo,
            shipmentNo,
            trackingNo,
            logisticsNodeTime,
            nodeStatus,
            nodeDesc,
            signedFlag,
            rawSnapshot);
    }
}

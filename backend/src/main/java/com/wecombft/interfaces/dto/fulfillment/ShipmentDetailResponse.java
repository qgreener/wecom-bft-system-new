package com.wecombft.interfaces.dto.fulfillment;

import java.util.List;

public record ShipmentDetailResponse(
    ShipmentActionResponse shipment,
    List<ShipmentItemResponse> items,
    List<LogisticsTraceResponse> traces,
    List<StockFlowResponse> stockFlows,
    List<DocumentLinkResponse> documentLinks
) {
}

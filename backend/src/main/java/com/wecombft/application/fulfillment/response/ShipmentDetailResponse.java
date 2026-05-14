package com.wecombft.application.fulfillment.response;

import java.util.List;

public record ShipmentDetailResponse(
    ShipmentActionResponse shipment,
    List<ShipmentItemResponse> items,
    List<LogisticsTraceResponse> traces,
    List<StockFlowResponse> stockFlows,
    List<DocumentLinkResponse> documentLinks
) {
}

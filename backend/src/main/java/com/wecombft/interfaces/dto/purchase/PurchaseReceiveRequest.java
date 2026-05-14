package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.PurchaseReceiveCommand;
import java.util.List;

public record PurchaseReceiveRequest(
    String inboundBatchNo,
    List<ReceivedItemRequest> receivedItems,
    String remark
) {
    public PurchaseReceiveCommand toCommand() {
        return new PurchaseReceiveCommand(
            inboundBatchNo,
            receivedItems == null ? null : receivedItems.stream().map(ReceivedItemRequest::toCommand).toList(),
            remark);
    }
}

package com.wecombft.application.command.purchase;

import java.util.List;

public record PurchaseReceiveCommand(String inboundBatchNo, List<ReceivedItemCommand> receivedItems, String remark) {
}

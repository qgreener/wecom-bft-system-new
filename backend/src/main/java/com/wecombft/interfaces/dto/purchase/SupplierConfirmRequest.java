package com.wecombft.interfaces.dto.purchase;

import com.wecombft.application.command.purchase.SupplierConfirmCommand;
import java.time.LocalDate;

public record SupplierConfirmRequest(
    LocalDate expectedArrivalDate,
    String remark
) {
    public SupplierConfirmCommand toCommand() {
        return new SupplierConfirmCommand(
            expectedArrivalDate,
            remark);
    }
}

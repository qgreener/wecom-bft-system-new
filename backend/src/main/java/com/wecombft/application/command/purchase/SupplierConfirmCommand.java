package com.wecombft.application.command.purchase;

import java.time.LocalDate;

public record SupplierConfirmCommand(LocalDate expectedArrivalDate, String remark) {
}

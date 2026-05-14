package com.wecombft.interfaces.dto.finance;

public record InvoiceTitleResponse(long titleId, String titleType, String titleName, String taxNo, String email, boolean isDefault, String status) {
}

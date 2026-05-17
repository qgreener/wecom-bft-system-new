package com.wecombft.interfaces.dto.trade;

import java.util.List;

public record PaymentAdminPage(List<PaymentRecordResponse> records, int pageNo, int pageSize, int total) {
}

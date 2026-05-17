package com.wecombft.interfaces.dto.finance;

import java.util.List;

public record TaxRulePage(List<TaxRuleResponse> records, int pageNo, int pageSize, int total) {
}

package com.wecombft.interfaces.dto.course;

public record CourseSpecItem(
    long specId,
    String specNo,
    long courseId,
    String specName,
    long salePriceCent,
    Long originPriceCent,
    String stockMode,
    boolean containsPhysical,
    Long skuId,
    Long giftSkuId,
    Long taxRuleId,
    String amountSplitSnapshot,
    String status,
    int sortNo
) {
}

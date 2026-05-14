package com.wecombft.application.command.course;

public record CourseSpecCommand(
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

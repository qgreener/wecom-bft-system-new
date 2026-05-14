package com.wecombft.interfaces.dto.course;

import com.wecombft.application.command.course.CourseSpecCommand;

public record CourseSpecRequest(
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
    public CourseSpecCommand toCommand() {
        return new CourseSpecCommand(
            specName,
            salePriceCent,
            originPriceCent,
            stockMode,
            containsPhysical,
            skuId,
            giftSkuId,
            taxRuleId,
            amountSplitSnapshot,
            status,
            sortNo);
    }
}

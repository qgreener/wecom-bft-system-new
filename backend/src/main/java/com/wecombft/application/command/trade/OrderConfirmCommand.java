package com.wecombft.application.command.trade;

public record OrderConfirmCommand(Long courseId, Long specId, Integer quantity, Long addressId, String sourceChannel, String sourceCode) {
}

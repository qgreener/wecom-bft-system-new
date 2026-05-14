package com.wecombft.application.fulfillment.command;

import java.time.LocalDateTime;

public record SignCommand(LocalDateTime signedAt, String remark) {
}

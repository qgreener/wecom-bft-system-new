package com.wecombft.application.command.fulfillment;

import java.time.LocalDateTime;

public record SignCommand(LocalDateTime signedAt, String remark) {
}

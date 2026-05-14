package com.wecombft.interfaces.dto.fulfillment;

import com.wecombft.application.command.fulfillment.SignCommand;
import java.time.LocalDateTime;

public record SignRequest(
    LocalDateTime signedAt,
    String remark
) {
    public SignCommand toCommand() {
        return new SignCommand(
            signedAt,
            remark);
    }
}

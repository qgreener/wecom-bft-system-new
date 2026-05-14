package com.wecombft.interfaces.dto.finance;

public record AccountingMaterialDownloadResponse(long materialId, String materialNo, String fileNo, boolean downloadAllowed, String downloadReason) {
}

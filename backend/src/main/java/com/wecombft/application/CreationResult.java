package com.wecombft.application;

public record CreationResult<T>(T response, boolean created) {
}

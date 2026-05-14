package com.wecombft.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.wecombft.shared.web.ApiException;

class ErrorCodeTest {

    @Test
    void should_expose_stable_code_default_message_and_http_status() {
        assertThat(ErrorCode.INVALID_ARGUMENT.code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(ErrorCode.INVALID_ARGUMENT.defaultMessage()).isEqualTo("Invalid request argument.");
        assertThat(ErrorCode.INVALID_ARGUMENT.httpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void api_exception_should_accept_error_code() {
        ApiException exception = new ApiException(ErrorCode.UNAUTHORIZED);

        assertThat(exception.code()).isEqualTo("UNAUTHORIZED");
        assertThat(exception.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo("Authentication is required.");
    }
}

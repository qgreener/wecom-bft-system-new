package com.wecombft.infrastructure.file;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class FileDigestCalculatorTest {

    @Test
    void should_calculate_sha256_hex_digest_for_uploaded_file_content() throws Exception {
        byte[] bytes = "abc".getBytes(StandardCharsets.UTF_8);

        assertThat(FileDigestCalculator.sha256Hex(bytes))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(FileDigestCalculator.sha256Hex(new ByteArrayInputStream(bytes)))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}

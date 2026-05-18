package com.wecombft.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;

public interface FileStorageAdapter {

    void store(InputStream content, String storageKey, String contentType) throws IOException;

    InputStream read(String storageKey) throws IOException;

    boolean exists(String storageKey);
}

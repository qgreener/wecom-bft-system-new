package com.wecombft.infrastructure.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "file.storage-type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageAdapter implements FileStorageAdapter {

    private final Path basePath;

    public LocalFileStorageAdapter(@Value("${file.upload-base-path:./storage/files}") String uploadBasePath) {
        this.basePath = Paths.get(uploadBasePath).toAbsolutePath().normalize();
    }

    @Override
    public void store(InputStream content, String storageKey, String contentType) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream read(String storageKey) throws IOException {
        Path target = resolve(storageKey);
        if (!Files.exists(target)) {
            throw new IOException("File not found at storage_key: " + storageKey);
        }
        return Files.newInputStream(target);
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path resolved = basePath.resolve(storageKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new IllegalArgumentException("storage_key escapes base path: " + storageKey);
        }
        return resolved;
    }
}

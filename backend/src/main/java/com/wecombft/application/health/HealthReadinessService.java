package com.wecombft.application.health;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.wecombft.interfaces.health.DependencyCheckPayload;
import com.wecombft.interfaces.health.FileStorageProperties;
import com.wecombft.interfaces.health.IntegrationModeProperties;
import com.wecombft.interfaces.health.RedisConnectionProperties;

@Service
public class HealthReadinessService {

    private static final Set<String> ALLOWED_MODES = Set.of("mock", "sandbox", "real");

    private final JdbcTemplate jdbcTemplate;
    private final RedisConnectionProperties redisConnectionProperties;
    private final FileStorageProperties fileStorageProperties;
    private final IntegrationModeProperties integrationModeProperties;

    public HealthReadinessService(
        JdbcTemplate jdbcTemplate,
        RedisConnectionProperties redisConnectionProperties,
        FileStorageProperties fileStorageProperties,
        IntegrationModeProperties integrationModeProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisConnectionProperties = redisConnectionProperties;
        this.fileStorageProperties = fileStorageProperties;
        this.integrationModeProperties = integrationModeProperties;
    }

    public List<DependencyCheckPayload> check() {
        List<DependencyCheckPayload> checks = new ArrayList<>();
        checks.add(checkDatabase());
        checks.add(checkRedis());
        checks.add(checkFileStorage());
        checks.add(checkIntegrationModes());
        return checks;
    }

    private DependencyCheckPayload checkDatabase() {
        return timed("database", () -> {
            Integer value = jdbcTemplate.queryForObject("select 1", Integer.class);
            if (Integer.valueOf(1).equals(value)) {
                return "reachable";
            }
            throw new IllegalStateException("unexpected validation result");
        });
    }

    private DependencyCheckPayload checkRedis() {
        return timed("redis", () -> {
            try (Socket socket = new Socket()) {
                socket.connect(
                    new InetSocketAddress(redisConnectionProperties.host(), redisConnectionProperties.port()),
                    500
                );
                return "tcp_reachable";
            }
        });
    }

    private DependencyCheckPayload checkFileStorage() {
        return timed("file_storage", () -> {
            Path path = Path.of(fileStorageProperties.uploadBasePath()).toAbsolutePath().normalize();
            Files.createDirectories(path);
            if (Files.isDirectory(path) && Files.isWritable(path)) {
                return fileStorageProperties.storageType();
            }
            throw new IllegalStateException("path is not writable");
        });
    }

    private DependencyCheckPayload checkIntegrationModes() {
        return timed("integration_modes", () -> {
            List<String> modes = List.of(
                integrationModeProperties.defaultMode(),
                integrationModeProperties.paymentMode(),
                integrationModeProperties.refundMode(),
                integrationModeProperties.logisticsMode(),
                integrationModeProperties.invoiceMode(),
                integrationModeProperties.wecomMode(),
                integrationModeProperties.messageMode()
            );
            boolean allSupported = modes.stream().allMatch(ALLOWED_MODES::contains);
            if (allSupported) {
                return "configured";
            }
            throw new IllegalStateException("unsupported integration mode");
        });
    }

    private DependencyCheckPayload timed(String name, ReadinessProbe probe) {
        Instant startedAt = Instant.now();
        try {
            String message = probe.check();
            return new DependencyCheckPayload(name, "UP", elapsedMillis(startedAt), message);
        } catch (RuntimeException ex) {
            return new DependencyCheckPayload(name, "DOWN", elapsedMillis(startedAt), ex.getClass().getSimpleName());
        } catch (Exception ex) {
            return new DependencyCheckPayload(name, "DOWN", elapsedMillis(startedAt), ex.getClass().getSimpleName());
        }
    }

    private long elapsedMillis(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }

    @FunctionalInterface
    private interface ReadinessProbe {
        String check() throws Exception;
    }
}

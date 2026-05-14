package com.wecombft.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class BackendPackageStructureTest {

    private static final Pattern APPLICATION_PUBLIC_DTO_RECORD = Pattern.compile(
        "\\bpublic\\s+record\\s+([A-Z][A-Za-z0-9]*(?:Command|Response|Page|Item|Summary))\\b");
    private static final Pattern CONTROLLER_NESTED_RECORD = Pattern.compile("\\bpublic\\s+record\\s+[A-Z][A-Za-z0-9]*\\b");
    private static final Pattern CONTROLLER_COMMAND_BODY = Pattern.compile("@RequestBody\\s+([A-Z][A-Za-z0-9]*Command)\\s+[a-zA-Z][A-Za-z0-9]*", Pattern.DOTALL);
    private static final Pattern INTERFACE_NESTED_APPLICATION_IMPORT = Pattern.compile(
        "import\\s+com\\.wecombft\\.application\\.[^;]+ApplicationService\\.[^;]+;");

    @Test
    void application_services_should_not_own_public_commands_or_dtos() throws IOException {
        List<String> violations = new ArrayList<>();
        for (SourceFile source : javaSources("backend/src/main/java/com/wecombft/application")) {
            if (source.path().toString().contains("\\application\\command\\")
                || source.path().toString().contains("/application/command/")) {
                continue;
            }
            APPLICATION_PUBLIC_DTO_RECORD.matcher(source.content()).results()
                .forEach(result -> violations.add(source.relativePath() + " -> " + result.group(1)));
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void controllers_should_not_define_inline_dtos_or_accept_application_commands() throws IOException {
        List<String> violations = new ArrayList<>();
        for (SourceFile source : javaSources("backend/src/main/java/com/wecombft/interfaces")) {
            if (!source.path().getFileName().toString().endsWith("Controller.java")) {
                continue;
            }
            CONTROLLER_NESTED_RECORD.matcher(source.content()).results()
                .forEach(result -> violations.add(source.relativePath() + " defines inline DTO " + result.group()));
            CONTROLLER_COMMAND_BODY.matcher(source.content()).results()
                .forEach(result -> violations.add(source.relativePath() + " accepts command body " + result.group(1)));
        }
        assertThat(violations).isEmpty();
    }

    @Test
    void interfaces_should_not_import_nested_application_service_payloads() throws IOException {
        List<String> violations = new ArrayList<>();
        for (SourceFile source : javaSources("backend/src/main/java/com/wecombft/interfaces")) {
            INTERFACE_NESTED_APPLICATION_IMPORT.matcher(source.content()).results()
                .forEach(result -> violations.add(source.relativePath() + " -> " + result.group()));
        }
        assertThat(violations).isEmpty();
    }

    private List<SourceFile> javaSources(String root) throws IOException {
        Path base = sourceRoot(root);
        try (Stream<Path> paths = Files.walk(base)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .map(path -> new SourceFile(path, base.relativize(path).toString(), read(path)))
                .toList();
        }
    }

    private Path sourceRoot(String root) {
        Path fromWorkspaceRoot = Path.of(root);
        if (Files.exists(fromWorkspaceRoot)) {
            return fromWorkspaceRoot;
        }
        String backendPrefix = "backend/";
        if (root.startsWith(backendPrefix)) {
            Path fromBackendRoot = Path.of(root.substring(backendPrefix.length()));
            if (Files.exists(fromBackendRoot)) {
                return fromBackendRoot;
            }
        }
        throw new IllegalStateException("Source root not found: " + root);
    }

    private String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read " + path, exception);
        }
    }

    private record SourceFile(Path path, String relativePath, String content) {
    }
}

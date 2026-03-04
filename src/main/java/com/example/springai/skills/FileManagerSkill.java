package com.example.springai.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * File management skills for the AI agent.
 * Allows the agent to read, write, and list files in a sandboxed workspace directory.
 */
@Configuration
public class FileManagerSkill {

    private static final Logger log = LoggerFactory.getLogger(FileManagerSkill.class);
    private static final Path WORKSPACE = Path.of(System.getProperty("user.dir"), "agent-workspace");

    // --- Read File ---

    public record ReadFileRequest(String filename) {}
    public record ReadFileResponse(String filename, String content, boolean success, String error) {}

    @Bean
    @Description("Read the contents of a file from the agent workspace. Provide the filename (relative to workspace).")
    public Function<ReadFileRequest, ReadFileResponse> readFile() {
        return request -> {
            log.info("Skill invoked: readFile({})", request.filename());
            try {
                Path filePath = resolveAndValidate(request.filename());
                String content = Files.readString(filePath);
                return new ReadFileResponse(request.filename(), content, true, null);
            } catch (Exception e) {
                log.error("Failed to read file: {}", e.getMessage());
                return new ReadFileResponse(request.filename(), null, false, e.getMessage());
            }
        };
    }

    // --- Write File ---

    public record WriteFileRequest(String filename, String content) {}
    public record WriteFileResponse(String filename, boolean success, String error) {}

    @Bean
    @Description("Write content to a file in the agent workspace. Provide the filename and content. Creates the file if it doesn't exist, overwrites if it does.")
    public Function<WriteFileRequest, WriteFileResponse> writeFile() {
        return request -> {
            log.info("Skill invoked: writeFile({})", request.filename());
            try {
                Path filePath = resolveAndValidate(request.filename());
                Files.createDirectories(filePath.getParent());
                Files.writeString(filePath, request.content());
                return new WriteFileResponse(request.filename(), true, null);
            } catch (Exception e) {
                log.error("Failed to write file: {}", e.getMessage());
                return new WriteFileResponse(request.filename(), false, e.getMessage());
            }
        };
    }

    // --- List Files ---

    public record ListFilesRequest(String directory) {}
    public record ListFilesResponse(String directory, List<String> files, boolean success, String error) {}

    @Bean
    @Description("List files in a directory within the agent workspace. Provide a directory path relative to workspace, or empty string for the root.")
    public Function<ListFilesRequest, ListFilesResponse> listFiles() {
        return request -> {
            String dir = (request.directory() != null && !request.directory().isBlank())
                    ? request.directory() : "";
            log.info("Skill invoked: listFiles({})", dir);
            try {
                Path dirPath = resolveAndValidate(dir);
                if (!Files.isDirectory(dirPath)) {
                    return new ListFilesResponse(dir, List.of(), false, "Not a directory: " + dir);
                }
                try (Stream<Path> stream = Files.list(dirPath)) {
                    List<String> files = stream
                            .map(p -> WORKSPACE.relativize(p).toString() + (Files.isDirectory(p) ? "/" : ""))
                            .sorted()
                            .collect(Collectors.toList());
                    return new ListFilesResponse(dir, files, true, null);
                }
            } catch (Exception e) {
                log.error("Failed to list files: {}", e.getMessage());
                return new ListFilesResponse(dir, List.of(), false, e.getMessage());
            }
        };
    }

    /**
     * Resolve a path relative to workspace and validate it doesn't escape the sandbox.
     */
    private Path resolveAndValidate(String relativePath) throws IOException {
        Files.createDirectories(WORKSPACE);
        Path resolved = WORKSPACE.resolve(relativePath).normalize();
        if (!resolved.startsWith(WORKSPACE)) {
            throw new SecurityException("Path traversal detected — access denied: " + relativePath);
        }
        return resolved;
    }
}

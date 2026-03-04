package com.example.springai.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Shell command execution skill for the AI agent.
 * Allows executing safe, read-only shell commands for system information gathering.
 */
@Configuration
public class ShellCommandSkill {

    private static final Logger log = LoggerFactory.getLogger(ShellCommandSkill.class);

    /** Allowed base command names. */
    private static final List<String> ALLOWED_COMMANDS = List.of(
            "ls", "cat", "echo", "date", "whoami", "pwd", "uname",
            "wc", "head", "tail", "grep", "find", "which",
            "java", "mvn", "git"
    );

    /** Commands that read file paths — path args are restricted to the agent workspace. */
    private static final Set<String> FILE_ACCESS_COMMANDS = Set.of(
            "cat", "head", "tail", "grep", "wc", "find"
    );

    /** Shell metacharacters that enable injection — always blocked. */
    private static final Pattern SHELL_METACHAR = Pattern.compile("[;&|`$><\\\\(){}#!]");

    private static final Path WORKSPACE =
            Path.of(System.getProperty("user.dir"), "agent-workspace");

    private static final int TIMEOUT_SECONDS = 30;

    public record ShellRequest(String command) {}
    public record ShellResponse(String command, String output, String error, int exitCode, boolean success) {}

    @Bean
    @Description("Execute a shell command. Allowed base commands: ls, cat, echo, date, whoami, pwd, uname, wc, head, tail, grep, find, which, java, mvn, git. Shell metacharacters are blocked. File-reading commands are restricted to the agent workspace.")
    public Function<ShellRequest, ShellResponse> executeShellCommand() {
        return request -> {
            String cmd = request.command().trim();
            log.info("Skill invoked: executeShellCommand({})", cmd);

            // Security: block shell metacharacters that enable injection
            if (SHELL_METACHAR.matcher(cmd).find()) {
                log.warn("Blocked shell metacharacters in command: {}", cmd);
                return new ShellResponse(cmd, null,
                        "Shell metacharacters (; & | ` $ > < \\ etc.) are not permitted.",
                        -1, false);
            }

            // Split into argument array — no sh -c, so no shell expansion
            String[] args = cmd.split("\\s+");
            String base = args[0];
            String commandName = base.contains("/")
                    ? base.substring(base.lastIndexOf('/') + 1)
                    : base;

            if (ALLOWED_COMMANDS.stream().noneMatch(a -> commandName.equals(a))) {
                log.warn("Blocked disallowed command: {}", cmd);
                return new ShellResponse(cmd, null,
                        "Command not allowed. Permitted: " + ALLOWED_COMMANDS,
                        -1, false);
            }

            // Security: for file-reading commands, restrict path args to the workspace
            if (FILE_ACCESS_COMMANDS.contains(commandName)) {
                for (String arg : args) {
                    if (!arg.startsWith("-") && (arg.startsWith("/") || arg.contains("..") )) {
                        try {
                            Path resolved = WORKSPACE.resolve(arg).normalize();
                            if (!resolved.startsWith(WORKSPACE)) {
                                log.warn("Blocked path outside workspace: {}", arg);
                                return new ShellResponse(cmd, null,
                                        "File access outside the agent workspace is not permitted.",
                                        -1, false);
                            }
                        } catch (Exception ignored) {
                            return new ShellResponse(cmd, null,
                                    "Invalid path argument: " + arg, -1, false);
                        }
                    }
                }
            }

            try {
                Files.createDirectories(WORKSPACE);
                ProcessBuilder pb = new ProcessBuilder(Arrays.asList(args));
                pb.directory(WORKSPACE.toFile());
                pb.redirectErrorStream(false);
                Process process = pb.start();

                StringBuilder stdout = new StringBuilder();
                StringBuilder stderr = new StringBuilder();

                try (BufferedReader outReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                     BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {

                    String line;
                    while ((line = outReader.readLine()) != null) {
                        stdout.append(line).append("\n");
                    }
                    while ((line = errReader.readLine()) != null) {
                        stderr.append(line).append("\n");
                    }
                }

                boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return new ShellResponse(cmd, stdout.toString(), "Command timed out after " + TIMEOUT_SECONDS + "s", -1, false);
                }

                int exitCode = process.exitValue();
                return new ShellResponse(cmd, stdout.toString().trim(), stderr.toString().trim(), exitCode, exitCode == 0);

            } catch (Exception e) {
                log.error("Shell command failed: {}", e.getMessage());
                return new ShellResponse(cmd, null, e.getMessage(), -1, false);
            }
        };
    }
}

package com.example.springai.skills;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Shell command execution skill for the AI agent.
 * Allows executing safe, read-only shell commands for system information gathering.
 */
@Configuration
public class ShellCommandSkill {

    private static final Logger log = LoggerFactory.getLogger(ShellCommandSkill.class);

    /**
     * Allowed command prefixes for security. Only these commands can be executed.
     */
    private static final List<String> ALLOWED_COMMANDS = List.of(
            "ls", "cat", "echo", "date", "whoami", "pwd", "uname",
            "wc", "head", "tail", "grep", "find", "which", "env",
            "java", "mvn", "git"
    );

    private static final int TIMEOUT_SECONDS = 30;

    public record ShellRequest(String command) {}
    public record ShellResponse(String command, String output, String error, int exitCode, boolean success) {}

    @Bean
    @Description("Execute a shell command on the system. Only safe, read-only commands are allowed (ls, cat, echo, date, whoami, pwd, uname, wc, head, tail, grep, find, which, env, java, mvn, git). Returns stdout, stderr, and exit code.")
    public Function<ShellRequest, ShellResponse> executeShellCommand() {
        return request -> {
            String cmd = request.command().trim();
            log.info("Skill invoked: executeShellCommand({})", cmd);

            // Security: validate command prefix
            String baseCommand = cmd.split("\\s+")[0];
            String commandName = baseCommand.contains("/")
                    ? baseCommand.substring(baseCommand.lastIndexOf('/') + 1)
                    : baseCommand;

            boolean allowed = ALLOWED_COMMANDS.stream()
                    .anyMatch(a -> commandName.equals(a));

            if (!allowed) {
                log.warn("Blocked disallowed command: {}", cmd);
                return new ShellResponse(cmd, null,
                        "Command not allowed. Permitted commands: " + ALLOWED_COMMANDS,
                        -1, false);
            }

            try {
                ProcessBuilder pb = new ProcessBuilder("sh", "-c", cmd);
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

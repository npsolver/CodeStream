package com.pipeline.worker.service;

import com.pipeline.worker.config.ExecutionDockerProperties;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

@Service
public class CodeExecutionService {

    private final ExecutionDockerProperties docker;

    public CodeExecutionService(ExecutionDockerProperties docker) {
        this.docker = docker;
    }

    public ExecutionResponse executePython(String code) {
        Path workDir = null;

        try {
            workDir = Files.createTempDirectory("code-exec-");
            Path codeFile = workDir.resolve("code.py");
            Files.writeString(codeFile, code, StandardCharsets.UTF_8);
            makeReadableByContainerUser(workDir, codeFile);

            Process process = new ProcessBuilder(buildDockerCommand(workDir))
                    .redirectErrorStream(false)
                    .start();

            ExecutorService executor = Executors.newFixedThreadPool(2);

            Future<String> stdoutFuture = executor.submit(
                    () -> readStreamWithLimit(process.getInputStream(), docker.getMaxOutputBytes()));

            Future<String> stderrFuture = executor.submit(
                    () -> readStreamWithLimit(process.getErrorStream(), docker.getMaxOutputBytes()));

            boolean finished = process.waitFor(docker.getTimeoutSeconds(), TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                executor.shutdownNow();
                return ExecutionResponse.timeout();
            }

            String stdout = stdoutFuture.get(docker.getTimeoutSeconds(), TimeUnit.SECONDS);
            String stderr = stderrFuture.get(docker.getTimeoutSeconds(), TimeUnit.SECONDS);

            executor.shutdown();

            int exitCode = process.exitValue();
            return ExecutionResponse.from(stdout, stderr, exitCode);

        } catch (TimeoutException e) {
            return ExecutionResponse.timeout();

        } catch (Exception e) {
            return ExecutionResponse.error(e.getMessage());

        } finally {
            if (workDir != null) {
                deleteRecursively(workDir);
            }
        }
    }

    private List<String> buildDockerCommand(Path workDir) {
        List<String> command = new ArrayList<>();
        command.add(docker.getBinary());
        command.add("run");
        command.add("--rm");
        command.add("--network");
        command.add("none");
        command.add("--memory");
        command.add(docker.getMemoryMb() + "m");
        command.add("--cpus");
        command.add(String.valueOf(docker.getCpus()));
        command.add("--pids-limit");
        command.add(String.valueOf(docker.getPidsLimit()));
        command.add("--read-only");
        command.add("--tmpfs");
        command.add("/tmp:rw,noexec,nosuid,size=64m");
        command.add("-v");
        command.add(workDir.toAbsolutePath() + ":/workspace:ro");
        command.add(docker.getImage());
        command.add("python3");
        command.add("/workspace/code.py");
        return command;
    }

    // Worker may run as a different uid than the container user (uid 1000 on EC2).
    private static void makeReadableByContainerUser(Path workDir, Path codeFile) throws IOException {
        if (!Files.getFileStore(workDir).supportsFileAttributeView("posix")) {
            return;
        }
        Set<PosixFilePermission> dirPermissions = PosixFilePermissions.fromString("rwxr-xr-x");
        Set<PosixFilePermission> filePermissions = PosixFilePermissions.fromString("rw-r--r--");
        Files.setPosixFilePermissions(workDir, dirPermissions);
        Files.setPosixFilePermissions(codeFile, filePermissions);
    }

    private void deleteRecursively(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (var entries = Files.list(path)) {
                    entries.forEach(this::deleteRecursively);
                }
            }
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private String readStreamWithLimit(InputStream stream, int maxBytes) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();

        int total = 0;
        String line;

        while ((line = reader.readLine()) != null) {
            total += line.length();

            if (total > maxBytes) {
                throw new RuntimeException("Output too large");
            }

            result.append(line).append("\n");
        }

        return result.toString();
    }
}

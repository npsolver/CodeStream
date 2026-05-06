package com.pipeline.worker.service;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

@Service
public class CodeExecutionService {

    private static final int TIMEOUT_SECONDS = 3;
    private static final int MAX_OUTPUT_SIZE = 10_000; // 10 KB

    public ExecutionResponse executePython(String code) {

        File tempFile = null;

        try {
            // 1. Create temp file
            tempFile = File.createTempFile("code-", ".py");

            try (FileWriter writer = new FileWriter(tempFile)) {
                writer.write(code);
            }

            // 2. Start process
            Process process = new ProcessBuilder("python3", tempFile.getAbsolutePath())
                    .start();

            // 3. Read stdout & stderr in parallel
            ExecutorService executor = Executors.newFixedThreadPool(2);

            Future<String> stdoutFuture = executor
                    .submit(() -> readStreamWithLimit(process.getInputStream(), MAX_OUTPUT_SIZE));

            Future<String> stderrFuture = executor
                    .submit(() -> readStreamWithLimit(process.getErrorStream(), MAX_OUTPUT_SIZE));

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                executor.shutdownNow();
                return ExecutionResponse.timeout();
            }

            String stdout = stdoutFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            executor.shutdown();

            int exitCode = process.exitValue();

            return ExecutionResponse.from(stdout, stderr, exitCode);

        } catch (TimeoutException e) {
            return ExecutionResponse.timeout();

        } catch (Exception e) {
            return ExecutionResponse.error(e.getMessage());

        } finally {
            // 4. Cleanup
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    // 🔒 Safe stream reader with output limit
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
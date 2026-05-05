package com.pipeline.worker.service;

import org.springframework.stereotype.Service;
import java.io.*;

@Service
public class CodeExecutionService {

    public String executePython(String code) throws Exception {

        File tempFile = File.createTempFile("code", ".py");

        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(code);
        }

        Process process = new ProcessBuilder("python3", tempFile.getAbsolutePath())
                .redirectErrorStream(true)
                .start();

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));

        StringBuilder output = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            output.append(line).append("\n");
        }

        process.waitFor();

        return output.toString();
    }
}

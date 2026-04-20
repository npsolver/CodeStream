package com.pipeline.api;

import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/execute")
public class CodeController {

    @PostMapping
    public String execute(@RequestBody Map<String, String> request) {

        String code = request.get("code");

        System.out.println("Received code: " + code);

        // For now, just return a jobId
        return UUID.randomUUID().toString();
    }
}
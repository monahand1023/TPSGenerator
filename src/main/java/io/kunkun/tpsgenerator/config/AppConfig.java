package com.example.tpsgenerator.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Application configuration for TPS Generator.
 * This class holds global application settings and shared resources.
 */
@Data
@Slf4j
public class AppConfig {

    /**
     * Default connection timeout for HTTP clients.
     */
    private Duration connectionTimeout = Duration.ofSeconds(10);

    /**
     * Default response timeout for HTTP requests.
     */
    private Duration responseTimeout = Duration.ofSeconds(30);

    /**
     * Output directory for test results.
     */
    private String outputDirectory = "results";

    /**
     * Whether to enable verbose logging.
     */
    private boolean verboseLogging = false;

    /**
     * Whether to include request/response bodies in logs.
     */
    private boolean logRequestBodies = false;

    /**
     * Global HTTP client instance.
     */
    private HttpClient httpClient;

    /**
     * Global object mapper instance for JSON processing.
     */
    private ObjectMapper objectMapper;

    /**
     * Global scheduler for background tasks.
     */
    private ScheduledExecutorService scheduler;

    /**
     * Initializes the application configuration.
     */
    public void initialize() {
        // Initialize HTTP client
        httpClient = HttpClient.newBuilder()
                .connectTimeout(connectionTimeout)
                .build();

        // Initialize object mapper
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Initialize scheduler
        scheduler = Executors.newScheduledThreadPool(2);

        // Ensure output directory exists
        File outputDir = new File(outputDirectory);
        if (!outputDir.exists()) {
            if (outputDir.mkdirs()) {
                log.info("Created output directory: {}", outputDir.getAbsolutePath());
            } else {
                log.warn("Failed to create output directory: {}", outputDir.getAbsolutePath());
            }
        }

        log.info("Application configuration initialized");
    }

    /**
     * Shuts down resources.
     */
    public void shutdown() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        log.info("Application resources shutdown completed");
    }
}
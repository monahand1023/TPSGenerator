package io.kunkun.tpsgenerator.request;

import io.kunkun.tpsgenerator.config.TestConfig;
import io.kunkun.tpsgenerator.factory.ParameterSourceFactory;
import io.kunkun.tpsgenerator.request.parameter.ParameterSource;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Generates HTTP requests from templates using parameter sources.
 */
@Slf4j
public class RequestGenerator {

    private final List<RequestTemplate> requestTemplates;
    private final Map<String, ParameterSource> parameterSources = new ConcurrentHashMap<>();
    private final int[] templateWeights;
    private final int totalWeight;
    private final Random random = new Random();

    /**
     * Creates a new RequestGenerator.
     *
     * @param config the test configuration
     */
    public RequestGenerator(TestConfig config) {
        this.requestTemplates = config.getRequestTemplates();

        // Initialize parameter sources using factory
        for (Map.Entry<String, TestConfig.ParameterSourceConfig> entry :
                config.getParameterSources().entrySet()) {
            String paramName = entry.getKey();
            TestConfig.ParameterSourceConfig sourceConfig = entry.getValue();

            try {
                ParameterSource source = ParameterSourceFactory.create(sourceConfig);
                parameterSources.put(paramName, source);
                log.info("Initialized parameter source for '{}'", paramName);
            } catch (Exception e) {
                log.error("Failed to initialize parameter source for '{}': {}",
                        paramName, e.getMessage(), e);
            }
        }

        // Calculate template weights for weighted random selection
        templateWeights = new int[requestTemplates.size()];
        int weightSum = 0;
        for (int i = 0; i < requestTemplates.size(); i++) {
            weightSum += requestTemplates.get(i).getWeight();
            templateWeights[i] = weightSum;
        }
        this.totalWeight = weightSum;

        log.info("Initialized request generator with {} templates and {} parameter sources",
                requestTemplates.size(), parameterSources.size());
    }

    /**
     * Generates an HTTP request.
     *
     * @param requestId the request ID
     * @param elapsedTimeMs the elapsed time since test start in milliseconds
     * @return the generated HTTP request
     * @throws RequestGenerationException if request generation fails
     */
    public HttpRequest generateRequest(long requestId, long elapsedTimeMs) {
        try {
            // Select template based on weights
            RequestTemplate template = selectTemplate();

            // Generate parameters
            Map<String, String> parameters = generateParameters(requestId, elapsedTimeMs);

            // Generate request from template
            return template.generate(parameters);

        } catch (Exception e) {
            String message = String.format("Failed to generate request %d: %s", requestId, e.getMessage());
            log.error(message, e);
            throw new RequestGenerationException(message, e);
        }
    }

    /**
     * Selects a request template using weighted random selection.
     *
     * @return the selected template
     */
    private RequestTemplate selectTemplate() {
        if (requestTemplates.size() == 1) {
            return requestTemplates.get(0);
        }

        int randomWeight = random.nextInt(totalWeight);
        for (int i = 0; i < templateWeights.length; i++) {
            if (randomWeight < templateWeights[i]) {
                return requestTemplates.get(i);
            }
        }

        // Should never happen if weights are calculated correctly
        return requestTemplates.get(0);
    }

    /**
     * Generates parameter values.
     *
     * @param requestId the request ID
     * @param elapsedTimeMs the elapsed time since test start in milliseconds
     * @return the parameter values
     */
    private Map<String, String> generateParameters(long requestId, long elapsedTimeMs) {
        Map<String, String> parameters = new HashMap<>();

        // Add default parameters
        parameters.put("requestId", String.valueOf(requestId));
        parameters.put("timestamp", String.valueOf(System.currentTimeMillis()));
        parameters.put("elapsedTime", String.valueOf(elapsedTimeMs));

        // Add parameters from sources
        for (Map.Entry<String, ParameterSource> entry : parameterSources.entrySet()) {
            String paramName = entry.getKey();
            ParameterSource source = entry.getValue();

            try {
                String value = source.getValue();
                parameters.put(paramName, value);
            } catch (Exception e) {
                log.warn("Failed to get value for parameter '{}': {}",
                        paramName, e.getMessage());
                // Use a fallback value
                parameters.put(paramName, "error");
            }
        }

        return parameters;
    }

    /**
     * Gets the list of parameter names.
     *
     * @return the parameter names
     */
    public List<String> getParameterNames() {
        return parameterSources.keySet().stream()
                .sorted()
                .collect(Collectors.toList());
    }
}
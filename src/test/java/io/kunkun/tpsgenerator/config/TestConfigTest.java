package io.kunkun.tpsgenerator.config;

import io.kunkun.tpsgenerator.request.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for TestConfig validation.
 */
class TestConfigTest {

    private TestConfig config;

    @BeforeEach
    void setUp() {
        config = new TestConfig();
        config.setName("Test");
        config.setTestDuration(Duration.ofMinutes(1));

        TestConfig.TrafficConfig trafficConfig = new TestConfig.TrafficConfig();
        trafficConfig.setType("stable");
        trafficConfig.setTargetTps(100);
        config.setTrafficPattern(trafficConfig);

        TestConfig.ThreadPoolConfig threadPoolConfig = new TestConfig.ThreadPoolConfig();
        threadPoolConfig.setCoreSize(10);
        threadPoolConfig.setMaxSize(50);
        config.setThreadPool(threadPoolConfig);

        RequestTemplate template = new RequestTemplate();
        template.setUrlTemplate("http://example.com");
        template.setMethod("GET");
        config.setRequestTemplates(Collections.singletonList(template));
    }

    @Test
    @DisplayName("Should validate successfully with valid config")
    void shouldValidateSuccessfully() {
        assertDoesNotThrow(() -> config.validate());
    }

    @Test
    @DisplayName("Should throw exception for null name")
    void shouldThrowExceptionForNullName() {
        config.setName(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    @DisplayName("Should throw exception for blank name")
    void shouldThrowExceptionForBlankName() {
        config.setName("   ");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("name"));
    }

    @Test
    @DisplayName("Should throw exception for null test duration")
    void shouldThrowExceptionForNullTestDuration() {
        config.setTestDuration(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("duration"));
    }

    @Test
    @DisplayName("Should throw exception for zero test duration")
    void shouldThrowExceptionForZeroTestDuration() {
        config.setTestDuration(Duration.ZERO);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("duration"));
    }

    @Test
    @DisplayName("Should throw exception for negative test duration")
    void shouldThrowExceptionForNegativeTestDuration() {
        config.setTestDuration(Duration.ofMinutes(-1));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("duration"));
    }

    @Test
    @DisplayName("Should throw exception for null traffic pattern")
    void shouldThrowExceptionForNullTrafficPattern() {
        config.setTrafficPattern(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Traffic pattern"));
    }

    @Test
    @DisplayName("Should throw exception for null traffic pattern type")
    void shouldThrowExceptionForNullTrafficPatternType() {
        config.getTrafficPattern().setType(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Traffic pattern type"));
    }

    @Test
    @DisplayName("Should throw exception for zero target TPS")
    void shouldThrowExceptionForZeroTargetTps() {
        config.getTrafficPattern().setTargetTps(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Target TPS"));
    }

    @Test
    @DisplayName("Should throw exception for negative target TPS")
    void shouldThrowExceptionForNegativeTargetTps() {
        config.getTrafficPattern().setTargetTps(-100);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Target TPS"));
    }

    @Test
    @DisplayName("Should throw exception for null thread pool")
    void shouldThrowExceptionForNullThreadPool() {
        config.setThreadPool(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Thread pool"));
    }

    @Test
    @DisplayName("Should throw exception for zero core size")
    void shouldThrowExceptionForZeroCoreSize() {
        config.getThreadPool().setCoreSize(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("core size"));
    }

    @Test
    @DisplayName("Should throw exception for max size less than core size")
    void shouldThrowExceptionForMaxSizeLessThanCoreSize() {
        config.getThreadPool().setCoreSize(50);
        config.getThreadPool().setMaxSize(10);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("max size"));
    }

    @Test
    @DisplayName("Should throw exception for null request templates")
    void shouldThrowExceptionForNullRequestTemplates() {
        config.setRequestTemplates(null);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("request template"));
    }

    @Test
    @DisplayName("Should throw exception for empty request templates")
    void shouldThrowExceptionForEmptyRequestTemplates() {
        config.setRequestTemplates(Collections.emptyList());

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("request template"));
    }
}

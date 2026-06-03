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
    @DisplayName("Should accept a null thread pool (optional under the virtual-thread engine)")
    void shouldAcceptNullThreadPool() {
        // threadPool is vestigial — the engine runs one virtual thread per request — so a
        // config that omits it must validate successfully.
        config.setThreadPool(null);

        assertDoesNotThrow(() -> config.validate());
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
    @DisplayName("rampUp without rampDuration is rejected (would NPE in the factory)")
    void rampUpWithoutRampDurationThrows() {
        TestConfig.TrafficConfig t = new TestConfig.TrafficConfig();
        t.setType("rampUp");
        t.setTargetTps(100);
        config.setTrafficPattern(t);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> config.validate());
        assertTrue(ex.getMessage().contains("rampDuration"), ex.getMessage());
    }

    @Test
    @DisplayName("spike without spikeDuration is rejected (would NPE in the factory)")
    void spikeWithoutSpikeDurationThrows() {
        TestConfig.TrafficConfig t = new TestConfig.TrafficConfig();
        t.setType("spike");
        t.setTargetTps(50);
        t.setSpikeStartTime(Duration.ofSeconds(5));
        config.setTrafficPattern(t);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> config.validate());
        assertTrue(ex.getMessage().contains("spikeDuration"), ex.getMessage());
    }

    @Test
    @DisplayName("Should throw exception for submissionThreads below 1")
    void shouldThrowExceptionForInvalidSubmissionThreads() {
        config.setSubmissionThreads(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("submissionThreads"));
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

    @Test
    @DisplayName("Custom pattern with targetTps=0 should pass validation")
    void customPatternWithZeroTargetTpsShouldPassValidation() {
        TestConfig.TrafficConfig customTraffic = new TestConfig.TrafficConfig();
        customTraffic.setType("custom");
        customTraffic.setTargetTps(0); // intentionally unset for custom patterns
        customTraffic.setPatternFile("patterns/my-pattern.csv");
        config.setTrafficPattern(customTraffic);

        assertDoesNotThrow(() -> config.validate(),
                "Custom pattern type should not require targetTps > 0");
    }

    @Test
    @DisplayName("Non-custom pattern with targetTps=0 should fail validation")
    void nonCustomPatternWithZeroTargetTpsShouldFailValidation() {
        config.getTrafficPattern().setType("stable");
        config.getTrafficPattern().setTargetTps(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> config.validate()
        );
        assertTrue(exception.getMessage().contains("Target TPS must be positive"),
                "Expected 'Target TPS must be positive' in: " + exception.getMessage());
    }
}

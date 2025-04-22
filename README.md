# TPS Generator

A robust, flexible, and feature-rich load testing tool for generating controlled HTTP traffic patterns.

## Features

- **Flexible Traffic Patterns**: Stable, Ramp-up, Spike, and Custom patterns
- **Parameterized Requests**: Create dynamic requests with values from files or random generators
- **Comprehensive Metrics Collection**: Response times, success rates, TPS, resource usage
- **Real-time Monitoring**: Track test progress with live metrics
- **Dashboard Integration**: Visualize results with InfluxDB/Grafana or custom dashboard
- **Circuit Breaker Protection**: Automatically stop tests when error rates exceed thresholds
- **Detailed Reports**: Export results to CSV for analysis

## Getting Started

### Prerequisites

- Java 11 or higher
- Maven 3.6 or higher

### Building the Project

```bash
mvn clean package
```

This will create a runnable JAR file in the `target` directory.

### Running a Test

```bash
java -jar target/tps-generator-1.0.0.jar path/to/test-config.json [output-directory]
```

Where:
- `test-config.json` is your test configuration file
- `output-directory` (optional) is where results will be stored (default: `results`)

## Configuration

TPS Generator uses JSON configuration files to define test parameters. Here's a sample configuration:

```json
{
  "name": "API Load Test",
  "targetServiceUrl": "http://api.example.com",
  "testDuration": "10m",
  
  "trafficPattern": {
    "type": "rampUp",
    "startTps": 10,
    "targetTps": 100,
    "rampDuration": "2m"
  },
  
  "requestTemplates": [
    {
      "name": "getUser",
      "weight": 70,
      "method": "GET",
      "urlTemplate": "http://api.example.com/users/${userId}"
    }
  ],
  
  "parameterSources": {
    "userId": {
      "type": "random",
      "range": [1000, 9999]
    }
  }
}
```

See `test-config.json` for a complete example with all available options.

## Traffic Patterns

### Stable Pattern

Maintains a constant TPS rate throughout the test.

```json
"trafficPattern": {
  "type": "stable",
  "targetTps": 100
}
```

### Ramp-up Pattern

Linearly increases TPS from a start value to a target value over time.

```json
"trafficPattern": {
  "type": "rampUp",
  "startTps": 10,
  "targetTps": 100,
  "rampDuration": "2m"
}
```

### Spike Pattern

Maintains a base TPS with spikes of higher TPS at specific times.

```json
"trafficPattern": {
  "type": "spike",
  "targetTps": 50,
  "spikeTps": 200,
  "spikeStartTime": "5m",
  "spikeDuration": "30s"
}
```

## Parameter Sources

### Random Parameters

Generate random values within specified ranges:

```json
"userId": {
  "type": "random",
  "range": [1000, 9999]
}
```

With normal distribution:

```json
"quantity": {
  "type": "random",
  "distribution": "normal",
  "mean": 3,
  "stddev": 1,
  "min": 1,
  "max": 10
}
```

### File Parameters

Read values from text or CSV files:

```json
"authToken": {
  "type": "file",
  "path": "tokens.txt",
  "selection": "round-robin"
}
```

## Dashboard Integration

### Using InfluxDB and Grafana

1. Enable InfluxDB integration in the config:

```json
"influxDb": {
  "enabled": true,
  "url": "http://localhost:8086",
  "token": "your_influxdb_token",
  "org": "your_org",
  "bucket": "tps_metrics"
}
```

2. Import the provided Grafana dashboard template (in `dashboards/grafana-dashboard.json`)

### CSV Output

Test results are automatically exported to CSV files:
- `results.csv`: Main test metrics
- `tps_samples.csv`: TPS measurements over time
- `resource_snapshots.csv`: CPU and memory usage over time

## Advanced Configuration

### Thread Pool Settings

Configure the executor service:

```json
"threadPool": {
  "coreSize": 20,
  "maxSize": 50,
  "queueSize": 100,
  "keepAliveTime": "60s"
}
```

### Circuit Breaker

Automatically stop tests when error rates are too high:

```json
"circuitBreaker": {
  "enabled": true,
  "errorThreshold": 0.5,
  "windowSize": 100
}
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.
# TPS Generator

When I was working at Amazon, we had a tool called "TPS Generator" that we would use to simulate traffic and generate traffic loads. I know that you can do a very quick solution using a tool like Postman (https://blog.postman.com/postman-api-performance-testing/), but if you need something a bit more customizable and flexible, you may need to build out your own solution.
My solution here is a robust, flexible, and feature-rich load testing tool for generating controlled HTTP traffic patterns to test API performance and reliability. If you also need to use a quick scaffolding to mock a service, be sure to check out my TPSGenerator-Service to quickly do this: https://github.com/monahand1023/TPSGenerator-Server

## Table of Contents



- [Project Overview](#project-overview)
- [Features](#features)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Building the Project](#building-the-project)
    - [Configuration](#configuration)
- [Usage](#usage)
    - [Basic Usage](#basic-usage)
    - [Traffic Patterns](#traffic-patterns)
    - [Request Templates](#request-templates)
    - [Parameter Sources](#parameter-sources)
- [Metrics and Reports](#metrics-and-reports)
- [Advanced Features](#advanced-features)
    - [Circuit Breaker](#circuit-breaker)
    - [Dashboard Integration](#dashboard-integration)
    - [Resource Monitoring](#resource-monitoring)
- [License](#license)

## Project Overview

TPS Generator is a Java-based load testing tool designed to generate controlled HTTP traffic with configurable patterns. It allows you to test the performance, reliability, and scalability of your APIs and web services by simulating realistic traffic conditions. The tool provides comprehensive metrics collection, resource monitoring, and detailed reporting capabilities.

## Features

- **Flexible Traffic Patterns**: Configure stable, ramp-up, spike, or custom traffic patterns


- **Parameterized Requests**: Create dynamic requests with values from files or random generators
- **Comprehensive Metrics Collection**: Measure response times, success rates, TPS, and more
- **Resource Monitoring**: Track CPU, memory, and thread usage during tests
- **Real-time Monitoring**: View test progress and metrics as the test runs
- **Dashboard Integration**: Visualize results with customizable dashboards
- **Circuit Breaker Protection**: Automatically stop tests when error rates exceed thresholds
- **Detailed Reports**: Export results to CSV for analysis

## Architecture

The TPS Generator is built with a modular architecture focusing on separation of concerns:

### Core Components

- **ExecutionController**: Orchestrates the test execution, manages threads, and controls traffic rates
- **RequestExecutor**: Executes HTTP requests and collects performance metrics
- **CircuitBreaker**: Monitors success/failure rates and prevents excessive failures

### Traffic Patterns

The tool supports various traffic pattern implementations:

- **StablePattern**: Maintains a constant TPS throughout the test
- **RampUpPattern**: Linearly increases TPS from a start value to a target value
- **SpikePattern**: Maintains a base TPS with spikes of higher TPS at specified times
- **CustomPattern**: Allows defining custom TPS values over time

### Request Management

- **RequestGenerator**: Generates HTTP requests based on templates and parameter sources
- **RequestTemplate**: Defines the structure of requests with parameter placeholders
- **Parameter Sources**: Provide values for parameters (file-based, random)

### Metrics and Monitoring

- **MetricsCollector**: Collects and aggregates test metrics
- **ResourceMonitor**: Monitors system resources (CPU, memory, threads)
- **ErrorAnalyzer**: Analyzes errors and failures during test execution
- **ResponseValidator**: Validates HTTP responses against configured criteria

### Reporting

- **CSVExporter**: Exports metrics to CSV files for analysis
- **DashboardClient**: Sends metrics to a dashboard service for visualization

## Getting Started

### Prerequisites

- Java 11 or higher
- Maven 3.6 or higher

### Building the Project

Clone the repository and build the project using Maven:



```bash
git clone https://github.com/monahand1023/tps-generator.git
cd tps-generator
mvn clean package
```

This will create a runnable JAR file in the `target` directory.

### Configuration

TPS Generator uses JSON configuration files to define test parameters. Here's a sample configuration:

```json
{
  "name": "Mock API Load Test",
  "targetServiceUrl": "http://localhost:8080",
  "testDuration": "PT2M",

  "trafficPattern": {
    "type": "spike",
    "targetTps": 30,
    "spikeTps": 100,
    "spikeStartTime": "PT45S",
    "spikeDuration": "PT15S"
  },

  "threadPool": {
    "coreSize": 20,
    "maxSize": 50,
    "queueSize": 100,
    "keepAliveTime": "PT60S"
  },

  "requestTemplates": [
    {
      "name": "getResource",
      "weight": 70,
      "method": "GET",
      "urlTemplate": "http://localhost:8080/resources",
      "headers": {
        "Authorization": "Bearer token123",
        "Accept": "application/json"
      }
    },
    {
      "name": "createResource",
      "weight": 30,
      "method": "POST",
      "urlTemplate": "http://localhost:8080/orders",
      "headers": {
        "Content-Type": "application/json",
        "Authorization": "Bearer token123"
      },
      "bodyTemplate": "{\"productId\":\"${productId}\",\"quantity\":${quantity}}"
    }
  ],

  "parameterSources": {
    "resourceId": {
      "type": "random",
      "range": [1, 1000]
    },
    "productId": {
      "type": "random",
      "range": [100, 999]
    },
    "quantity": {
      "type": "random",
      "distribution": "normal",
      "mean": 3,
      "stddev": 1,
      "min": 1,
      "max": 10
    }
  },

  "metrics": {
    "responseTimePercentiles": [50, 90, 95, 99],
    "outputFile": "results.csv",
    "resourceMonitoring": {
      "enabled": true,
      "sampleInterval": "PT5S"
    }
  },

  "circuitBreaker": {
    "enabled": true,
    "errorThreshold": 0.3,
    "windowSize": 50
  }
}
```

## Usage

### Basic Usage

Run a test using the following command:

```bash
java -jar target/tps-generator-1.0.0.jar path/to/test-config.json [output-directory]
```

Where:
- `test-config.json` is your test configuration file
- `output-directory` (optional) is where results will be stored (default: `results`)

For verbose logging, add the `--verbose` flag:

```bash
java -jar target/tps-generator-1.0.0.jar path/to/test-config.json results --verbose
```

### Traffic Patterns

The tool supports several traffic patterns:

#### Stable Pattern

Maintains a constant TPS rate throughout the test:

```json
"trafficPattern": {
  "type": "stable",
  "targetTps": 100
}
```

#### Ramp-up Pattern

Linearly increases TPS from a start value to a target value:

```json
"trafficPattern": {
  "type": "rampUp",
  "startTps": 10,
  "targetTps": 100,
  "rampDuration": "PT2M"
}
```

#### Spike Pattern

Maintains a base TPS with spikes of higher TPS at specific times:

```json
"trafficPattern": {
  "type": "spike",
  "targetTps": 50,
  "spikeTps": 200,
  "spikeStartTime": "PT5M",
  "spikeDuration": "PT30S"
}
```

#### Custom Pattern

Loads TPS values from a CSV file:

```json
"trafficPattern": {
  "type": "custom",
  "patternFile": "patterns/custom-pattern.csv"
}
```

### Request Templates

Request templates define the structure of HTTP requests with parameter placeholders:

```json
"requestTemplates": [
  {
    "name": "getResource",
    "weight": 70,
    "method": "GET",
    "urlTemplate": "${targetServiceUrl}/api/resources/${resourceId}",
    "headers": {
      "Authorization": "Bearer ${authToken}",
      "Accept": "application/json"
    }
  }
]
```

- `name`: A descriptive name for the template
- `weight`: The relative frequency of this template when selecting randomly
- `method`: The HTTP method (GET, POST, PUT, DELETE)
- `urlTemplate`: The URL with optional parameter placeholders
- `headers`: HTTP headers with optional parameter placeholders
- `bodyTemplate`: Request body template with parameter placeholders (for POST/PUT)

### Parameter Sources

Parameter sources provide values for request templates:

#### Random Parameters

Generate random integer values:

```json
"userId": {
  "type": "random",
  "range": [1000, 9999]
}
```

Generate values with normal distribution:

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

Select from a predefined set:

```json
"status": {
  "type": "random",
  "range": ["pending", "active", "completed", "cancelled"]
}
```

#### File Parameters

Read values from a text file:

```json
"authToken": {
  "type": "file",
  "path": "tokens.txt",
  "selection": "round-robin"
}
```

Read values from a CSV file column:

```json
"username": {
  "type": "file",
  "path": "users.csv",
  "column": "username",
  "selection": "random"
}
```

## Metrics and Reports

TPS Generator collects comprehensive metrics during test execution:

- **Request Counts**: Total, successful, failed, timed out, and skipped requests
- **Response Times**: Min, max, average, and percentiles (P50, P90, P95, P99)
- **TPS Rates**: Current, average, and maximum TPS
- **Status Codes**: Distribution of HTTP status codes
- **Resource Usage**: CPU, memory, thread counts
- **Network Metrics**: Bytes sent and received, request and response sizes

Test results are automatically exported to CSV files:
- `results.csv`: Main test metrics
- `tps_samples.csv`: TPS measurements over time
- `resource_snapshots.csv`: CPU and memory usage over time

Example output:

```
=== Test Summary ===
Duration: 00:05:00
Total Requests: 25000
Successful Requests: 24850
Failed Requests: 150
Success Rate: 99.40%
Average TPS: 83.33
P95 Response Time: 245 ms
Max CPU Usage: 78.20%
Max Memory Usage: 512.45 MB
==================
```

## Advanced Features

### Circuit Breaker

The circuit breaker monitors success/failure rates and prevents excessive failures:

```json
"circuitBreaker": {
  "enabled": true,
  "errorThreshold": 0.5,
  "windowSize": 100
}
```

- `enabled`: Whether the circuit breaker is active
- `errorThreshold`: Error rate threshold (0.0-1.0) that will trigger the circuit breaker
- `windowSize`: Number of recent requests to consider for error rate calculation

When the error rate exceeds the threshold, the circuit breaker "opens" and stops sending requests.

### Dashboard Integration

TPS Generator can send metrics to a dashboard service for visualization:

```json
"dashboard": {
  "enabled": true,
  "url": "http://localhost:8080",
  "apiKey": "your-api-key"
}
```

### Resource Monitoring

Enable resource monitoring to track CPU, memory, and thread usage:

```json
"resourceMonitoring": {
"enabled": true,
"sampleInterval": "PT5S"
}
```

## Sample Console Output

Below is the output for the above sample configuration file.

```
025-04-22 11:49:48 [main] INFO  i.k.t.TPSGeneratorApplication - Verbose logging enabled
2025-04-22 11:49:48 [main] INFO  i.k.t.TPSGeneratorApplication - Loaded test configuration: Mock API Load Test
2025-04-22 11:49:48 [main] INFO  i.k.t.metrics.ResourceMonitor - Initialized resource monitor
2025-04-22 11:49:48 [main] INFO  i.k.t.metrics.MetricsCollector - Initialized metrics collector
2025-04-22 11:49:48 [main] INFO  i.k.t.request.RequestGenerator - Initialized parameter source for 'resourceId'
2025-04-22 11:49:48 [main] INFO  i.k.t.request.RequestGenerator - Initialized parameter source for 'productId'
2025-04-22 11:49:48 [main] INFO  i.k.t.request.RequestGenerator - Initialized parameter source for 'quantity'
2025-04-22 11:49:48 [main] INFO  i.k.t.request.RequestGenerator - Initialized request generator with 2 templates and 3 parameter sources
2025-04-22 11:49:48 [main] INFO  i.k.tpsgenerator.core.CircuitBreaker - Initialized circuit breaker with error threshold 0.3, window size 50
2025-04-22 11:49:48 [main] INFO  i.k.t.core.ExecutionController - Initialized execution controller with traffic pattern: SpikePattern(baseTps=30.00, spikeTps=100.00, spikeStart=45000 ms, spikeDuration=15000 ms)
2025-04-22 11:49:48 [main] INFO  i.k.t.TPSGeneratorApplication - Starting test execution...
2025-04-22 11:49:48 [main] INFO  i.k.t.metrics.ResourceMonitor - Started resource monitoring with sample interval PT5S
2025-04-22 11:49:48 [main] INFO  i.k.t.metrics.MetricsCollector - Started metrics collection at 1745347788303
2025-04-22 11:49:48 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 0.0% | Target TPS: 30.00 | Actual TPS: 0.00 | Success Rate: 0.00%
2025-04-22 11:49:48 [main] INFO  i.k.t.core.ExecutionController - Test started, will run for 120 seconds
2025-04-22 11:49:58 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 8.3% | Target TPS: 30.00 | Actual TPS: 29.00 | Success Rate: 99.34%
2025-04-22 11:50:08 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 16.7% | Target TPS: 30.00 | Actual TPS: 30.00 | Success Rate: 99.67%
2025-04-22 11:50:18 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 25.0% | Target TPS: 30.00 | Actual TPS: 29.00 | Success Rate: 99.67%
2025-04-22 11:50:28 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 33.3% | Target TPS: 30.00 | Actual TPS: 29.00 | Success Rate: 99.83%
2025-04-22 11:50:38 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 41.7% | Target TPS: 100.00 | Actual TPS: 100.00 | Success Rate: 99.71%
2025-04-22 11:50:48 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 50.0% | Target TPS: 30.00 | Actual TPS: 100.00 | Success Rate: 99.71%
2025-04-22 11:50:58 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 58.3% | Target TPS: 30.00 | Actual TPS: 30.00 | Success Rate: 99.93%
2025-04-22 11:51:08 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 66.7% | Target TPS: 30.00 | Actual TPS: 30.00 | Success Rate: 99.94%
2025-04-22 11:51:18 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 75.0% | Target TPS: 30.00 | Actual TPS: 30.00 | Success Rate: 99.95%
2025-04-22 11:51:28 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 83.3% | Target TPS: 30.00 | Actual TPS: 30.00 | Success Rate: 99.97%
2025-04-22 11:51:38 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 91.7% | Target TPS: 30.00 | Actual TPS: 29.00 | Success Rate: 99.95%
2025-04-22 11:51:48 [pool-2-thread-1] INFO  i.k.t.core.ExecutionController - Progress: 100.0% | Target TPS: 30.00 | Actual TPS: 30.00 | Success Rate: 99.98%
2025-04-22 11:51:48 [main] INFO  i.k.t.core.ExecutionController - Test execution completed, waiting for pending requests to finish
2025-04-22 11:51:52 [main] INFO  i.k.t.metrics.MetricsCollector - Stopped metrics collection, test duration: 123938 ms
2025-04-22 11:51:52 [main] INFO  i.k.t.TPSGeneratorApplication - Test completed in 00:02:03
2025-04-22 11:51:52 [main] INFO  i.k.t.metrics.exporter.CSVExporter - Exporting metrics to /Users/danm/IdeaProjects/TPSGenerator/results/Mock API Load Test_20250422_114948.csv
2025-04-22 11:51:52 [main] INFO  i.k.t.metrics.exporter.CSVExporter - Metrics exported successfully
2025-04-22 11:51:52 [main] INFO  i.k.t.TPSGeneratorApplication - Results exported to results/Mock API Load Test_20250422_114948.csv

=== Test Summary ===
Duration: 00:02:03
Total Requests: 4685
Successful Requests: 4685
Failed Requests: 0
Success Rate: 100.00%
Average TPS: 37.80
P95 Response Time: 101 ms
Max CPU Usage: 20.61%
Max Memory Usage: 96.34 MB
==================

Process finished with exit code 0
```

## License

This project is licensed under the MIT License - see the LICENSE file for details.

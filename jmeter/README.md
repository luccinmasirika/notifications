# JMeter Test Suite - Irembo Notifications API

Complete performance and functional testing configuration for the Irembo Notifications API.

## 📋 Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [File Structure](#file-structure)
- [Test Plans](#test-plans)
- [Configuration](#configuration)
- [Running Tests](#running-tests)
- [Results Analysis](#results-analysis)
- [Test Scenarios](#test-scenarios)
- [Troubleshooting](#troubleshooting)

## 🎯 Overview

This JMeter test suite covers the following aspects of the Irembo Notifications API:

- **Load Testing**: Load tests with HMAC authentication
- **Rate Limiting**: Rate limit behavior validation (soft throttle and hard reject)
- **Admin API**: Functional tests for administration endpoints
- **Authentication**: HMAC-SHA256 and Basic Auth authentication tests

## 📦 Prerequisites

### Required Software

1. **Apache JMeter 5.6+**
   ```bash
   # Installation on macOS with Homebrew
   brew install jmeter
   
   # Verify installation
   jmeter --version
   ```

2. **Java 11+**
   ```bash
   # Verify installation
   java -version
   ```

3. **Irembo Notifications Application running**
   ```bash
   # Start via Docker Compose
   cd /Users/luccinmasirika/Developer/irembo/notifications
   docker-compose up -d
   
   # Check health
   curl http://localhost:1310/health
   ```

## 🚀 Installation

1. **Clone or verify JMeter directory**
   ```bash
   cd /Users/luccinmasirika/Developer/irembo/notifications/jmeter
   ```

2. **Create results folder**
   ```bash
   mkdir -p results
   ```

3. **Verify configuration files**
   ```bash
   ls -la
   # Should contain:
   # - jmeter.properties
   # - test-data.csv
   # - *.jmx (test plans)
   # - scripts/hmac-signature.groovy
   ```

## 📁 File Structure

```
jmeter/
├── README.md                          # This file
├── jmeter.properties                  # Global configuration
├── test-data.csv                      # Test data
├── scripts/
│   └── hmac-signature.groovy          # HMAC signature generation script
├── Notifications-Load-Test.jmx        # Load test plan
├── Rate-Limiting-Test.jmx             # Rate limiting test plan
├── Admin-API-Test.jmx                 # Admin API test plan
├── results/                           # Test results (created automatically)
│   ├── load-test-results.jtl
│   ├── rate-limit-test-results.jtl
│   └── admin-api-test-results.jtl
└── run-tests.sh                       # Execution script (optional)
```

## 🎨 Test Plans

### 1. Notifications-Load-Test.jmx

**Objective**: Test API performance under load with HMAC authentication.

**Features**:
- Automatic HMAC-SHA256 authentication
- CSV data usage for test variety
- Rate limiting headers extraction and validation
- Detailed performance metrics

**Default Configuration**:
- 10 virtual users
- Duration: 60 seconds
- Ramp-up: 5 seconds
- Think time: 500-2000ms

### 2. Rate-Limiting-Test.jmx

**Objective**: Validate rate limiting behavior in 3 phases.

**Test Phases**:
1. **Phase 1**: 80 requests (approach soft throttle threshold at 80%)
2. **Phase 2**: 19 requests (trigger soft throttle 81-99%)
3. **Phase 3**: 5 requests (trigger hard reject 100%+)

**Validations**:
- ✅ Phase 1: All requests accepted (202)
- ✅ Phase 2: Requests accepted with `X-Soft-Throttled: true` header
- ✅ Phase 3: Requests rejected (429) with `Retry-After` header

### 3. Admin-API-Test.jmx

**Objective**: Test administration endpoints with Basic Auth.

**Endpoints Tested**:
1. GET `/admin/status` - System status
2. GET `/admin/clients/page` - Client list (paginated)
3. GET `/admin/generate-api-key` - API key generation
4. POST `/admin/clients` - Client creation
5. GET `/admin/clients/{id}/details` - Client details
6. PUT `/admin/clients/{id}/limits` - Update limits
7. GET `/admin/system-limits` - System limits
8. DELETE `/admin/clients/{id}` - Client deletion

## ⚙️ Configuration

### jmeter.properties File

```properties
# Server configuration
server.host=localhost
server.port=1310
server.protocol=http

# Test credentials (from V4__seed_test_data.sql)
api.key=LMirg1mq_LQXNQJ5H4ZC7WH5ZMzNTKRJSKvQhG2XHfU
api.secret=W0yVOb0mHJUm8C4-M34HXHV3nNWP7UUpWwVwlO6oVfbGIvRaODN7_1RVqyUeHl8mPFvRUkNuW5Rm2YXnZGxMaA

# Admin
admin.username=admin
admin.password=admin123

# Test parameters
threads.count=10
rampup.time=5
test.duration=60
```

### Environment Variables (optional)

```bash
export SERVER_HOST=localhost
export SERVER_PORT=1310
export API_KEY=your-api-key
export API_SECRET=your-api-secret
```

## 🏃 Running Tests

### GUI Mode (development)

**1. Load Test**
```bash
cd /Users/luccinmasirika/Developer/irembo/notifications/jmeter
jmeter -t Notifications-Load-Test.jmx
```

**2. Rate Limiting Test**
```bash
jmeter -t Rate-Limiting-Test.jmx
```

**3. Admin API Test**
```bash
jmeter -t Admin-API-Test.jmx
```

### CLI Mode (production/CI)

**Complete load test**
```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -Jserver.host=localhost \
  -Jserver.port=1310 \
  -Jthreads.count=50 \
  -Jtest.duration=300 \
  -l results/load-test-$(date +%Y%m%d-%H%M%S).jtl \
  -e -o results/html-report-load
```

**Rate limiting test**
```bash
jmeter -n -t Rate-Limiting-Test.jmx \
  -l results/rate-limit-test-$(date +%Y%m%d-%H%M%S).jtl \
  -e -o results/html-report-rate-limit
```

**Admin API test**
```bash
jmeter -n -t Admin-API-Test.jmx \
  -l results/admin-api-test-$(date +%Y%m%d-%H%M%S).jtl \
  -e -o results/html-report-admin
```

### Running with Custom Parameters

```bash
# Load test with 100 users for 10 minutes
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=100 \
  -Jrampup.time=30 \
  -Jtest.duration=600 \
  -l results/heavy-load-test.jtl

# Test against remote server
jmeter -n -t Notifications-Load-Test.jmx \
  -Jserver.host=api.irembo.com \
  -Jserver.port=443 \
  -Jserver.protocol=https \
  -l results/prod-test.jtl
```

## 📊 Results Analysis

### Results Files

Results are stored in the `results/` folder in JTL (JMeter Test Log) format.

### HTML Report Generation

```bash
# Generate HTML report from existing JTL file
jmeter -g results/load-test-results.jtl -o results/html-report
```

### Key Metrics to Monitor

1. **Throughput**: Requests per second
2. **Response Time**: Response time (90th, 95th, 99th percentiles)
3. **Error Rate**: Error percentage (should be < 1%)
4. **Rate Limit Headers**: 
   - `X-RateLimit-Limit`
   - `X-RateLimit-Remaining`
   - `X-RateLimit-Reset`
   - `X-Soft-Throttled`

### Analysis with InfluxDB + Grafana (optional)

The Load Test plan includes a Backend Listener for InfluxDB (disabled by default).

To enable:
1. Start InfluxDB
2. Create database: `CREATE DATABASE jmeter`
3. Enable Backend Listener in test plan
4. Visualize in Grafana

## 🧪 Test Scenarios

### Scenario 1: Progressive Load Test

```bash
# Warm-up (5 min, 10 users)
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=10 -Jtest.duration=300 \
  -l results/warmup.jtl

# Normal load (10 min, 50 users)
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=50 -Jtest.duration=600 \
  -l results/normal-load.jtl

# Stress test (5 min, 100 users)
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=100 -Jtest.duration=300 \
  -l results/stress-test.jtl
```

### Scenario 2: Rate Limiting Validation

```bash
# 1. Check initial state
curl http://localhost:1310/health

# 2. Run rate limiting test
jmeter -n -t Rate-Limiting-Test.jmx \
  -l results/rate-limit-validation.jtl

# 3. Check metrics
curl http://localhost:1310/actuator/metrics/ratelimiter.soft_throttle
curl http://localhost:1310/actuator/metrics/ratelimiter.hard_reject

# 4. Wait for reset window (60 seconds)
sleep 60

# 5. Re-run to confirm reset
jmeter -n -t Rate-Limiting-Test.jmx \
  -l results/rate-limit-validation-2.jtl
```

### Scenario 3: Complete Admin Tests

```bash
# Complete client CRUD test
jmeter -n -t Admin-API-Test.jmx \
  -l results/admin-crud-test.jtl

# View logs
cat results/admin-crud-test.jtl | grep "201\|200\|400\|401"
```

## 🔧 Troubleshooting

### Issue: HMAC Authentication Error

**Symptom**: All requests return 401
```
Response code: 401
Response message: Unauthorized
```

**Solutions**:
1. Verify API secret is correct in `jmeter.properties`
2. Verify timestamp is within allowed window (120 seconds)
3. Verify Groovy script executes correctly

```bash
# Check HMAC script
cat scripts/hmac-signature.groovy

# Test manually with curl
cd /Users/luccinmasirika/Developer/irembo/notifications
# Use backend HMAC test script
```

### Issue: Rate Limit Reached Immediately

**Symptom**: Rate limiting test fails at phase 1
```
Expected: 202, Actual: 429
```

**Solutions**:
1. Wait for reset window (60 seconds)
2. Verify no other test is running
3. Check client limits in database

```sql
-- Check TestClient limits
SELECT * FROM client WHERE name = 'TestClient';
SELECT * FROM client_limit WHERE client_id = (SELECT id FROM client WHERE name = 'TestClient');
```

### Issue: Connection Refused

**Symptom**: `Connection refused`
```
java.net.ConnectException: Connection refused
```

**Solutions**:
1. Verify application is started
   ```bash
   docker-compose ps
   curl http://localhost:1310/health
   ```
2. Verify port in `jmeter.properties`
3. Check application logs
   ```bash
   docker-compose logs -f backend
   ```

### Issue: OutOfMemoryError

**Symptom**: JMeter crashes with memory error

**Solutions**:
1. Increase JMeter heap size
   ```bash
   export HEAP="-Xms1g -Xmx4g"
   jmeter -n -t Notifications-Load-Test.jmx ...
   ```

2. Reduce thread count or duration
3. Disable memory-intensive listeners (View Results Tree)

## 📈 Best Practices

### 1. CLI Mode Tests for Production

❌ **Avoid**: GUI mode for load tests
```bash
jmeter -t Notifications-Load-Test.jmx  # GUI mode
```

✅ **Recommended**: CLI mode for load tests
```bash
jmeter -n -t Notifications-Load-Test.jmx -l results/test.jtl
```

### 2. Disable Unnecessary Listeners

For CLI mode tests, disable visual listeners:
- View Results Tree (debug only)
- Graph Results (memory intensive)
- Aggregate Graph (prefer HTML reports)

### 3. Reuse Connections

Test plans are configured to reuse HTTP connections (`use_keepalive=true`).

### 4. Monitor System Resources

```bash
# CPU and memory
top -pid $(pgrep java)

# Network connections
netstat -an | grep 1310 | wc -l

# Real-time logs
docker-compose logs -f backend
```

### 5. Clean Old Results

```bash
# Clean files older than 7 days
find results/ -name "*.jtl" -mtime +7 -delete
find results/ -name "*.log" -mtime +7 -delete
```

## 🔗 Resources

- [Apache JMeter Documentation](https://jmeter.apache.org/usermanual/index.html)
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
- [Irembo Notifications API - Swagger](http://localhost:1310/swagger-ui.html)
- [Postman Collection](../Irembo-Notifications-API.postman_collection.json)

## 📝 Notes

### HMAC Authentication

HMAC signature format:
```
HMAC-SHA256(timestamp + "\n" + HTTP_METHOD + "\n" + PATH + "\n" + REQUEST_BODY)
```

Required headers:
- `X-API-Key`: Client API key
- `X-Timestamp`: Unix timestamp in milliseconds
- `X-Signature`: HMAC-SHA256 signature in Base64

### Default Limits

- **Client Rate Limit**: 100 requests / 60 seconds
- **Soft Throttle**: 80% (80 requests)
- **Hard Reject**: 100% (100 requests)
- **Monthly Quota**: 10,000 requests

### Test Credentials

Test credentials are defined in `backend/src/main/resources/db/migration/V4__seed_test_data.sql`:
- **Client**: TestClient
- **API Key**: `LMirg1mq_LQXNQJ5H4ZC7WH5ZMzNTKRJSKvQhG2XHfU`
- **API Secret**: `W0yVOb0mHJUm8C4-M34HXHV3nNWP7UUpWwVwlO6oVfbGIvRaODN7_1RVqyUeHl8mPFvRUkNuW5Rm2YXnZGxMaA`

## 🤝 Contribution



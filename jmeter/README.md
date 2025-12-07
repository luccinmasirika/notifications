# JMeter Load Testing - Irembo Notifications API

> **Comprehensive load testing suite for the Irembo Notifications API with HMAC authentication and rate limiting validation**

[![JMeter](https://img.shields.io/badge/JMeter-5.6%2B-red.svg)](https://jmeter.apache.org/)
[![Status](https://img.shields.io/badge/Status-Production%20Ready-brightgreen.svg)]()
[![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-success.svg)]()

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Quick Start](#-quick-start)
- [Configuration](#-configuration)
- [Test Plans](#-test-plans)
- [Running Tests](#-running-tests)
- [Understanding Results](#-understanding-results)
- [Test Scenarios](#-test-scenarios)
- [Troubleshooting](#-troubleshooting)
- [Advanced Usage](#-advanced-usage)

---

## 🎯 Overview

This JMeter test suite provides comprehensive load testing and validation for the Irembo Notifications API, including:

✅ **HMAC-SHA256 Authentication** - Automatic signature generation  
✅ **Rate Limiting Validation** - Soft throttle and hard reject testing  
✅ **Load Testing** - Configurable concurrent users and duration  
✅ **Admin API Testing** - CRUD operations with Basic Auth  
✅ **HTML Reports** - Professional test reports with graphs  
✅ **CI/CD Ready** - Command-line execution support  

### Key Features

- **Pre-configured Test Plans**: 3 ready-to-use `.jmx` files
- **Valid Credentials**: Working API keys already configured
- **Flexible Configuration**: Override settings via CLI parameters
- **Realistic Test Data**: CSV-based test data with 20 scenarios
- **Automated Signature**: HMAC-SHA256 signature generation in Groovy
- **Performance Metrics**: Response times, throughput, error rates

---

## 🚀 Quick Start

### Prerequisites

```bash
# Verify JMeter is installed
jmeter --version
# Expected: Apache JMeter 5.6 or higher

# Verify the backend is running
curl http://localhost:1310/actuator/health
# Expected: {"status":"UP"}
```

### Run Your First Test

```bash
# 1. Navigate to jmeter directory
cd /path/to/notifications/jmeter

# 2. Run a quick 30-second load test
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=10 \
  -Jtest.duration=30 \
  -l results/quick-test.jtl \
  -e -o results/quick-report

# 3. View results
open results/quick-report/index.html
```

**Expected Results**: 
- ✅ 100% success rate (all requests return 202 Accepted)
- ✅ 0% authentication errors (HMAC working correctly)
- ⚠️ Some 429 responses (rate limiting working as expected)

---

## ⚙️ Configuration

### Default Settings

The tests are pre-configured with the following defaults:

| Parameter | Default Value | Description |
|-----------|---------------|-------------|
| **Server** | | |
| `server.host` | `localhost` | API server hostname |
| `server.port` | `1310` | API server port |
| `server.protocol` | `http` | HTTP or HTTPS |
| **Load Test** | | |
| `threads.count` | `10` | Number of concurrent virtual users |
| `rampup.time` | `5` | Time to reach full load (seconds) |
| `test.duration` | `60` | Test duration (seconds) |
| **Think Time** | | |
| `think.time.min` | `500` | Minimum pause between requests (ms) |
| `think.time.max` | `2000` | Maximum pause between requests (ms) |

### 🔑 Valid Test Credentials

The test plans are pre-configured with **valid credentials**:

```bash
# Client: JMeterTestClient (ID: 4)
API_KEY="LM-qkVRYb8UNVTcZcQc_i1Y0oxL2dALqNMt6qL8wCc8e7o"
API_SECRET="ZGqBk_5Qpidky55aa9iPo9hf9aIxc-YVM8tZOBEsNJnHvjDFJLNYwY5qEHUhsSt1zceBHjiZtyV9-rUoCLXXaQ"

# Admin credentials
ADMIN_USERNAME="admin"
ADMIN_PASSWORD="admin123"
```

⚠️ **Important**: These credentials are already configured in the `.jmx` files. No manual changes needed!

### Rate Limits (JMeterTestClient)

| Limit Type | Value | Description |
|------------|-------|-------------|
| **Window Size** | 60 seconds | Fixed time window |
| **Max Requests/Window** | 300 | Requests allowed per window |
| **Monthly Quota** | 50,000 | Total requests per month |
| **Soft Throttle** | 80% (240 req) | Artificial delay applied |
| **Hard Reject** | 100% (300 req) | Requests rejected with 429 |

---

## 📊 Test Plans

### 1. Notifications-Load-Test.jmx

**Purpose**: Load testing the notification API endpoint with HMAC authentication

**What it tests**:
- HMAC-SHA256 signature generation
- POST /api/notifications endpoint
- Response time under load
- Error rate tracking
- Rate limit headers validation

**Key Assertions**:
- ✅ HTTP 202 Accepted response code
- ✅ JSON response contains `status: "accepted"`
- ✅ X-RateLimit headers present

**Default Configuration**:
- 10 concurrent users
- 60-second duration
- Random think time (500-2000ms)
- CSV test data (rotating through 20 scenarios)

### 2. Rate-Limiting-Test.jmx

**Purpose**: Validate rate limiting behavior (soft throttle and hard reject)

**What it tests**:
- Normal operation (0-79% usage)
- Soft throttling (80-99% usage)
- Hard rejection (100%+ usage)
- Window reset behavior
- Retry-After header validation

**Test Phases**:
1. **Phase 1**: Send 240 requests (80% - soft throttle)
2. **Phase 2**: Send 60 more requests (100% - hard reject)
3. **Phase 3**: Wait for window reset (60 seconds)
4. **Phase 4**: Verify requests accepted again

**Expected Behavior**:
- Requests 1-240: HTTP 202 (some with `X-Soft-Throttled: true`)
- Requests 241-300: HTTP 202
- Requests 301+: HTTP 429 Too Many Requests

### 3. Admin-API-Test.jmx

**Purpose**: Functional testing of Admin API endpoints with Basic Auth

**What it tests**:
- GET /admin/clients (list clients)
- POST /admin/clients (create client)
- GET /admin/clients/{id}/details (client details)
- PUT /admin/clients/{id}/limits (update limits)
- GET /admin/system-limits (system limits)

**Authentication**: Basic Auth (admin:admin123)

**Assertions**:
- ✅ HTTP 200/201 response codes
- ✅ Valid JSON responses
- ✅ Expected fields present

---

## 🏃 Running Tests

### Basic Usage

#### 1. Standard Load Test (10 users, 60 seconds)

```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -l results/load-test.jtl \
  -e -o results/load-test-report
```

#### 2. Custom Load Test (50 users, 5 minutes)

```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=50 \
  -Jtest.duration=300 \
  -l results/stress-test.jtl \
  -e -o results/stress-test-report
```

#### 3. Rate Limiting Test

```bash
jmeter -n -t Rate-Limiting-Test.jmx \
  -l results/rate-limit.jtl \
  -e -o results/rate-limit-report
```

#### 4. Admin API Test

```bash
jmeter -n -t Admin-API-Test.jmx \
  -l results/admin-api.jtl \
  -e -o results/admin-api-report
```

### Command-Line Parameters

Override any property using `-J` flag:

```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=20 \          # 20 concurrent users
  -Jrampup.time=10 \             # 10 seconds ramp-up
  -Jtest.duration=120 \          # 2 minutes test
  -Jthink.time.min=1000 \        # 1 second min think time
  -Jthink.time.max=3000 \        # 3 seconds max think time
  -Jserver.host=production.com \ # Different host
  -l results/custom.jtl \
  -e -o results/custom-report
```

### GUI Mode (Development)

Open test plans in GUI for editing and debugging:

```bash
# Open in JMeter GUI
jmeter -t Notifications-Load-Test.jmx
```

⚠️ **Warning**: Never run load tests in GUI mode - it consumes too much memory. Use GUI only for test development.

---

## 📈 Understanding Results

### HTML Report Structure

After running a test with `-e -o results/report-name`, you'll get:

```
results/report-name/
├── index.html          # Main dashboard
├── content/
│   ├── pages/
│   │   ├── Graph-xxx.html
│   │   └── Table-xxx.html
│   └── js/
└── sbadmin2-1.0.7/
```

### Key Metrics

#### 1. Summary Report (index.html)

| Metric | Description | Good | Warning | Critical |
|--------|-------------|------|---------|----------|
| **Avg Response Time** | Average response time | < 200ms | 200-500ms | > 500ms |
| **95th Percentile** | 95% of requests faster than | < 500ms | 500-1000ms | > 1000ms |
| **Throughput** | Requests per second | Stable | Decreasing | Dropping |
| **Error Rate** | % of failed requests | 0% | < 5% | > 5% |

#### 2. Rate Limiting Metrics

**Normal Operation (0-79% usage)**:
```
HTTP 202 Accepted
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 150
X-RateLimit-Reset: 1733612400
X-Soft-Throttled: false
```

**Soft Throttled (80-99% usage)**:
```
HTTP 202 Accepted (with 50-200ms artificial delay)
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 30
X-RateLimit-Reset: 1733612400
X-Soft-Throttled: true
```

**Hard Rejected (100%+ usage)**:
```
HTTP 429 Too Many Requests
X-RateLimit-Limit: 300
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1733612400
Retry-After: 45
```

### Analyzing Results

#### Good Test Results

```bash
# Check summary
tail -1 jmeter.log

# Expected output:
# summary =  1000 in 00:01:00 = 16.7/s Avg: 45ms Min: 10ms Max: 150ms Err: 0 (0.00%)
```

✅ **Success Indicators**:
- Error rate: 0% (excluding expected 429s)
- Avg response time: < 200ms
- 95th percentile: < 500ms
- Throughput: Consistent throughout test

#### Problem Indicators

❌ **Authentication Issues**:
- High % of 401 Unauthorized responses
- Check API key/secret are valid
- See [Troubleshooting](#-troubleshooting)

❌ **Performance Issues**:
- Response time increasing over time
- Check backend logs: `docker compose logs -f backend-1`
- Monitor system resources

❌ **Rate Limiting Issues**:
- Unexpected 429 responses
- Check client limits: `curl http://localhost:1310/admin/clients/4/details -u admin:admin123`

---

## 🎬 Test Scenarios

### Scenario 1: Quick Health Check (1 minute)

**Goal**: Verify API is responding correctly

```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=3 \
  -Jtest.duration=60 \
  -l results/health-check.jtl
```

**Expected**: 100% success, < 100ms avg response time

---

### Scenario 2: Standard Load Test (10 users, 5 minutes)

**Goal**: Baseline performance under normal load

```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=10 \
  -Jtest.duration=300 \
  -l results/baseline.jtl \
  -e -o results/baseline-report
```

**Expected**: 
- Throughput: ~5-10 req/s
- Avg response: < 100ms
- Some soft throttling near window boundaries

---

### Scenario 3: Stress Test (50 users, 10 minutes)

**Goal**: Test system behavior under heavy load

```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=50 \
  -Jrampup.time=30 \
  -Jtest.duration=600 \
  -l results/stress.jtl \
  -e -o results/stress-report
```

**Expected**:
- High % of 429 responses (rate limiting working)
- Soft throttling active
- Response times stable (no degradation)

---

### Scenario 4: Rate Limiting Validation

**Goal**: Verify soft throttle and hard reject behavior

```bash
# Test rate limiting
jmeter -n -t Rate-Limiting-Test.jmx \
  -l results/rate-limit.jtl \
  -e -o results/rate-limit-report

# Wait for window reset
echo "Waiting 60 seconds for window reset..."
sleep 60

# Verify requests accepted again
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=5 \
  -Jtest.duration=30 \
  -l results/after-reset.jtl
```

**Expected**:
- First test: High % of 429s
- After reset: 100% success

---

### Scenario 5: Soak Test (10 users, 2 hours)

**Goal**: Test system stability over extended period

```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=10 \
  -Jtest.duration=7200 \
  -l results/soak-test.jtl \
  -e -o results/soak-test-report
```

**Expected**:
- Consistent performance throughout
- No memory leaks
- No connection pool exhaustion

---

## 🔧 Troubleshooting

### Problem: 100% Authentication Errors (401)

**Symptom**: All requests fail with HTTP 401 Unauthorized

**Diagnosis**:
```bash
# Check test results
grep "401" results/load-test.jtl | wc -l

# Verify credentials exist in database
curl -u admin:admin123 http://localhost:1310/admin/clients | grep -i jmeter
```

**Solution**:

1. **Verify backend is running**:
   ```bash
   curl http://localhost:1310/actuator/health
   ```

2. **Check if JMeterTestClient exists**:
   ```bash
   curl -u admin:admin123 \
     http://localhost:1310/admin/clients/4/details | jq
   ```

3. **If client doesn't exist, create it**:
   ```bash
   curl -X POST http://localhost:1310/admin/clients \
     -u admin:admin123 \
     -H "Content-Type: application/json" \
     -d '{
       "name": "JMeterTestClient",
       "email": "jmeter@test.com",
       "description": "Client for JMeter load testing"
     }'
   ```

4. **Update credentials in test files**:
   - Open `Notifications-Load-Test.jmx` and `Rate-Limiting-Test.jmx`
   - Find `<elementProp name="API_KEY">`
   - Replace with new API key and secret from creation response

📖 **Detailed guide**: See [CORRECTIONS.md](./CORRECTIONS.md)

---

### Problem: High Rate of 429 Errors

**Symptom**: Many requests fail with HTTP 429 Too Many Requests

**Diagnosis**:
```bash
# Check current usage
docker exec notifications-redis redis-cli KEYS "rate:client:4:*"

# Check rate limit
curl -u admin:admin123 \
  http://localhost:1310/admin/clients/4/details | \
  jq '.limit.maxRequestsPerWindow'
```

**Solutions**:

1. **Increase rate limits** (for testing):
   ```bash
   curl -X PUT http://localhost:1310/admin/clients/4/limits \
     -u admin:admin123 \
     -H "Content-Type: application/json" \
     -d '{
       "windowSizeSeconds": 60,
       "maxRequestsPerWindow": 500,
       "monthlyQuota": 100000,
       "softThrottleThreshold": 0.80,
       "hardRejectThreshold": 1.00
     }'
   ```

2. **Wait for window reset** (60 seconds):
   ```bash
   sleep 60
   ```

3. **Reduce load** (fewer users or longer think time):
   ```bash
   jmeter -n -t Notifications-Load-Test.jmx \
     -Jthreads.count=5 \
     -Jthink.time.min=2000 \
     -Jthink.time.max=5000 \
     -l results/reduced-load.jtl
   ```

---

### Problem: Connection Refused

**Symptom**: Cannot connect to http://localhost:1310

**Solutions**:

```bash
# 1. Check if backend is running
docker compose ps

# 2. If not running, start it
cd /path/to/notifications
docker compose up -d

# 3. Wait for startup (check logs)
docker compose logs -f backend-1

# 4. Verify health
curl http://localhost:1310/actuator/health
```

---

### Problem: JMeter Out of Memory

**Symptom**: JMeter crashes with OutOfMemoryError

**Solutions**:

1. **Increase heap size**:
   ```bash
   export HEAP="-Xms1g -Xmx4g -XX:MaxMetaspaceSize=256m"
   jmeter -n -t Notifications-Load-Test.jmx ...
   ```

2. **Reduce thread count**:
   ```bash
   jmeter -n -t Notifications-Load-Test.jmx \
     -Jthreads.count=10 \  # Instead of 100
     -l results/test.jtl
   ```

3. **Don't use GUI mode for load tests**:
   ```bash
   # ❌ Wrong (GUI mode)
   jmeter -t test.jmx

   # ✅ Correct (Non-GUI mode)
   jmeter -n -t test.jmx -l results.jtl
   ```

---

### Problem: Incorrect Assertions Failing

**Symptom**: Tests fail with "No results for path: $.notificationId"

**Solution**: This is already fixed! The assertion now correctly checks for `$.status` instead of `$.notificationId`.

If you see this error, you may be using an old version of the test file. Get the latest version.

---

## 🚀 Advanced Usage

### Distributed Testing

Run tests across multiple JMeter instances for massive load:

**Server machine**:
```bash
jmeter-server -Dserver.rmi.localport=4000
```

**Client machine**:
```bash
jmeter -n -t Notifications-Load-Test.jmx \
  -R server1.local,server2.local \
  -l results/distributed.jtl
```

### CI/CD Integration

#### GitHub Actions Example

```yaml
name: Load Tests

on:
  schedule:
    - cron: '0 2 * * *'  # Daily at 2 AM

jobs:
  load-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Setup JMeter
        run: |
          wget https://archive.apache.org/dist/jmeter/binaries/apache-jmeter-5.6.3.tgz
          tar -xzf apache-jmeter-5.6.3.tgz
          
      - name: Run Load Test
        run: |
          cd jmeter
          ../apache-jmeter-5.6.3/bin/jmeter -n \
            -t Notifications-Load-Test.jmx \
            -Jthreads.count=20 \
            -Jtest.duration=180 \
            -l results/ci-test.jtl \
            -e -o results/ci-report
            
      - name: Upload Results
        uses: actions/upload-artifact@v3
        with:
          name: load-test-results
          path: jmeter/results/
```

### Performance Baselines

Create baseline performance metrics:

```bash
# Run 5 baseline tests
for i in {1..5}; do
  jmeter -n -t Notifications-Load-Test.jmx \
    -Jthreads.count=10 \
    -Jtest.duration=300 \
    -l results/baseline-$i.jtl
done

# Calculate average metrics
awk -F',' 'NR>1 {sum+=$2; count++} END {print sum/count}' \
  results/baseline-*.jtl
```

### Custom HMAC Signature Script

The HMAC signature generation is in `scripts/hmac-signature.groovy`:

```groovy
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets

def apiSecret = vars.get("API_SECRET")
def timestamp = System.currentTimeMillis()
def httpMethod = "POST"
def requestPath = "/api/notifications"
def requestBody = vars.get("request_body")

// Build payload
def payload = timestamp + "\n" + httpMethod.toUpperCase() + "\n" + requestPath + "\n" + requestBody

// Generate signature
def algorithm = "HmacSHA256"
def mac = Mac.getInstance(algorithm)
def secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), algorithm)
mac.init(secretKeySpec)

def signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))
def signature = Base64.getEncoder().encodeToString(signatureBytes)

vars.put("signature", signature)
vars.put("timestamp", timestamp.toString())
```

---

## 📚 Additional Resources

### Documentation

- **Main README**: [../README.md](../README.md) - Full project documentation
- **Architecture**: [../ARCHITECTURE.md](../ARCHITECTURE.md) - System design
- **Corrections Guide**: [CORRECTIONS.md](./CORRECTIONS.md) - Troubleshooting authentication issues
- **Quick Summary**: [JMETER_FIX_SUMMARY.md](./JMETER_FIX_SUMMARY.md) - Fix summary

### API Documentation

- **Swagger UI**: http://localhost:1310/swagger-ui.html
- **OpenAPI Spec**: http://localhost:1310/v3/api-docs

### JMeter Resources

- [JMeter User Manual](https://jmeter.apache.org/usermanual/index.html)
- [JMeter Best Practices](https://jmeter.apache.org/usermanual/best-practices.html)
- [JMeter Functions Reference](https://jmeter.apache.org/usermanual/functions.html)

---

## 📋 Files Overview

```
jmeter/
├── README.md                      # This file
├── CORRECTIONS.md                 # Authentication troubleshooting guide
├── JMETER_FIX_SUMMARY.md         # Quick fix summary
├── SUMMARY.md                     # Configuration summary
├── INDEX.md                       # Test plans index
│
├── Notifications-Load-Test.jmx   # Main load test (HMAC auth)
├── Rate-Limiting-Test.jmx        # Rate limiting validation
├── Admin-API-Test.jmx            # Admin API functional tests
│
├── jmeter.properties              # Default configuration
├── test-data.csv                  # Test data (20 scenarios)
│
├── scripts/
│   └── hmac-signature.groovy     # HMAC signature generation
│
├── run-tests.sh                   # Automated test runner
│
└── results/                       # Test results (gitignored)
    └── .gitkeep
```

---


### Creating Test Clients

If you need to create additional test clients:

```bash
curl -X POST http://localhost:1310/admin/clients \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{
    "name": "MyTestClient",
    "email": "mytest@example.com",
    "description": "My custom test client"
  }' | jq
```

Save the returned `apiKey` and `apiSecret` - they cannot be retrieved later!

---

## ✅ Validation Checklist

Before running load tests, verify:

- [ ] JMeter 5.6+ installed
- [ ] Backend running (`curl http://localhost:1310/actuator/health`)
- [ ] Valid test client exists (ID: 4)
- [ ] Credentials match in `.jmx` files
- [ ] Rate limits configured appropriately
- [ ] Results directory exists
- [ ] Sufficient disk space for results

---

## 📊 Current Configuration Summary

**Test Client**: JMeterTestClient (ID: 4)

| Setting | Value |
|---------|-------|
| **API Key** | `LM-qkVRYb8UNVTcZcQc_i1Y0oxL2dALqNMt6qL8wCc8e7o` |
| **Window Limit** | 300 requests / 60 seconds |
| **Monthly Quota** | 50,000 requests |
| **Soft Throttle** | 240 requests (80%) |
| **Hard Reject** | 300 requests (100%) |
| **Status** | ✅ ACTIVE |

**Default Test Parameters**:

| Parameter | Value | Override Example |
|-----------|-------|------------------|
| Virtual Users | 10 | `-Jthreads.count=50` |
| Ramp-up Time | 5s | `-Jrampup.time=30` |
| Test Duration | 60s | `-Jtest.duration=300` |
| Think Time (min) | 500ms | `-Jthink.time.min=1000` |
| Think Time (max) | 2000ms | `-Jthink.time.max=5000` |

---

## 🎯 Success Criteria

A successful load test should show:

✅ **Functionality**:
- 100% authentication success (no 401 errors)
- Valid HMAC signatures generated
- Correct JSON responses received

✅ **Performance**:
- Average response time < 200ms
- 95th percentile < 500ms
- Throughput consistent throughout test

✅ **Rate Limiting**:
- 429 responses only when limits exceeded
- `X-RateLimit-*` headers present
- Soft throttling delays applied at 80%+
- Window resets after 60 seconds

✅ **Stability**:
- No connection errors
- No backend crashes
- Consistent resource usage


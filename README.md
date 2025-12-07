# Irembo Notifications API - Rate Limiting System

> **A production-ready distributed notification service with intelligent rate limiting for multi-tenant environments**

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Angular](https://img.shields.io/badge/Angular-21.0-red.svg)](https://angular.io/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-14-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-blue.svg)](https://docs.docker.com/compose/)

---

## 📋 Table of Contents

- [Overview](#-overview)
- [Architecture Summary](#-architecture-summary)
- [Tech Stack](#-tech-stack)
- [Prerequisites](#-prerequisites)
- [Quick Start](#-quick-start)
- [How to Run the Backend](#-how-to-run-the-backend)
- [How to Run the Frontend](#-how-to-run-the-frontend)
- [How to Test the Solution](#-how-to-test-the-solution)
- [API Documentation](#-api-documentation)
- [Rate Limiting Behavior](#-rate-limiting-behavior)
- [Deployment](#-deployment)
- [Monitoring & Observability](#-monitoring--observability)
- [Troubleshooting](#-troubleshooting)
- [Future Improvements](#-future-improvements)
- [Contributing](#-contributing)

---

## 🎯 Overview

### Business Context

Corporation X,Y,Z operates a **multi-tenant notification service** (SMS/Email) serving multiple clients. This system addresses critical performance and fairness challenges through intelligent distributed rate limiting.

### What Problems It Solves

✅ **Per-Client Rate Limits** - Enforce sliding/fixed window limits (e.g., 100 requests per minute)  
✅ **Monthly Quotas** - Track and enforce monthly usage caps per client (e.g., 10,000 requests/month)  
✅ **Global System Protection** - Prevent system overload with global rate limits (e.g., 1M requests/hour)  
✅ **Distributed Consistency** - Works correctly across multiple backend instances using Redis atomic operations  
✅ **Soft Throttling** - Gradual degradation (artificial delays) before hard rejection for better UX  
✅ **Hard Rejection** - Strict enforcement with HTTP 429 when limits exceeded  
✅ **Fair Resource Allocation** - Ensures equitable distribution across client tiers  

### Key Features

- 🔐 **HMAC-SHA256 Authentication** - Secure request signing with replay protection
- ⚡ **High Performance** - Sub-100ms response times with Redis in-memory counters
- 📊 **Real-Time Monitoring** - Prometheus metrics + Grafana dashboards
- 🔄 **Asynchronous Processing** - RabbitMQ message queue for notification delivery
- 🧪 **Comprehensive Testing** - Unit, integration, and load tests included
- 📈 **Horizontal Scalability** - Add backend instances without code changes
- 🎯 **Admin Dashboard** - Angular UI for client management and limit configuration

---

## 🏗 Architecture Summary

### System Overview

```
┌─────────────────┐
│   Client Apps   │ (SMS/Email Providers)
└────────┬────────┘
         │ HTTP + HMAC Auth
         ▼
┌─────────────────────────────────────────────────────┐
│              Traefik Load Balancer                  │
│    (Circuit Breaker, Retry Logic, Health Checks)   │
└──┬──────────────────────────────────────────────┬───┘
   │                                              │
   ▼                                              ▼
┌─────────────────┐                    ┌─────────────────┐
│  Backend 1      │◄──────────────────►│  Backend 2      │
│  Spring Boot    │    Stateless       │  Spring Boot    │
└────────┬────────┘                    └────────┬────────┘
         │                                      │
         └──────────────┬───────────────────────┘
                        │
         ┌──────────────┼──────────────────┐
         ▼              ▼                  ▼
   ┌─────────┐    ┌─────────┐       ┌──────────┐
   │  Redis  │    │PostgreSQL│       │ RabbitMQ │
   │ (Rate   │    │ (Config  │       │ (Async   │
   │ Limits) │    │ Storage) │       │ Queue)   │
   └─────────┘    └─────────┘       └──────────┘
```

### Key Components

| Component | Purpose | Technology |
|-----------|---------|------------|
| **Frontend** | Admin dashboard for client/limit management | Angular 21 + Material Design |
| **Backend** | REST API with rate limiting enforcement | Spring Boot 3.4.1 (Java 21) |
| **Traefik** | Load balancer, API gateway, circuit breaker | Traefik v2.10 |
| **Redis** | Rate limit counters (atomic operations) | Redis 7 (in-memory) |
| **PostgreSQL** | Client configs, notifications, audit logs | PostgreSQL 14 |
| **RabbitMQ** | Async notification queue | RabbitMQ 3 |
| **Prometheus** | Metrics collection | Prometheus v2.48 |
| **Grafana** | Dashboards and visualization | Grafana v10.2 |
| **Loki** | Log aggregation | Loki v2.9 |

📖 **Detailed Architecture**: See [ARCHITECTURE.md](./ARCHITECTURE.md) for comprehensive design documentation with diagrams and sequence flows.

---

## 🛠 Tech Stack

### Backend

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 21 | Runtime environment |
| **Spring Boot** | 3.4.1 | Framework |
| **Spring Data JPA** | 3.4.1 | Database access |
| **Spring Data Redis** | 3.4.1 | Redis integration |
| **Spring AMQP** | 3.4.1 | RabbitMQ messaging |
| **PostgreSQL Driver** | Latest | Database driver |
| **Flyway** | 10.x | Database migrations |
| **Caffeine** | 3.x | Local caching |
| **Micrometer** | 1.13.x | Metrics instrumentation |
| **Springdoc OpenAPI** | 2.7.0 | API documentation (Swagger) |
| **Testcontainers** | 1.20.4 | Integration testing |
| **JUnit 5** | 5.10.x | Unit testing |
| **Maven** | 3.9+ | Build tool |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| **Angular** | 21.0.0 | Framework |
| **Angular Material** | 21.0.0 | UI components |
| **TypeScript** | 5.6.0 | Language |
| **RxJS** | 7.8.0 | Reactive programming |
| **Vitest** | 2.0.0 | Testing framework |
| **Node.js** | 20+ | Runtime (dev) |
| **npm** | 10+ | Package manager |

### Infrastructure

| Tool | Version | Purpose |
|------|---------|---------|
| **Docker** | 24+ | Containerization |
| **Docker Compose** | 2.23+ | Multi-container orchestration |
| **Traefik** | 2.10 | Load balancer & reverse proxy |
| **Redis** | 7-alpine | In-memory data store |
| **PostgreSQL** | 14-alpine | Relational database |
| **RabbitMQ** | 3-management | Message broker |
| **Prometheus** | 2.48.0 | Metrics server |
| **Grafana** | 10.2.0 | Metrics visualization |
| **Loki** | 2.9.2 | Log aggregation |

### Testing Tools

- **Apache JMeter** 5.6+ - Load and performance testing
- **JaCoCo** 0.8.12 - Code coverage reporting
- **Testcontainers** - Docker-based integration tests

---

## ✅ Prerequisites

Before running this project, ensure you have the following installed:

### Required Software

| Software | Minimum Version | Check Command | Installation |
|----------|----------------|---------------|--------------|
| **Java** | 21 | `java -version` | [Download JDK 21](https://adoptium.net/) |
| **Maven** | 3.9+ | `mvn -version` | [Install Maven](https://maven.apache.org/install.html) |
| **Node.js** | 20+ | `node --version` | [Download Node.js](https://nodejs.org/) |
| **npm** | 10+ | `npm --version` | (Included with Node.js) |
| **Docker** | 24+ | `docker --version` | [Install Docker](https://docs.docker.com/get-docker/) |
| **Docker Compose** | 2.23+ | `docker compose version` | [Install Compose](https://docs.docker.com/compose/install/) |
| **Angular CLI** | 21+ | `ng version` | `npm install -g @angular/cli` |

### Optional (for load testing)

| Software | Version | Installation |
|----------|---------|--------------|
| **Apache JMeter** | 5.6+ | `brew install jmeter` (macOS) |

### System Requirements

- **RAM**: 8GB minimum, 16GB recommended
- **Disk Space**: 5GB free space
- **OS**: Linux, macOS, or Windows (with WSL2)

### Verify Prerequisites

```bash
# Check all versions at once
java -version && mvn -version && node --version && npm --version && docker --version && docker compose version && ng version
```

Expected output should show all tools installed with the correct versions.

---

## 🚀 Quick Start

Get the entire system running in under 5 minutes:

### 1. Clone the Repository

```bash
git clone <repository-url>
cd notifications
```

### 2. Start All Services with Docker Compose

```bash
# Using Make (recommended)
make up

# OR using Docker Compose directly
docker compose up --build
```

This command will:
- ✅ Build the backend (Spring Boot)
- ✅ Build the frontend (Angular)
- ✅ Start PostgreSQL, Redis, RabbitMQ
- ✅ Initialize the database with migrations
- ✅ Start 2 backend instances (load balanced)
- ✅ Start monitoring stack (Prometheus, Grafana, Loki)
- ✅ Start Traefik load balancer

### 3. Verify Services are Running

```bash
# Check all containers
docker compose ps

# Expected output (all services healthy):
# notifications-postgres      Up (healthy)
# notifications-redis         Up (healthy)
# notifications-rabbitmq      Up (healthy)
# notifications-backend-1     Up (healthy)
# notifications-backend-2     Up (healthy)
# notifications-frontend      Up (healthy)
# notifications-traefik       Up
# notifications-prometheus    Up
# notifications-grafana       Up
# notifications-loki          Up
# notifications-promtail      Up
```

### 4. Access the Applications

| Service | URL | Credentials |
|---------|-----|-------------|
| **Frontend (Admin UI)** | http://localhost:1015 | `admin` / `admin123` |
| **Backend API** | http://localhost:1310 | N/A (use HMAC auth) |
| **Swagger UI** | http://localhost:1310/swagger-ui.html | N/A |
| **Traefik Dashboard** | http://localhost:8080 | N/A |
| **Grafana** | http://localhost:3000 | `admin` / `admin123` |
| **Prometheus** | http://localhost:9090 | N/A |
| **RabbitMQ Management** | http://localhost:15672 | `guest` / `guest` |

### 5. Test the API

```bash
# Health check
curl http://localhost:1310/actuator/health

# Expected response:
# {"status":"UP"}
```

🎉 **Congratulations!** The system is now running. Proceed to [API Documentation](#-api-documentation) to start making requests.

---

## 🔧 How to Run the Backend

### Option 1: Docker Compose (Recommended for Full Stack)

```bash
# Start all services (PostgreSQL, Redis, RabbitMQ, Backend, Frontend, Monitoring)
docker compose up --build

# Or run in detached mode
docker compose up --build -d

# View logs
docker compose logs -f backend-1 backend-2

# Stop services
docker compose down

# Stop and remove volumes (clean slate)
docker compose down -v
```

### Option 2: Local Development (Backend Only)

For active development on the backend without Docker:

#### Step 1: Start Infrastructure Services

```bash
# Start only PostgreSQL, Redis, and RabbitMQ
docker compose up postgres redis rabbitmq -d

# Verify services are running
docker compose ps
```

#### Step 2: Set Environment Variables

```bash
# Copy environment template (if it exists)
cp .env.example .env

# Or set environment variables manually
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/notifications
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=6379
export SPRING_RABBITMQ_HOST=localhost
export SPRING_RABBITMQ_PORT=5672
export SPRING_RABBITMQ_USERNAME=guest
export SPRING_RABBITMQ_PASSWORD=guest
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD=admin123
export CORS_ALLOWED_ORIGINS=http://localhost:4200,http://localhost:1015
export APP_CRYPTO_MASTER_KEY=uo0mWscOs31YO4l7nxkswP6bvKghQr01jTyL+VrwwjA=
```

#### Step 3: Run with Maven

```bash
cd backend

# Run the application
./mvnw spring-boot:run

# OR build and run the JAR
./mvnw clean package
java -jar target/notifications-1.0.0-SNAPSHOT.jar
```

#### Step 4: Verify Backend Startup

You should see console output indicating successful startup:

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.4.1)

...

Started NotificationsApplication in 5.234 seconds
```

The API is now accessible at **http://localhost:1310**

### Configuration Reference

Key configuration properties (see `backend/src/main/resources/application.yml`):

| Property | Default | Description |
|----------|---------|-------------|
| `server.port` | 1310 | Backend HTTP port |
| `spring.datasource.url` | jdbc:postgresql://postgres:5432/notifications | PostgreSQL connection |
| `spring.data.redis.host` | redis | Redis hostname |
| `spring.rabbitmq.host` | rabbitmq | RabbitMQ hostname |
| `app.rate-limiter.thresholds.soft-throttle` | 0.80 | Soft throttle at 80% usage |
| `app.rate-limiter.thresholds.hard-reject` | 1.00 | Hard reject at 100% usage |
| `app.rate-limiter.jitter.min-ms` | 50 | Min delay for soft throttle |
| `app.rate-limiter.jitter.max-ms` | 200 | Max delay for soft throttle |
| `app.cache.client-configs-ttl` | 300 | Client config cache TTL (seconds) |

### Example API Request

```bash
# Test client credentials (from seed data)
API_KEY="LMirg1mq_LQXNQJ5H4ZC7WH5ZMzNTKRJSKvQhG2XHfU"
API_SECRET="W0yVOb0mHJUm8C4-M34HXHV3nNWP7UUpWwVwlO6oVfbGIvRaODN7_1RVqyUeHl8mPFvRUkNuW5Rm2YXnZGxMaA"

# Note: For production, use proper HMAC signature generation
# See jmeter/scripts/hmac-signature.groovy for implementation example

curl -X POST http://localhost:1310/api/notifications \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: ${API_KEY}" \
  -H "X-TIMESTAMP: $(date +%s)000" \
  -H "X-SIGNATURE: <calculated-hmac-signature>" \
  -d '{
    "channel": "SMS",
    "recipient": "+250788123456",
    "message": "Hello from Irembo Notifications",
    "priority": "MEDIUM"
  }'
```

### Expected Console Logs

On successful startup, you should see:

```
✅ Database migrations applied successfully
✅ Redis connection established
✅ RabbitMQ connection established
✅ Rate limiter initialized
✅ Actuator endpoints available at /actuator
✅ Swagger UI available at /swagger-ui.html
✅ Server started on port 1310
```

---

## 🎨 How to Run the Frontend

### Option 1: Docker Compose (Recommended for Full Stack)

The frontend is automatically started with Docker Compose:

```bash
docker compose up --build
```

Access the UI at **http://localhost:1015**

### Option 2: Local Development (Frontend Only)

For active development on the frontend:

#### Step 1: Install Dependencies

```bash
cd frontend

# Install npm packages
npm install
```

#### Step 2: Configure API URL

Update the API URL in `src/assets/config.json`:

```json
{
  "apiUrl": "http://localhost:1310"
}
```

#### Step 3: Start Development Server

```bash
# Start Angular dev server
npm start

# OR use Angular CLI directly
ng serve --port 4200
```

Expected output:

```
✔ Browser application bundle generation complete.

Initial Chunk Files   | Names         |  Raw Size
main.js               | main          | 250.45 kB | 
styles.css            | styles        |  85.23 kB | 

Application bundle generation complete. [5.234 seconds]

Watch mode enabled. Watching for file changes...
  ➜  Local:   http://localhost:4200/
```

#### Step 4: Access the UI

Open your browser and navigate to **http://localhost:4200**

Login with:
- **Username**: `admin`
- **Password**: `admin123`

### Frontend Features

The admin dashboard provides:

✅ **Client Management** - Create, view, update, delete clients  
✅ **Limit Configuration** - Configure per-client rate limits and quotas  
✅ **System Limits** - Manage global rate limits  
✅ **API Key Generation** - Generate secure API keys and secrets  
✅ **Real-Time Testing** - Test client API with HMAC authentication  
✅ **Usage Monitoring** - View rate limit headers and throttling status  
✅ **Internationalization** - English/French language support  

### Build for Production

```bash
cd frontend

# Build optimized production bundle
npm run build

# Output will be in dist/notifications-frontend/
# Deploy dist/ folder to Nginx, Apache, or any static host
```

Production build includes:
- AOT compilation
- Minification
- Tree shaking
- Lazy loading
- Service worker (optional)

---

## 🧪 How to Test the Solution

### 1. Unit Tests (Backend)

Run backend unit tests with Maven:

```bash
cd backend

# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=RateLimiterServiceTest

# Run with coverage report
./mvnw test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

**Coverage Target**: ~85% line coverage

### 2. Integration Tests (Backend)

Integration tests use Testcontainers (Docker required):

```bash
cd backend

# Run integration tests (starts Redis + PostgreSQL containers)
./mvnw verify

# Run specific integration test
./mvnw verify -Dit.test=RateLimiterIntegrationTest
```

**Key Integration Tests**:
- `RateLimiterIntegrationTest` - End-to-end rate limiting behavior
- `NotificationControllerIntegrationTest` - API endpoint testing
- `RedisCounterRepositoryTest` - Redis atomic operations

### 3. Frontend Tests

Run frontend tests with Vitest:

```bash
cd frontend

# Run all tests
npm test

# Run tests in watch mode
npm run test:watch

# Run with UI
npm run test:ui

# Run with coverage
npm run test:coverage

# View coverage report
open coverage/index.html
```

### 4. Load Tests with JMeter

Comprehensive load testing with Apache JMeter:

#### Prerequisites

```bash
# Install JMeter (macOS)
brew install jmeter

# Verify installation
jmeter --version
```

#### Run Load Tests

```bash
cd jmeter

# 1. Standard load test (10 users, 60 seconds)
jmeter -n -t Notifications-Load-Test.jmx \
  -l results/load-test.jtl \
  -e -o results/html-report-load

# 2. Rate limiting validation test
jmeter -n -t Rate-Limiting-Test.jmx \
  -l results/rate-limit-test.jtl \
  -e -o results/html-report-rate-limit

# 3. Admin API test
jmeter -n -t Admin-API-Test.jmx \
  -l results/admin-api-test.jtl \
  -e -o results/html-report-admin

# 4. Heavy load test (100 users, 10 minutes)
jmeter -n -t Notifications-Load-Test.jmx \
  -Jthreads.count=100 \
  -Jtest.duration=600 \
  -l results/heavy-load.jtl
```

#### View Results

```bash
# Open HTML report in browser
open results/html-report-load/index.html
```

### 5. Manual Testing Scenarios

#### Scenario 1: Test Soft Throttle (80-99% usage)

```bash
# Send 85 requests (limit is 100/minute)
for i in {1..85}; do
  curl -X POST http://localhost:1310/api/notifications \
    -H "X-API-KEY: LMirg1mq_LQXNQJ5H4ZC7WH5ZMzNTKRJSKvQhG2XHfU" \
    -H "Content-Type: application/json" \
    -d '{"channel":"SMS","recipient":"+250788123456","message":"Test"}' \
    -w "\nStatus: %{http_code}, Time: %{time_total}s\n" \
    -s -o /dev/null
done

# Expected: Later requests show increased latency (50-200ms delay)
```

#### Scenario 2: Test Hard Reject (100%+ usage)

```bash
# Send 105 requests (exceeds limit of 100/minute)
for i in {1..105}; do
  curl -X POST http://localhost:1310/api/notifications \
    -H "X-API-KEY: LMirg1mq_LQXNQJ5H4ZC7WH5ZMzNTKRJSKvQhG2XHfU" \
    -H "Content-Type: application/json" \
    -d '{"channel":"SMS","recipient":"+250788123456","message":"Test"}' \
    -i | grep -E "HTTP|X-RateLimit|Retry-After"
done

# Expected: Requests 101-105 return HTTP 429 with Retry-After header
```

#### Scenario 3: Verify Window Reset

```bash
# 1. Exhaust limit
for i in {1..100}; do curl ...; done

# 2. Verify rejection
curl ...  # Returns 429

# 3. Wait for window reset (60 seconds)
sleep 60

# 4. Verify acceptance
curl ...  # Returns 202 Accepted
```

#### Scenario 4: Test Distributed Consistency

```bash
# Send requests to both backend instances via load balancer
# They should share rate limits via Redis

ab -n 200 -c 10 -H "X-API-KEY: ..." http://localhost:1310/api/notifications

# Check Redis counters
docker exec notifications-redis redis-cli KEYS "rate:*"
docker exec notifications-redis redis-cli GET "rate:client:1:window:..."
```

### 6. Check Test Coverage Reports

After running tests:

```bash
# Backend coverage
open backend/target/site/jacoco/index.html

# Frontend coverage
open frontend/coverage/index.html
```

### Expected Test Results

| Test Suite | Tests | Pass | Coverage |
|------------|-------|------|----------|
| Backend Unit | ~30 | 100% | ~85% |
| Backend Integration | ~10 | 100% | N/A |
| Frontend Unit | ~20 | 100% | ~70% |
| JMeter Load | 3 plans | 100% | N/A |

📖 **Detailed Testing Guide**: See [jmeter/README.md](./jmeter/README.md) for comprehensive JMeter documentation.

---

## 📚 API Documentation

### Base URL

```
http://localhost:1310
```

### Authentication

This API uses **HMAC-SHA256** signature authentication for client requests and **Basic Auth** for admin endpoints.

#### HMAC Authentication (Client API)

**Required Headers**:
```http
X-API-KEY: <client-api-key>
X-TIMESTAMP: <unix-timestamp-milliseconds>
X-SIGNATURE: <hmac-sha256-base64>
```

**Signature Calculation**:
```javascript
// Payload format
const payload = `${timestamp}\n${method}\n${path}\n${body}`;

// Calculate HMAC-SHA256
const signature = HMAC_SHA256(payload, apiSecret);

// Base64 encode
const base64Signature = Base64.encode(signature);
```

**Timestamp Window**: ±120 seconds (2 minutes) from server time

#### Basic Authentication (Admin API)

**Default Credentials**:
- Username: `admin`
- Password: `admin123`

**Header**:
```http
Authorization: Basic YWRtaW46YWRtaW4xMjM=
```

### Client API Endpoints

#### 1. Send Notification (Rate Limited)

Send a notification via SMS or Email (async processing).

```http
POST /api/notifications
```

**Headers**:
```http
Content-Type: application/json
X-API-KEY: LMirg1mq_LQXNQJ5H4ZC7WH5ZMzNTKRJSKvQhG2XHfU
X-TIMESTAMP: 1733612345000
X-SIGNATURE: <calculated-hmac>
```

**Request Body**:
```json
{
  "channel": "SMS",
  "recipient": "+250788123456",
  "message": "Your verification code is 123456",
  "priority": "HIGH"
}
```

**Response (202 Accepted)**:
```json
{
  "id": 12345,
  "status": "PENDING",
  "channel": "SMS",
  "createdAt": "2025-12-07T14:30:00Z"
}
```

**Rate Limit Headers**:
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 75
X-RateLimit-Reset: 1733612400
X-Soft-Throttled: false
```

**Error Response (429 Too Many Requests)**:
```json
{
  "error": "Rate limit exceeded",
  "message": "Window limit exceeded (100/100)",
  "timestamp": "2025-12-07T14:30:00Z"
}
```

**Error Headers**:
```http
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1733612400
Retry-After: 45
```

### Admin API Endpoints

#### 2. List Clients

```http
GET /admin/clients/page?page=0&size=20&sort=name,asc
Authorization: Basic <credentials>
```

**Response (200 OK)**:
```json
{
  "content": [
    {
      "id": 1,
      "name": "TestClient",
      "apiKey": "LMirg1mq_...",
      "status": "ACTIVE",
      "createdAt": "2025-12-01T10:00:00Z"
    }
  ],
  "totalElements": 10,
  "totalPages": 1
}
```

#### 3. Create Client

```http
POST /admin/clients
Authorization: Basic <credentials>
Content-Type: application/json
```

**Request**:
```json
{
  "name": "NewClient",
  "email": "client@example.com",
  "description": "New SMS provider"
}
```

**Response (201 Created)**:
```json
{
  "id": 2,
  "name": "NewClient",
  "apiKey": "generated-api-key",
  "apiSecret": "generated-api-secret",
  "status": "ACTIVE"
}
```

#### 4. Get Client Details

```http
GET /admin/clients/{id}/details
Authorization: Basic <credentials>
```

**Response (200 OK)**:
```json
{
  "client": {
    "id": 1,
    "name": "TestClient",
    "status": "ACTIVE"
  },
  "limits": [
    {
      "id": 1,
      "limitType": "WINDOW",
      "windowSizeSeconds": 60,
      "maxRequestsPerWindow": 100,
      "softThrottleThreshold": 0.80,
      "hardRejectThreshold": 1.00
    },
    {
      "id": 2,
      "limitType": "MONTHLY",
      "monthlyQuota": 10000
    }
  ]
}
```

#### 5. Update Client Limits

```http
PUT /admin/clients/{id}/limits
Authorization: Basic <credentials>
Content-Type: application/json
```

**Request**:
```json
{
  "windowSizeSeconds": 60,
  "maxRequestsPerWindow": 200,
  "softThrottleThreshold": 0.85,
  "hardRejectThreshold": 1.00,
  "monthlyQuota": 50000
}
```

#### 6. Get System Limits

```http
GET /admin/system-limits
Authorization: Basic <credentials>
```

**Response**:
```json
[
  {
    "id": 1,
    "name": "global-hourly-limit",
    "limitType": "GLOBAL",
    "maxRequestsPerWindow": 1000000,
    "windowSizeSeconds": 3600,
    "active": true
  }
]
```

#### 7. Health Check

```http
GET /actuator/health
```

**Response**:
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "rabbit": {"status": "UP"}
  }
}
```

#### 8. Metrics (Prometheus)

```http
GET /actuator/prometheus
```

Returns metrics in Prometheus format for scraping.

### Swagger/OpenAPI Documentation

Interactive API documentation is available at:

**Swagger UI**: http://localhost:1310/swagger-ui.html  
**OpenAPI JSON**: http://localhost:1310/v3/api-docs

### Error Codes

| Code | Description | Action |
|------|-------------|--------|
| 200 | Success | N/A |
| 201 | Created | N/A |
| 202 | Accepted (Async) | N/A |
| 400 | Bad Request | Check request body |
| 401 | Unauthorized | Check API key/signature |
| 404 | Not Found | Check endpoint URL |
| 429 | Too Many Requests | Wait for Retry-After seconds |
| 500 | Internal Server Error | Contact support |
| 503 | Service Unavailable | Circuit breaker open, retry later |

---

## ⚡ Rate Limiting Behavior

### Rate Limit Types

This system enforces **three types** of rate limits simultaneously:

1. **Per-Client Window Limits** - Fixed time windows (e.g., 100 req/60s)
2. **Per-Client Monthly Quotas** - Calendar month limits (e.g., 10,000 req/month)
3. **Global System Limits** - Total system capacity (e.g., 1M req/hour)

### Throttling Strategies

#### 1. Normal Operation (0-79% usage)

✅ **Status**: HTTP 202 Accepted  
✅ **Behavior**: Normal processing, no delay  
✅ **Headers**: Standard rate limit headers  

```http
HTTP/1.1 202 Accepted
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 75
X-RateLimit-Reset: 1733612400
X-Soft-Throttled: false
```

#### 2. Soft Throttle (80-99% usage)

⚠️ **Status**: HTTP 202 Accepted (with delay)  
⚠️ **Behavior**: Artificial delay of 50-200ms (random jitter)  
⚠️ **Headers**: `X-Soft-Throttled: true`  
⚠️ **Counter**: Incremented  

**Purpose**: Gradual backpressure to slow clients before hard limit

```http
HTTP/1.1 202 Accepted
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 15
X-RateLimit-Reset: 1733612400
X-Soft-Throttled: true
```

#### 3. Hard Reject (100%+ usage)

🚫 **Status**: HTTP 429 Too Many Requests  
🚫 **Behavior**: Request rejected immediately  
🚫 **Headers**: `Retry-After` (seconds until window reset)  
🚫 **Counter**: NOT incremented  

**Purpose**: Strict enforcement, prevents quota overrun

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1733612400
Retry-After: 45
Content-Type: application/json

{
  "error": "Rate limit exceeded",
  "message": "Window limit exceeded (100/100)",
  "limitType": "WINDOW"
}
```

### Rate Limit Headers Reference

| Header | Description | Example |
|--------|-------------|---------|
| `X-RateLimit-Limit` | Maximum requests allowed in window | `100` |
| `X-RateLimit-Remaining` | Requests remaining in current window | `75` |
| `X-RateLimit-Reset` | Unix timestamp when window resets | `1733612400` |
| `X-Soft-Throttled` | Whether request was soft throttled | `true` / `false` |
| `Retry-After` | Seconds to wait before retry (429 only) | `45` |

### Fixed Window Algorithm

This system uses a **fixed window** approach:

```
Time Windows (60 seconds each):
┌─────────────────┬─────────────────┬─────────────────┐
│  14:00:00 -     │  14:01:00 -     │  14:02:00 -     │
│  14:01:00       │  14:02:00       │  14:03:00       │
│  (100 requests) │  (100 requests) │  (100 requests) │
└─────────────────┴─────────────────┴─────────────────┘
```

**Window Calculation**:
```
windowStart = (currentTimestamp / windowSize) * windowSize
Redis Key: rate:client:{id}:window:{windowStart}
TTL: windowSize seconds
```

**Trade-off**: Clients can burst at window boundaries (e.g., 100 at 14:00:59, 100 at 14:01:00 = 200 in 2 seconds). Mitigated by soft throttle starting at 80%.

### Redis Keys

```
# Window limit (resets every 60 seconds)
rate:client:1:window:1733612340

# Monthly quota (resets at month start)
rate:client:1:monthly:2025-12

# Global limit (hourly)
rate:global:window:1733612400
```

### Configuration

Default thresholds (configurable per client):

```yaml
app:
  rate-limiter:
    thresholds:
      soft-throttle: 0.80  # 80% usage
      hard-reject: 1.00    # 100% usage
    jitter:
      min-ms: 50           # Min delay
      max-ms: 200          # Max delay
```

---

## 🚢 Deployment

### Docker Compose (Production-Ready)

The provided `docker-compose.yml` is production-ready with:

✅ Health checks on all services  
✅ Restart policies  
✅ Volume persistence  
✅ Network isolation  
✅ Resource limits (optional)  
✅ Monitoring stack included  

#### Deploy Full Stack

```bash
# Clone repository
git clone <repo-url>
cd notifications

# Set environment variables (optional)
cp .env.example .env
nano .env

# Start all services in detached mode
docker compose up -d --build

# Verify health
docker compose ps
docker compose logs -f backend-1 backend-2

# Scale backend instances (optional)
docker compose up -d --scale backend-1=3 --scale backend-2=0
```

#### Production Environment Variables

Create `.env` file in project root:

```bash
# Database
POSTGRES_DB=notifications
POSTGRES_USER=postgres
POSTGRES_PASSWORD=<strong-password>

# RabbitMQ
RABBITMQ_USERNAME=notif_user
RABBITMQ_PASSWORD=<strong-password>

# Admin Credentials
ADMIN_USERNAME=admin
ADMIN_PASSWORD=<strong-password>

# Encryption
APP_CRYPTO_MASTER_KEY=<base64-encoded-32-byte-key>

# CORS (adjust for production)
CORS_ALLOWED_ORIGINS=https://yourdomain.com
```

#### Generate Secure Master Key

```bash
# Generate 256-bit key (32 bytes)
openssl rand -base64 32 > master_key.b64
cat master_key.b64
```

### Kubernetes (Future)

For Kubernetes deployment, see the planned Helm charts (coming soon).

Key considerations:
- Use StatefulSet for Redis (or Redis Sentinel/Cluster)
- Use Deployment for stateless backends
- Use ConfigMap/Secret for configuration
- Use Ingress for Traefik
- Use PersistentVolumeClaim for PostgreSQL

### Scaling Strategy

#### Horizontal Scaling (Add Backend Instances)

```bash
# Using Docker Compose
docker compose up -d --scale backend-1=5

# Traefik automatically detects new instances via Docker labels
# No configuration changes needed
```

#### Scaling Redis (If Needed)

Current setup uses a single Redis instance (sufficient for ~10K req/s).

For higher load:

**Option 1: Redis Sentinel** (Automatic Failover)
```yaml
# docker-compose.yml
redis-sentinel-1:
  image: redis:7-alpine
  command: redis-sentinel /etc/sentinel.conf
```

**Option 2: Redis Cluster** (Sharding)
```yaml
# Consistent hashing by client ID
redis-node-1:
  image: redis:7-alpine
  command: redis-server --cluster-enabled yes
```

#### Database Scaling

- **Read Replicas**: Add PostgreSQL read replicas for queries
- **Connection Pooling**: HikariCP (configured in Spring Boot)
- **Caching**: Caffeine local cache (5-minute TTL on client configs)

### Load Balancer Configuration

Traefik is configured with:

- **Load Balancing**: Round-robin across backends
- **Health Checks**: `/actuator/health` every 10s
- **Circuit Breaker**: Opens if >30% network errors or >25% 5xx responses
- **Retry Logic**: 3 retries with 100ms initial interval
- **Timeouts**: 30s read/write, 90s idle
- **CORS**: Configured for frontend origins

### SSL/TLS (Production)

Update `docker-compose.yml` for HTTPS:

```yaml
traefik:
  command:
    - "--entrypoints.websecure.address=:443"
    - "--certificatesresolvers.letsencrypt.acme.email=admin@example.com"
    - "--certificatesresolvers.letsencrypt.acme.storage=/letsencrypt/acme.json"
    - "--certificatesresolvers.letsencrypt.acme.httpchallenge.entrypoint=web"
  ports:
    - "443:443"
  volumes:
    - ./letsencrypt:/letsencrypt
```

Update backend labels:
```yaml
labels:
  - "traefik.http.routers.backend.tls=true"
  - "traefik.http.routers.backend.tls.certresolver=letsencrypt"
```

---

## 📊 Monitoring & Observability

### Metrics (Prometheus + Grafana)

#### Access Grafana

**URL**: http://localhost:3000  
**Credentials**: `admin` / `admin123`

#### Pre-configured Dashboard

Navigate to: **Dashboards → Notifications Dashboard**

**Metrics Included**:
- Request rate (requests/second)
- Response time (p50, p95, p99)
- Error rate (%)
- Rate limit events (soft throttle, hard reject, invalid API key)
- System health (CPU, memory, connections)
- Redis operations
- Database queries

#### Key Metrics

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| `ratelimiter_soft_throttle_total` | Soft throttle events | >1000/min |
| `ratelimiter_hard_reject_total` | Hard reject events | >100/min |
| `ratelimiter_invalid_api_key_total` | Invalid auth attempts | >50/min |
| `ratelimiter_check_and_consume_seconds` | Rate limiter latency | p95 >50ms |
| `http_server_requests_seconds` | API response time | p95 >500ms |

#### Custom Queries (PromQL)

```promql
# Request rate
rate(http_server_requests_seconds_count[5m])

# Error rate
rate(http_server_requests_seconds_count{status=~"5.."}[5m]) / rate(http_server_requests_seconds_count[5m])

# Soft throttle rate
rate(ratelimiter_soft_throttle_total[5m])

# Hard reject rate
rate(ratelimiter_hard_reject_total[5m])
```

### Logs (Loki + Grafana)

#### Query Logs in Grafana

1. Go to **Explore** in Grafana
2. Select **Loki** as data source
3. Run queries:

```logql
# All backend logs
{container="notifications-backend-1"}

# Rate limit events
{container="notifications-backend-1"} |= "Rate limit"

# Errors
{container="notifications-backend-1"} |= "ERROR"

# Specific client
{container="notifications-backend-1"} |= "clientId=1"
```

#### Structured Logging Format

Logs are in JSON format for easy parsing:

```json
{
  "timestamp": "2025-12-07T14:30:00.123Z",
  "level": "INFO",
  "logger": "com.irembo.notifications.filter.RateLimiterFilter",
  "message": "Rate limit soft throttle: usage: 82.50%",
  "clientId": 1,
  "apiKey": "LMirg1mq_...",
  "usagePercent": 82.50
}
```

### Tracing (Optional)

To enable distributed tracing with Zipkin/Jaeger:

```yaml
# application.yml
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://zipkin:9411/api/v2/spans
```

### Alerting (Prometheus Alertmanager)

Configure alerts in `monitoring/prometheus.yml`:

```yaml
groups:
  - name: rate_limiter
    rules:
      - alert: HighRateLimitRejects
        expr: rate(ratelimiter_hard_reject_total[5m]) > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High rate limit rejections"
```

### Health Checks

#### Application Health

```bash
curl http://localhost:1310/actuator/health

# Response
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "redis": {"status": "UP"},
    "rabbit": {"status": "UP"}
  }
}
```

#### Individual Component Health

```bash
# PostgreSQL
curl http://localhost:1310/actuator/health/db

# Redis
curl http://localhost:1310/actuator/health/redis

# RabbitMQ
curl http://localhost:1310/actuator/health/rabbit
```

---

## 🔧 Troubleshooting

### Common Issues

#### 1. Backend Won't Start

**Symptom**: Backend container exits with error

**Check logs**:
```bash
docker compose logs backend-1 backend-2
```

**Common causes**:
- PostgreSQL not ready → Wait for health check
- Redis connection refused → Check Redis is running
- Port 1310 already in use → Stop conflicting service

**Solution**:
```bash
# Restart services in order
docker compose down
docker compose up postgres redis rabbitmq -d
sleep 10
docker compose up backend-1 backend-2 -d
```

#### 2. 401 Unauthorized (HMAC)

**Symptom**: All API requests return 401

**Causes**:
- Incorrect API key
- Incorrect API secret
- Timestamp outside ±120s window
- Malformed signature

**Verify credentials**:
```sql
-- Connect to PostgreSQL
docker exec -it notifications-postgres psql -U postgres -d notifications

-- Check test client
SELECT id, name, api_key, api_secret_encrypted FROM client WHERE name = 'TestClient';
```

**Test HMAC generation**:
Use the JMeter HMAC script in `jmeter/scripts/hmac-signature.groovy` as reference.

#### 3. 429 Too Many Requests

**Symptom**: Requests rejected with 429

**Check current usage**:
```bash
# Check Redis counters
docker exec notifications-redis redis-cli KEYS "rate:client:*"

# Get specific counter
docker exec notifications-redis redis-cli GET "rate:client:1:window:1733612340"

# Check TTL
docker exec notifications-redis redis-cli TTL "rate:client:1:window:1733612340"
```

**Wait for window reset** or **increase limits**:
```bash
# Via Admin UI: http://localhost:1015
# Or via API:
curl -X PUT http://localhost:1310/admin/clients/1/limits \
  -u admin:admin123 \
  -H "Content-Type: application/json" \
  -d '{"maxRequestsPerWindow": 200}'
```

#### 4. Redis Connection Error

**Symptom**: `Could not connect to Redis`

**Check Redis**:
```bash
# Test connection
docker exec notifications-redis redis-cli ping

# Should return: PONG

# Check Redis logs
docker compose logs redis
```

**Solution**:
```bash
# Restart Redis
docker compose restart redis

# Or clear data and restart
docker compose down
docker volume rm notifications_redis_data
docker compose up redis -d
```

#### 5. Database Migration Failed

**Symptom**: Backend logs show Flyway error

**Check migrations**:
```bash
# Connect to database
docker exec -it notifications-postgres psql -U postgres -d notifications

# Check migration history
SELECT * FROM flyway_schema_history ORDER BY installed_rank;
```

**Solution**:
```bash
# Clean database and restart
docker compose down -v
docker compose up postgres -d
sleep 10
docker compose up backend-1 -d
```

#### 6. Frontend Can't Connect to Backend

**Symptom**: Angular app shows connection errors

**Check API URL**:
```bash
# Verify config
cat frontend/src/assets/config.json

# Should show
{"apiUrl": "http://localhost:1310"}
```

**Check CORS**:
```bash
# Test CORS headers
curl -X OPTIONS http://localhost:1310/api/notifications \
  -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: POST" \
  -v
```

**Solution**:
```bash
# Update CORS in docker-compose.yml backend environment
CORS_ALLOWED_ORIGINS: "http://localhost:4200,http://localhost:1015"

# Restart backend
docker compose restart backend-1 backend-2
```

#### 7. Out of Memory (JMeter)

**Symptom**: JMeter crashes during load test

**Solution**:
```bash
# Increase heap size
export HEAP="-Xms1g -Xmx4g"
jmeter -n -t Notifications-Load-Test.jmx ...

# Or reduce thread count
jmeter -n -t Notifications-Load-Test.jmx -Jthreads.count=50 ...
```

### Debug Mode

Enable debug logging:

```yaml
# application.yml
logging:
  level:
    com.irembo.notifications: DEBUG
    org.springframework.data.redis: DEBUG
```

Or via environment variable:
```bash
docker compose -f docker-compose.yml \
  -e LOGGING_LEVEL_COM_IREMBO_NOTIFICATIONS=DEBUG \
  up backend-1
```

### Get Support

1. Check logs: `docker compose logs -f`
2. Check [ARCHITECTURE.md](./ARCHITECTURE.md) for design details
3. Check GitHub Issues
4. Contact: engineering@irembo.com

---

## 🚀 Future Improvements

### Short-Term (High Priority)

1. **Redis Sentinel** - Automatic failover for Redis high availability
2. **Sliding Window Algorithm** - More accurate rate limiting (prevents double-rate bursts)
3. **Chaos Testing** - Automated failure injection tests for Redis/PostgreSQL
4. **Memory Alerts** - Prometheus alerts for Redis memory usage

### Medium-Term

5. **Client SDK** - JavaScript/Python/Java libraries with local rate limiting
6. **Priority-Based Quotas** - VIP clients get reserved capacity
7. **Rate Limit Forecasting** - Proactive alerts when clients approach limits
8. **Global Limit Load Test** - JMeter test validating system-wide throttling

### Long-Term

9. **Multi-Region Deployment** - Geographic distribution with Kafka quota sync
10. **ML-Based Anomaly Detection** - Detect suspicious traffic patterns
11. **Adaptive Rate Limiting** - Dynamic limits based on system load
12. **Client Usage Analytics Dashboard** - Real-time usage trends and predictions
13. **GraphQL API** - Alternative to REST for more flexible queries
14. **Event Sourcing** - Complete audit trail with event replay
15. **API Gateway Integration** - Kong/Apigee integration

### Performance Optimizations

- **HTTP/2** - Multiplexed connections for lower latency
- **gRPC** - High-performance alternative to REST
- **WebSocket** - Real-time usage updates for clients
- **CDN Integration** - Edge caching for static content

### Security Enhancements

- **OAuth 2.0** - Industry-standard authentication
- **JWT Tokens** - Alternative to HMAC for some use cases
- **Rate Limit by IP** - Additional layer of protection
- **API Key Rotation** - Automated key lifecycle management
- **Secret Encryption** - HashiCorp Vault integration

---

## 🤝 Contributing

### Development Setup

```bash
# Clone repository
git clone <repo-url>
cd notifications

# Create feature branch
git checkout -b feature/your-feature-name

# Make changes and test
docker compose up --build

# Run tests
cd backend && ./mvnw verify
cd frontend && npm test

# Commit changes
git add .
git commit -m "feat: add new feature"
git push origin feature/your-feature-name
```

### Code Style

- **Backend**: Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- **Frontend**: Follow [Angular Style Guide](https://angular.io/guide/styleguide)

### Testing Requirements

- ✅ All new features must have unit tests
- ✅ Integration tests for API endpoints
- ✅ JMeter load tests for performance-critical changes
- ✅ Minimum 80% code coverage

### Pull Request Checklist

- [ ] Code builds without errors
- [ ] All tests pass
- [ ] Documentation updated (README, ARCHITECTURE.md)
- [ ] No security vulnerabilities introduced
- [ ] CHANGELOG.md updated

---

## 📄 License

This project is proprietary software developed for Irembo Government Solutions.

© 2025 Irembo Government Solutions. All rights reserved.

---

## 📞 Contact & Support

**Engineering Team**: engineering@irembo.com  
**Documentation**: [ARCHITECTURE.md](./ARCHITECTURE.md)  
**Issues**: GitHub Issues  
**API Docs**: http://localhost:1310/swagger-ui.html  

---

## 🙏 Acknowledgments

Built with:
- [Spring Boot](https://spring.io/projects/spring-boot)
- [Angular](https://angular.io/)
- [Redis](https://redis.io/)
- [PostgreSQL](https://www.postgresql.org/)
- [Traefik](https://traefik.io/)
- [Docker](https://www.docker.com/)
- [Grafana](https://grafana.com/)

---

**Last Updated**: December 7, 2025  
**Version**: 1.0.0  
**Author**: Irembo Engineering Team

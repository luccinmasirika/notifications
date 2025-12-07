#!/bin/bash

###############################################################################
# JMeter Test Runner for Irembo Notifications API
# 
# Usage:
#   ./run-tests.sh [test-type] [options]
#
# Test Types:
#   load        - Run load test
#   rate-limit  - Run rate limiting test
#   admin       - Run admin API test
#   all         - Run all tests
#
# Examples:
#   ./run-tests.sh load
#   ./run-tests.sh rate-limit
#   ./run-tests.sh all
#   ./run-tests.sh load --threads=50 --duration=300
###############################################################################

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default configuration
JMETER_HOME=${JMETER_HOME:-$(which jmeter | xargs dirname | xargs dirname)}
TEST_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RESULTS_DIR="${TEST_DIR}/results"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)

# Server configuration
SERVER_HOST=${SERVER_HOST:-localhost}
SERVER_PORT=${SERVER_PORT:-1310}
SERVER_PROTOCOL=${SERVER_PROTOCOL:-http}

# Test parameters
THREADS=${THREADS:-10}
RAMPUP=${RAMPUP:-5}
DURATION=${DURATION:-60}

# Function to print colored messages
print_info() {
    echo -e "${BLUE}ℹ ${1}${NC}"
}

print_success() {
    echo -e "${GREEN}✓ ${1}${NC}"
}

print_error() {
    echo -e "${RED}✗ ${1}${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ ${1}${NC}"
}

# Function to check prerequisites
check_prerequisites() {
    print_info "Checking prerequisites..."
    
    # Check JMeter
    if ! command -v jmeter &> /dev/null; then
        print_error "JMeter is not installed or not in PATH"
        echo "Install with: brew install jmeter (macOS)"
        exit 1
    fi
    print_success "JMeter found: $(jmeter --version 2>&1 | head -n 1)"
    
    # Check Java
    if ! command -v java &> /dev/null; then
        print_error "Java is not installed or not in PATH"
        exit 1
    fi
    print_success "Java found: $(java -version 2>&1 | head -n 1)"
    
    # Check if server is running
    if curl -s -f "${SERVER_PROTOCOL}://${SERVER_HOST}:${SERVER_PORT}/health" > /dev/null 2>&1; then
        print_success "Server is running at ${SERVER_PROTOCOL}://${SERVER_HOST}:${SERVER_PORT}"
    else
        print_warning "Server might not be running at ${SERVER_PROTOCOL}://${SERVER_HOST}:${SERVER_PORT}"
        print_info "Start with: docker-compose up -d"
        read -p "Continue anyway? (y/n) " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            exit 1
        fi
    fi
}

# Function to create results directory
setup_results_dir() {
    mkdir -p "${RESULTS_DIR}"
    print_info "Results will be saved to: ${RESULTS_DIR}"
}

# Function to run load test
run_load_test() {
    print_info "Running Load Test..."
    print_info "Configuration: ${THREADS} threads, ${RAMPUP}s ramp-up, ${DURATION}s duration"
    
    local result_file="${RESULTS_DIR}/load-test-${TIMESTAMP}.jtl"
    local html_report="${RESULTS_DIR}/html-report-load-${TIMESTAMP}"
    
    jmeter -n -t "${TEST_DIR}/Notifications-Load-Test.jmx" \
        -Jserver.host="${SERVER_HOST}" \
        -Jserver.port="${SERVER_PORT}" \
        -Jserver.protocol="${SERVER_PROTOCOL}" \
        -Jthreads.count="${THREADS}" \
        -Jrampup.time="${RAMPUP}" \
        -Jtest.duration="${DURATION}" \
        -l "${result_file}" \
        -e -o "${html_report}"
    
    print_success "Load test completed!"
    print_info "Results: ${result_file}"
    print_info "HTML Report: ${html_report}/index.html"
    
    # Open HTML report if on macOS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        read -p "Open HTML report in browser? (y/n) " -n 1 -r
        echo
        if [[ $REPLY =~ ^[Yy]$ ]]; then
            open "${html_report}/index.html"
        fi
    fi
}

# Function to run rate limiting test
run_rate_limit_test() {
    print_info "Running Rate Limiting Test..."
    print_warning "This test will send 104+ requests to validate rate limiting behavior"
    print_warning "Ensure no other tests are running against the same client"
    
    read -p "Continue? (y/n) " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        print_info "Rate limiting test cancelled"
        return
    fi
    
    local result_file="${RESULTS_DIR}/rate-limit-test-${TIMESTAMP}.jtl"
    local html_report="${RESULTS_DIR}/html-report-rate-limit-${TIMESTAMP}"
    
    jmeter -n -t "${TEST_DIR}/Rate-Limiting-Test.jmx" \
        -Jserver.host="${SERVER_HOST}" \
        -Jserver.port="${SERVER_PORT}" \
        -Jserver.protocol="${SERVER_PROTOCOL}" \
        -l "${result_file}" \
        -e -o "${html_report}"
    
    print_success "Rate limiting test completed!"
    print_info "Results: ${result_file}"
    print_info "HTML Report: ${html_report}/index.html"
    
    # Show rate limiting metrics
    print_info "Fetching rate limiting metrics..."
    echo ""
    curl -s "${SERVER_PROTOCOL}://${SERVER_HOST}:${SERVER_PORT}/actuator/metrics/ratelimiter.soft_throttle" | jq '.measurements[0].value // "N/A"' | xargs echo "Soft Throttle Count:"
    curl -s "${SERVER_PROTOCOL}://${SERVER_HOST}:${SERVER_PORT}/actuator/metrics/ratelimiter.hard_reject" | jq '.measurements[0].value // "N/A"' | xargs echo "Hard Reject Count:"
    
    print_warning "Wait 60 seconds for rate limit window to reset before running another test"
}

# Function to run admin API test
run_admin_test() {
    print_info "Running Admin API Test..."
    
    local result_file="${RESULTS_DIR}/admin-test-${TIMESTAMP}.jtl"
    local html_report="${RESULTS_DIR}/html-report-admin-${TIMESTAMP}"
    
    jmeter -n -t "${TEST_DIR}/Admin-API-Test.jmx" \
        -Jserver.host="${SERVER_HOST}" \
        -Jserver.port="${SERVER_PORT}" \
        -Jserver.protocol="${SERVER_PROTOCOL}" \
        -l "${result_file}" \
        -e -o "${html_report}"
    
    print_success "Admin API test completed!"
    print_info "Results: ${result_file}"
    print_info "HTML Report: ${html_report}/index.html"
}

# Function to run all tests
run_all_tests() {
    print_info "Running all tests..."
    echo ""
    
    run_admin_test
    echo ""
    print_info "Waiting 5 seconds before next test..."
    sleep 5
    
    run_load_test
    echo ""
    print_info "Waiting 5 seconds before next test..."
    sleep 5
    
    run_rate_limit_test
    
    echo ""
    print_success "All tests completed!"
}

# Function to show usage
show_usage() {
    cat << EOF
${GREEN}JMeter Test Runner for Irembo Notifications API${NC}

${YELLOW}Usage:${NC}
  ./run-tests.sh [test-type] [options]

${YELLOW}Test Types:${NC}
  load        - Run load test (default: 10 threads, 60s)
  rate-limit  - Run rate limiting test
  admin       - Run admin API test
  all         - Run all tests sequentially

${YELLOW}Options:${NC}
  --threads=N     Number of threads (default: 10)
  --rampup=N      Ramp-up time in seconds (default: 5)
  --duration=N    Test duration in seconds (default: 60)
  --host=HOST     Server host (default: localhost)
  --port=PORT     Server port (default: 1310)
  --protocol=P    Protocol http/https (default: http)
  -h, --help      Show this help message

${YELLOW}Environment Variables:${NC}
  SERVER_HOST     Server hostname (default: localhost)
  SERVER_PORT     Server port (default: 1310)
  SERVER_PROTOCOL Protocol http/https (default: http)
  THREADS         Number of threads (default: 10)
  RAMPUP          Ramp-up time (default: 5)
  DURATION        Test duration (default: 60)

${YELLOW}Examples:${NC}
  # Run load test with default settings
  ./run-tests.sh load

  # Run load test with 50 threads for 5 minutes
  ./run-tests.sh load --threads=50 --duration=300

  # Run rate limiting test
  ./run-tests.sh rate-limit

  # Run all tests
  ./run-tests.sh all

  # Run against remote server
  ./run-tests.sh load --host=api.irembo.com --port=443 --protocol=https

${YELLOW}Prerequisites:${NC}
  - JMeter 5.6+ installed
  - Java 11+ installed
  - Server running at ${SERVER_PROTOCOL}://${SERVER_HOST}:${SERVER_PORT}

${YELLOW}Notes:${NC}
  - Results are saved in ${RESULTS_DIR}/
  - HTML reports are generated automatically
  - Rate limiting test should be run alone (not concurrent with other tests)
  - Wait 60 seconds between rate limiting tests for window reset

EOF
}

# Parse command line arguments
TEST_TYPE="${1:-}"
shift || true

while [[ $# -gt 0 ]]; do
    case $1 in
        --threads=*)
            THREADS="${1#*=}"
            shift
            ;;
        --rampup=*)
            RAMPUP="${1#*=}"
            shift
            ;;
        --duration=*)
            DURATION="${1#*=}"
            shift
            ;;
        --host=*)
            SERVER_HOST="${1#*=}"
            shift
            ;;
        --port=*)
            SERVER_PORT="${1#*=}"
            shift
            ;;
        --protocol=*)
            SERVER_PROTOCOL="${1#*=}"
            shift
            ;;
        -h|--help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Main execution
echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║  JMeter Test Runner - Irembo Notifications API           ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════════════════╝${NC}"
echo ""

check_prerequisites
setup_results_dir

echo ""

case "${TEST_TYPE}" in
    load)
        run_load_test
        ;;
    rate-limit|ratelimit)
        run_rate_limit_test
        ;;
    admin)
        run_admin_test
        ;;
    all)
        run_all_tests
        ;;
    "")
        print_error "No test type specified"
        echo ""
        show_usage
        exit 1
        ;;
    *)
        print_error "Unknown test type: ${TEST_TYPE}"
        echo ""
        show_usage
        exit 1
        ;;
esac

echo ""
print_success "Test execution completed!"
echo ""

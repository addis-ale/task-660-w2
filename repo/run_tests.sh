#!/bin/bash
###############################################################################
# Unified Test Execution Script
# Executes all unit tests and API interface functional tests for the
# Heritage Marketplace Operations Management System.
#
# Usage:
#   ./run_tests.sh              # Run all tests (unit + API if server running)
#   ./run_tests.sh unit         # Run only unit tests
#   ./run_tests.sh api          # Run only API tests
#
# Prerequisites:
#   - Java 17+ and Maven installed
#   - For API tests: application running at BASE_URL (default: http://localhost:8080)
###############################################################################

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODE="${1:-all}"

UNIT_EXIT=0
API_EXIT=0
UNIT_SKIPPED=false
API_SKIPPED=false

echo "###############################################"
echo "#  Heritage Marketplace — Test Suite Runner   #"
echo "###############################################"
echo "Mode:       $MODE"
echo "Started at: $(date)"
echo "###############################################"
echo ""

###############################################################################
# Unit Tests
###############################################################################

if [ "$MODE" = "all" ] || [ "$MODE" = "unit" ]; then
    echo "=============================================="
    echo "  PHASE 1: UNIT TESTS"
    echo "=============================================="
    echo ""

    if command -v mvn &> /dev/null; then
        bash "$SCRIPT_DIR/unit_tests/run_unit_tests.sh"
        UNIT_EXIT=$?
    else
        echo "  [SKIP] Maven not found. Cannot run unit tests."
        UNIT_SKIPPED=true
    fi

    echo ""
else
    UNIT_SKIPPED=true
fi

###############################################################################
# API Interface Functional Tests
###############################################################################

if [ "$MODE" = "all" ] || [ "$MODE" = "api" ]; then
    echo "=============================================="
    echo "  PHASE 2: API INTERFACE FUNCTIONAL TESTS"
    echo "=============================================="
    echo ""

    BASE_URL="${BASE_URL:-http://localhost:8080}"

    # Check if the server is reachable
    if curl -s --max-time 5 "${BASE_URL}/api/v1/listings" > /dev/null 2>&1; then
        bash "$SCRIPT_DIR/API_tests/run_api_tests.sh"
        API_EXIT=$?
    else
        echo "  [SKIP] Application is not reachable at $BASE_URL"
        echo "         Start the application first, then re-run with: ./run_tests.sh api"
        API_SKIPPED=true
    fi

    echo ""
else
    API_SKIPPED=true
fi

###############################################################################
# Summary
###############################################################################

echo "###############################################"
echo "#           OVERALL TEST SUMMARY              #"
echo "###############################################"
echo ""

if [ "$UNIT_SKIPPED" = true ]; then
    echo "  Unit Tests:     SKIPPED"
elif [ "$UNIT_EXIT" -eq 0 ]; then
    echo "  Unit Tests:     PASSED"
else
    echo "  Unit Tests:     FAILED (exit code $UNIT_EXIT)"
fi

if [ "$API_SKIPPED" = true ]; then
    echo "  API Tests:      SKIPPED"
elif [ "$API_EXIT" -eq 0 ]; then
    echo "  API Tests:      PASSED"
else
    echo "  API Tests:      FAILED (exit code $API_EXIT)"
fi

echo ""
echo "  Finished at: $(date)"
echo "###############################################"

# Exit with failure if any test suite failed
if [ "$UNIT_EXIT" -ne 0 ] || [ "$API_EXIT" -ne 0 ]; then
    exit 1
fi

exit 0

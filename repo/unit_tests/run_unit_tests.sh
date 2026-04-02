#!/bin/bash
###############################################################################
# Unit Test Runner
# Executes all JUnit 5 unit tests via Maven Surefire plugin.
###############################################################################

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "=============================================="
echo "  UNIT TEST SUITE"
echo "=============================================="
echo "Project root: $PROJECT_ROOT"
echo "Started at:   $(date)"
echo "=============================================="

cd "$PROJECT_ROOT"

# Run only the unit test classes (exclude integration tests)
mvn test \
  -Dtest="OrderServiceTest,ListingServiceTest,TicketServiceTest,AuthenticationServiceTest,InventoryServiceTest,BenefitEvaluationServiceTest,RiskServiceTest,AppealServiceTest,InputSanitizerTest,PasswordPolicyValidatorTest" \
  -DfailIfNoTests=false \
  -pl . \
  --batch-mode \
  2>&1

EXIT_CODE=$?

echo ""
echo "=============================================="
if [ $EXIT_CODE -eq 0 ]; then
  echo "  UNIT TESTS: ALL PASSED"
else
  echo "  UNIT TESTS: SOME FAILURES DETECTED"
fi
echo "  Finished at: $(date)"
echo "=============================================="

exit $EXIT_CODE

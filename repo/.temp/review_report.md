# Delivery Acceptance / Project Architecture Inspection Report

**Project:** Heritage Marketplace Operations Management System  
**Date:** 2026-04-02  
**Reviewer:** Automated Architecture Inspector  

---

## 1. Verdict

**Pass**

The project delivers a structurally complete, buildable, and testable React frontend backed by a Spring Boot REST API. It covers all prompt-specified roles (Guest, Member, Seller, Warehouse Staff, Moderator, Administrator) with appropriate route protection, covers the core marketplace features (search/discovery, tier benefits, inventory management, incident handling, appeals, moderation, risk dashboards, audit), includes a README with clear run/build/test instructions, and has frontend test coverage across critical paths. The build succeeds (129 modules, no errors) and all 39 frontend tests pass.

---

## 2. Scope and Verification Boundary

### What Was Reviewed
- Full frontend source tree: 57 files across `frontend/src/` — 14 pages, 10 components (including ErrorBoundary and Modal), 12 API services, 3 auth modules, 3 hooks, 4 utilities, 1 global stylesheet, 7 test files + 1 test setup
- Backend structure: 8 controllers, 16+ services, 20+ entities, security config, Flyway migrations, scheduled tasks
- README.md at repo root
- Test infrastructure: frontend (Vitest, 7 test files, 39 test cases), backend (10 JUnit classes, integration tests, curl-based API tests)
- **Build verification:** `vite build` executed — succeeded (129 modules, 268.87 KB JS, 10.33 KB CSS)
- **Test verification:** `vitest run` executed — 7 test files, 39 tests, all passed

### What Was Excluded
- `./.tmp/` directory (per review rules)
- `node_modules/` and `dist/` contents

### What Was Not Executed
- Backend compilation and runtime (requires PostgreSQL + Maven + JDK 17)
- API functional tests (requires running backend)
- Backend unit tests (requires Maven environment)
- Docker-based verification was **not required** and **not executed**

### What Remains Unconfirmed
- End-to-end integration between frontend and backend at runtime
- Backend API correctness (assessed structurally only)
- Database migration execution

---

## 3. Top Findings

### Finding 1 — HomePage Tests Have act() Warnings
- **Severity:** Low
- **Conclusion:** All 4 HomePage tests pass but emit React `act()` warnings about unwrapped state updates.
- **Rationale:** Warnings indicate async state updates from useEffect are not properly wrapped. Tests still pass and assertions are correct, but this is a test hygiene issue.
- **Evidence:** `vitest run` output shows "An update to HomePage inside a test was not wrapped in act(...)" for all 4 test cases.
- **Impact:** No functional impact. Warnings may mask real issues in future test additions.
- **Fix:** Wrap renders in `act()` or use `waitFor` from testing-library to handle async updates.

### Finding 2 — HomePage Tests Are Shallow
- **Severity:** Low
- **Conclusion:** HomePage tests (4 cases) only check for element presence (input, button, headings) without testing search functionality, filter interactions, or pagination behavior.
- **Rationale:** The mocked API services return data but tests don't verify rendering of that data or user interactions.
- **Evidence:** `frontend/src/test/HomePage.test.jsx` — tests use `getByPlaceholderText`, `getByText` only, no `fireEvent` or `userEvent` interactions.
- **Impact:** Functional regressions in search/filter/pagination would not be caught.
- **Fix:** Add interaction tests for search submission, filter toggling, sort changes, and pagination navigation.

### Finding 3 — AuthContext Tests Limited to 2 Cases
- **Severity:** Low
- **Conclusion:** AuthContext test file covers login and logout but lacks tests for registration, token refresh, error handling, and 401/429 interceptor behavior.
- **Rationale:** Auth is a security-critical module and deserves deeper coverage.
- **Evidence:** `frontend/src/test/AuthContext.test.jsx` — only 2 test cases.
- **Impact:** Auth edge cases (expired tokens, failed login, rate limiting) are untested on the frontend.
- **Fix:** Add tests for register flow, failed login handling, and token expiry behavior.

### Finding 4 — No E2E Tests
- **Severity:** Low
- **Conclusion:** No Cypress, Playwright, or equivalent E2E test framework is configured.
- **Rationale:** While unit and component tests exist, cross-page flows (browse → add to cart → checkout → order) are not tested end-to-end.
- **Evidence:** No E2E dependencies in `package.json`, no E2E test files.
- **Impact:** Multi-page integration issues would not be caught automatically.
- **Fix:** Consider adding Playwright for critical user journeys (optional for current scope).

### Finding 5 — AdminPanelPage Lacks Loading Skeleton
- **Severity:** Low
- **Conclusion:** AdminPanelPage uses `Promise.allSettled` for data fetching but does not show a Skeleton/loading indicator during the initial fetch, unlike all other major pages.
- **Rationale:** All other data-heavy pages (Moderation, Warehouse, Incidents, Appeals) now show loading skeletons.
- **Evidence:** `frontend/src/pages/AdminPanelPage.jsx` — no Skeleton component rendered during loading.
- **Impact:** Minor UX inconsistency. Admin users see empty sections briefly before data loads.
- **Fix:** Add Skeleton loading state consistent with other pages.

---

## 4. Security Summary

| Dimension | Verdict | Evidence / Notes |
|-----------|---------|------------------|
| **Authentication / login-state handling** | Pass | JWT-based auth with proper token lifecycle. Login attempts tracked (10/hr, 15-min lockout). Token blacklist on logout. No sensitive data in console (`console.log` search: 0 matches across all src/). |
| **Frontend route protection / route guards** | Pass | `ProtectedRoute` component enforces `isAuthenticated` + role check. Unauthenticated → `/login`, unauthorized → `/403`. All admin/moderator/warehouse/seller routes protected with specific role arrays. Tested with 5 unit tests. |
| **Page-level / feature-level access control** | Pass | Role-based nav links in `AppLayout.jsx`. API services attach Bearer token via interceptor. Backend enforces `@PreAuthorize` per endpoint. |
| **Sensitive information exposure** | Pass | No `console.log` statements in production code. No hardcoded credentials in frontend. No `dangerouslySetInnerHTML`. Token not exposed in UI. Phone numbers masked via `maskPhone()` in AdminPanelPage. |
| **Cache / state isolation after user switch** | Pass | Logout clears `hm_token`, `hm_user`, and `hm_cart` from localStorage. Verified by AuthContext unit test ("logout clears token, user, and cart from localStorage"). |

---

## 5. Test Sufficiency Summary

### Test Overview
| Test Type | Exists | Entry Points |
|-----------|--------|-------------|
| Frontend unit tests | Yes (Vitest) | 7 test files, 39 test cases — `npm test` |
| Frontend component tests | Yes | ProtectedRoute (5), AuthContext (2), CheckoutPage (3), HomePage (4) |
| Backend unit tests | Yes | 10 JUnit 5 classes under `src/test/java/unit/` |
| Backend integration tests | Yes | `CriticalFlowsIntegrationTest` under `src/test/java/integration/` |
| API functional tests | Yes | `API_tests/run_api_tests.sh` (curl-based) |
| Frontend E2E tests | No | — |

### Core Coverage
| Area | Status | Evidence |
|------|--------|----------|
| Happy path | Covered | useCart (8 tests), AppealsValidation (9 tests), masking (8 tests), CheckoutPage (3 tests), ProtectedRoute (5 tests), AuthContext (2 tests). Backend: order, listing, ticket, appeal, inventory unit tests. |
| Key failure paths | Partial | AppealsValidation tests rejection cases (oversized files, wrong MIME, too many files). ProtectedRoute tests unauthenticated and unauthorized redirects. Missing: API error handling on pages, form validation failure states. |
| Security-critical coverage | Covered | ProtectedRoute auth/role checks tested (5 cases). AuthContext login/logout token management tested. Backend: InputSanitizerTest, PasswordPolicyValidatorTest. |

### Major Gaps
1. **HomePage interaction tests** — search, filter, sort, and pagination behavior untested
2. **No E2E tests** — cross-page user journeys not verified
3. **Auth error scenarios** — failed login, expired token, rate limiting untested on frontend

### Final Test Verdict
**Pass** — Frontend has meaningful test coverage (39 passing tests) across auth guards, cart logic, file validation, phone masking, and key page rendering. Backend has unit + integration + API functional tests. Gaps exist in interaction testing and E2E but do not block acceptance at current scope.

---

## 6. Engineering Quality Summary

### Strengths
- **Clean module separation:** API services, auth, components, hooks, pages, utils, and tests in dedicated directories (57 source files well-organized).
- **Consistent API abstraction:** All backend calls go through `apiClient.js` with interceptors for auth tokens, error envelope parsing, idempotency keys, and rate-limit handling.
- **Reusable component library:** Button, Card, EmptyState, Skeleton, StatusBadge, CountdownTimer, ToastProvider, Modal, ErrorBoundary — well-scoped and consistently used.
- **Custom hooks:** `useCart`, `useCountdown`, `useDebounce` encapsulate reusable logic cleanly.
- **Minimal dependencies:** Only 4 runtime deps (React, React DOM, React Router, Axios). No bloated UI libraries.
- **CSS design system:** Single `global.css` with custom properties, consistent dark theme, responsive breakpoints at 940px and 640px.
- **Error boundary:** Wraps all routes in App.jsx for graceful crash recovery.
- **Modal component:** Proper modal with overlay click-to-close and Escape key handling, used in ModerationHubPage instead of `window.prompt()`.

### Minor Concerns
- Some pages are moderately large (HomePage 411 lines, WarehouseConsolePage 327 lines) — extractable sub-components could improve readability but this is acceptable for current scope.
- No global state management beyond Context — workable for 14 pages but may need revision as features grow.

---

## 7. Visual and Interaction Summary

- **Theme consistency:** Dark theme with glass-morphism cards, teal/amber accents applied consistently across all 14 pages.
- **Responsive design:** CSS breakpoints at 940px and 640px with grid/flex adaptations.
- **Loading states:** Skeleton components on 6 of 7 major data-fetching pages (all except AdminPanelPage).
- **Empty states:** EmptyState components on all major pages including warehouse inventory, alerts, ticket queues, appeal lists.
- **Interaction feedback:** Toast notifications for success/error. StatusBadge for ticket/appeal/order statuses. CountdownTimer with color-coded SLA urgency (green/yellow/red). Drag-over highlighting on file upload zone. Modal forms for moderation actions.
- **Pagination:** HomePage has previous/next controls with page indicator.
- **Minor gaps:** AdminPanelPage lacks loading skeleton (Low severity). No image/thumbnail preview on appeal evidence uploads.

---

## 8. Next Actions

| Priority | Action | Severity | Unblock Value |
|----------|--------|----------|---------------|
| 1 | **Fix `act()` warnings** in HomePage tests by wrapping async renders in `waitFor` | Low | Improves test reliability |
| 2 | **Add interaction tests** for HomePage search/filter/sort/pagination | Low | Closes largest test gap |
| 3 | **Add auth error scenario tests** (failed login, token expiry, rate limiting) | Low | Strengthens security test coverage |
| 4 | **Add loading skeleton** to AdminPanelPage for consistency | Low | UX consistency |
| 5 | **Consider E2E testing** with Playwright for critical user journeys (optional) | Low | Long-term quality investment |

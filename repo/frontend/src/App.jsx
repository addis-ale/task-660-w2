import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./auth/ProtectedRoute";
import { AppLayout } from "./components/AppLayout";
import { ErrorBoundary } from "./components/ErrorBoundary";
import AdminPanelPage from "./pages/AdminPanelPage";
import AppealsPage from "./pages/AppealsPage";
import CheckoutPage from "./pages/CheckoutPage";
import ForbiddenPage from "./pages/ForbiddenPage";
import HomePage from "./pages/HomePage";
import IncidentsPage from "./pages/IncidentsPage";
import ListingDetailPage from "./pages/ListingDetailPage";
import LoginPage from "./pages/LoginPage";
import ModerationHubPage from "./pages/ModerationHubPage";
import NotFoundPage from "./pages/NotFoundPage";
import OrdersPage from "./pages/OrdersPage";
import RegisterPage from "./pages/RegisterPage";
import SellerDashboardPage from "./pages/SellerDashboardPage";
import WarehouseConsolePage from "./pages/WarehouseConsolePage";

export default function App() {
  return (
    <ErrorBoundary>
    <AppLayout>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/listings/:id" element={<ListingDetailPage />} />

        <Route path="/login" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="/403" element={<ForbiddenPage />} />

        <Route
          path="/checkout"
          element={
            <ProtectedRoute roles={["MEMBER"]}>
              <CheckoutPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/orders"
          element={
            <ProtectedRoute roles={["MEMBER"]}>
              <OrdersPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/seller"
          element={
            <ProtectedRoute roles={["SELLER", "ADMIN"]}>
              <SellerDashboardPage />
            </ProtectedRoute>
          }
        />
        <Route
          path="/warehouse"
          element={
            <ProtectedRoute roles={["WAREHOUSE_STAFF", "ADMIN"]}>
              <WarehouseConsolePage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/incidents"
          element={
            <ProtectedRoute
              roles={[
                "MEMBER",
                "SELLER",
                "WAREHOUSE_STAFF",
                "MODERATOR",
                "ADMIN",
              ]}
            >
              <IncidentsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/appeals"
          element={
            <ProtectedRoute
              roles={[
                "MEMBER",
                "SELLER",
                "WAREHOUSE_STAFF",
                "MODERATOR",
                "ADMIN",
              ]}
            >
              <AppealsPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/moderation"
          element={
            <ProtectedRoute roles={["MODERATOR", "ADMIN"]}>
              <ModerationHubPage />
            </ProtectedRoute>
          }
        />

        <Route
          path="/admin"
          element={
            <ProtectedRoute roles={["ADMIN"]}>
              <AdminPanelPage />
            </ProtectedRoute>
          }
        />

        <Route path="/home" element={<Navigate to="/" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AppLayout>
    </ErrorBoundary>
  );
}

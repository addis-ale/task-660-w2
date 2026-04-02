import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { useToast } from "../components/ToastProvider";

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const { login } = useAuth();
  const { showToast } = useToast();
  const [form, setForm] = useState({ email: "", password: "" });
  const [saving, setSaving] = useState(false);

  const onSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      const user = await login(form);
      const next =
        location.state?.from?.pathname || defaultRouteByRole(user?.role);
      navigate(next, { replace: true });
    } catch (error) {
      showToast(error?.message || "Login failed", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="auth-shell">
      <Card className="auth-card">
        <h1>Welcome Back</h1>
        <p>Sign in to manage listings, orders, incidents, and operations.</p>
        <form className="form-grid" onSubmit={onSubmit}>
          <label>
            Email
            <input
              type="email"
              required
              value={form.email}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, email: e.target.value }))
              }
            />
          </label>
          <label>
            Password
            <input
              type="password"
              required
              value={form.password}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, password: e.target.value }))
              }
            />
          </label>
          <Button type="submit" disabled={saving}>
            {saving ? "Signing in..." : "Sign In"}
          </Button>
        </form>
        <p>
          New here? <Link to="/register">Create an account</Link>
        </p>
      </Card>
    </div>
  );
}

function defaultRouteByRole(role) {
  switch (role) {
    case "SELLER":
      return "/seller";
    case "WAREHOUSE_STAFF":
      return "/warehouse";
    case "MODERATOR":
      return "/moderation";
    case "ADMIN":
      return "/admin";
    case "MEMBER":
      return "/";
    default:
      return "/";
  }
}

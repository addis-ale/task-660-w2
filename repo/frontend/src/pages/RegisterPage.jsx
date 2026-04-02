import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Button } from "../components/Button";
import { Card } from "../components/Card";
import { useToast } from "../components/ToastProvider";

export default function RegisterPage() {
  const navigate = useNavigate();
  const { register } = useAuth();
  const { showToast } = useToast();
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState({
    email: "",
    password: "",
    display_name: "",
    phone: "",
  });

  const onSubmit = async (event) => {
    event.preventDefault();
    setSaving(true);
    try {
      await register(form);
      showToast("Account created. Please sign in.", "success");
      navigate("/login");
    } catch (error) {
      showToast(error?.message || "Registration failed", "error");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="auth-shell">
      <Card className="auth-card">
        <h1>Create Account</h1>
        <p>Join the Heritage Marketplace with a secure member profile.</p>
        <form className="form-grid" onSubmit={onSubmit}>
          <label>
            Display Name
            <input
              required
              minLength={2}
              maxLength={100}
              value={form.display_name}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, display_name: e.target.value }))
              }
            />
          </label>
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
            Phone (optional)
            <input
              value={form.phone}
              onChange={(e) =>
                setForm((prev) => ({ ...prev, phone: e.target.value }))
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
            {saving ? "Creating..." : "Create Account"}
          </Button>
        </form>
        <p>
          Already registered? <Link to="/login">Sign in</Link>
        </p>
      </Card>
    </div>
  );
}

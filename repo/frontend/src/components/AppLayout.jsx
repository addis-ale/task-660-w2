import { Link, NavLink, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { Button } from "./Button";

const roleLinks = {
  GUEST: [],
  MEMBER: [
    { to: "/checkout", label: "Checkout" },
    { to: "/orders", label: "My Orders" },
    { to: "/incidents", label: "Incidents" },
    { to: "/appeals", label: "Appeals" },
  ],
  SELLER: [
    { to: "/seller", label: "Seller" },
    { to: "/incidents", label: "Incidents" },
    { to: "/appeals", label: "Appeals" },
  ],
  WAREHOUSE_STAFF: [
    { to: "/warehouse", label: "Warehouse" },
    { to: "/incidents", label: "Incidents" },
    { to: "/appeals", label: "Appeals" },
  ],
  MODERATOR: [
    { to: "/moderation", label: "Moderation" },
    { to: "/incidents", label: "Incidents" },
    { to: "/appeals", label: "Appeals" },
  ],
  ADMIN: [
    { to: "/admin", label: "Admin" },
    { to: "/moderation", label: "Moderation" },
    { to: "/warehouse", label: "Warehouse" },
    { to: "/seller", label: "Seller" },
  ],
};

export function AppLayout({ children }) {
  const navigate = useNavigate();
  const { user, isAuthenticated, logout } = useAuth();
  const role = user?.role || "GUEST";
  const links = roleLinks[role] || [];

  const handleLogout = async () => {
    await logout();
    navigate("/login");
  };

  return (
    <div className="app-shell">
      <header className="top-nav">
        <div className="top-nav-inner">
          <Link to="/" className="brand">
            Heritage Marketplace
          </Link>
          <nav className="main-nav">
            <NavLink to="/">Browse</NavLink>
            {links.map((link) => (
              <NavLink key={link.to} to={link.to}>
                {link.label}
              </NavLink>
            ))}
          </nav>
          <div className="nav-actions">
            {isAuthenticated ? (
              <>
                <span className="user-chip">
                  {user?.display_name || user?.email || "User"}
                </span>
                <Button variant="ghost" onClick={handleLogout}>
                  Logout
                </Button>
              </>
            ) : (
              <>
                <Link className="link-btn" to="/login">
                  Login
                </Link>
                <Link className="link-btn" to="/register">
                  Register
                </Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main className="page-wrap">{children}</main>
    </div>
  );
}

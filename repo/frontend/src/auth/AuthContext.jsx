import { createContext, useContext, useEffect, useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { authService } from "../api/authService";
import { setRateLimitHandler, setUnauthorizedHandler } from "../api/apiClient";
import { useToast } from "../components/ToastProvider";
import { getToken, setToken } from "./tokenStore";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const navigate = useNavigate();
  const { showToast } = useToast();
  const [token, setTokenState] = useState(getToken());
  const [user, setUser] = useState(() => {
    const raw = localStorage.getItem("hm_user");
    return raw ? JSON.parse(raw) : null;
  });
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      setToken("");
      setTokenState("");
      setUser(null);
      localStorage.removeItem("hm_user");
      showToast("Session expired. Please login again.", "warn");
      navigate("/login");
    });

    setRateLimitHandler((retryAfter) => {
      showToast(`Rate limit exceeded. Try again in ${retryAfter}s`, "warn");
    });
  }, [navigate, showToast]);

  useEffect(() => {
    if (!token || user) {
      return;
    }
    let mounted = true;

    setLoading(true);
    authService
      .me()
      .then((profile) => {
        if (!mounted) return;
        setUser(profile);
        localStorage.setItem("hm_user", JSON.stringify(profile));
      })
      .catch(() => {
        setToken("");
        setTokenState("");
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [token, user]);

  const login = async (credentials) => {
    const result = await authService.login(credentials);
    const nextToken = result.access_token;
    setToken(nextToken);
    setTokenState(nextToken);
    const profile = await authService.me();
    setUser(profile);
    localStorage.setItem("hm_user", JSON.stringify(profile));
    showToast("Welcome back.", "success");
    return profile;
  };

  const register = async (payload) => {
    return authService.register(payload);
  };

  const logout = async () => {
    try {
      if (token) {
        await authService.logout();
      }
    } catch {
      // best effort
    }
    setToken("");
    setTokenState("");
    setUser(null);
    localStorage.removeItem("hm_user");
    localStorage.removeItem("hm_cart");
  };

  const value = useMemo(
    () => ({
      token,
      user,
      loading,
      isAuthenticated: Boolean(token),
      login,
      register,
      logout,
      refreshProfile: async () => {
        const profile = await authService.me();
        setUser(profile);
        localStorage.setItem("hm_user", JSON.stringify(profile));
        return profile;
      },
    }),
    [token, user, loading],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { authApi, ensureCsrf, type SessionUser } from "./api";
import { ApiError } from "../../lib/http";

type AuthState = { user: SessionUser | null; loading: boolean; refresh: () => Promise<SessionUser | null>; logout: () => Promise<void> };
const Context = createContext<AuthState | null>(null);
const cachedIdentity = "stockly:offline-identity";
const pendingLogout = "stockly:offline-logout";

function rememberedUser(): SessionUser | null {
  try {
    if (localStorage.getItem(pendingLogout)) return null;
    return JSON.parse(localStorage.getItem(cachedIdentity) ?? "null");
  } catch { return null; }
}

function rememberUser(user: SessionUser | null) {
  try {
    if (user?.companyId && !user.mustChangePassword)
      localStorage.setItem(cachedIdentity, JSON.stringify({ ...user, email: "", username: "" }));
    else localStorage.removeItem(cachedIdentity);
  } catch { /* The current online session can continue without local storage. */ }
}

async function loadSession() {
  try {
    await ensureCsrf();
    if (localStorage.getItem(pendingLogout)) {
      await authApi.logout();
      localStorage.removeItem(pendingLogout);
      await ensureCsrf();
    }
    const current = await authApi.me();
    rememberUser(current);
    return current;
  } catch (failure) {
    if (failure instanceof ApiError && failure.status === 0) return rememberedUser();
    rememberUser(null);
    return null;
  }
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user,setUser]=useState<SessionUser|null>(null);
  const [loading,setLoading]=useState(true);
  const refresh=useCallback(async () => {
    const next = await loadSession();
    setUser(next);
    return next;
  },[]);
  useEffect(()=>{
    let alive=true;
    const restore = async () => {
      const next = await loadSession();
      if (alive) { setUser(next); setLoading(false); }
    };
    const changed = (event: StorageEvent) => {
      if (event.key === pendingLogout && event.newValue) setUser(null);
    };
    void restore();
    window.addEventListener("online", restore);
    window.addEventListener("storage", changed);
    return () => {
      alive = false;
      window.removeEventListener("online", restore);
      window.removeEventListener("storage", changed);
    };
  },[]);
  const logout = useCallback(async () => {
    try { localStorage.setItem(pendingLogout, "1"); } catch { /* Continue with server logout. */ }
    rememberUser(null);
    setUser(null);
    try {
      await authApi.logout();
      localStorage.removeItem(pendingLogout);
      await ensureCsrf();
    } catch { /* Retry server logout when this device reconnects. */ }
  }, []);
  return <Context.Provider value={{user,loading,refresh,logout}}>{children}</Context.Provider>;
}
export function useAuth() {
  const value=useContext(Context);
  if(!value) throw new Error("AuthProvider requerido");
  return value;
}

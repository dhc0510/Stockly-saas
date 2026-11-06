import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { authApi, ensureCsrf, type SessionUser } from "./api";

type AuthState = { user: SessionUser | null; loading: boolean; refresh: () => Promise<SessionUser | null>; logout: () => Promise<void> };
const Context = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user,setUser]=useState<SessionUser|null>(null);
  const [loading,setLoading]=useState(true);
  const refresh=useCallback(async () => {
    try { const next=await authApi.me(); setUser(next); return next; }
    catch { setUser(null); return null; }
  },[]);
  useEffect(()=>{
    let alive=true;
    (async()=>{
      try { await ensureCsrf(); const next=await authApi.me(); if(alive) setUser(next); }
      catch { if(alive) setUser(null); }
      finally { if(alive) setLoading(false); }
    })();
    return ()=>{alive=false;};
  },[]);
  const logout=useCallback(async()=>{try{await authApi.logout();}finally{setUser(null);}},[]);
  return <Context.Provider value={{user,loading,refresh,logout}}>{children}</Context.Provider>;
}
export function useAuth() {
  const value=useContext(Context);
  if(!value) throw new Error("AuthProvider requerido");
  return value;
}

import { request } from "../../lib/http";

export type SessionUser = {
  id: string;
  fullName: string;
  username: string;
  email: string;
  role: "SAAS_ADMIN" | "COMPANY_ADMIN" | "WORKER";
  mustChangePassword: boolean;
  companyId: string | null;
  modules: { moduleKey: string; accessLevel: "NONE" | "READ" | "OPERATE" | "ADMIN" }[];
  specialPermissions: string[];
};
export type LoginResult = { status: string; challengeId: string | null; message: string };

declare global {
  interface Window {
    stocklySession?: { csrfToken: string; csrfHeaderName: string };
  }
}

export async function ensureCsrf() {
  const value = await request<{ csrfToken: string; csrfHeaderName: string }>("/api/auth/csrf");
  window.stocklySession = value;
}

export const authApi = {
  login: (identifier: string, password: string) => request<LoginResult>("/api/auth/login", {
    method: "POST", body: JSON.stringify({ identifier, password }),
  }),
  verify: (challengeId: string, code: string, rememberDevice: boolean) => request<SessionUser>("/api/auth/device-challenges/verify", {
    method: "POST", body: JSON.stringify({ challengeId, code, rememberDevice }),
  }),
  me: () => request<SessionUser>("/api/auth/me"),
  logout: () => request<void>("/api/auth/logout", { method: "POST" }),
  firstChange: (newPassword: string) => request<SessionUser>("/api/auth/password/first-change", {
    method: "POST", body: JSON.stringify({ newPassword }),
  }),
  recovery: (email: string) => request<{ message: string }>("/api/auth/password-recovery", {
    method: "POST", body: JSON.stringify({ email }),
  }),
  completeRecovery: (token: string, newPassword: string) => request<{ message: string }>("/api/auth/password-recovery/complete", {
    method: "POST", body: JSON.stringify({ token, newPassword }),
  }),
};

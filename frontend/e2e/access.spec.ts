// STK-005 · Acceso, usuarios y permisos: pantallas a 375 y 1440 px con la API simulada dentro de Playwright.
// El comportamiento del servidor (sesiones, códigos, permisos, aislamiento) se verifica con el recorrido real documentado en docs/04-Testing/STK-005.
import { test, expect, type Page } from "@playwright/test";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const CODE = "482913";
const TEMP = "Temporal-de-un-solo-uso-24h";

type State = {
  stage: "anon" | "challenge" | "change" | "in";
  role: "COMPANY_ADMIN" | "WORKER";
  workers: { id: string; fullName: string; username: string; email: string; active: boolean; roleId: string; roleName: string }[];
  roles: { id: string; name: string; modules: Record<string, string>; specialPermissions: string[]; assignedWorkers: number }[];
  policy: number | null;
  audit: { id: string; actorId: string; actorName: string; action: string; actionLabel: string; targetType: string; targetId: string; targetLabel: string; occurredAt: string }[];
  calls: string[];
};

const labels: Record<string, string> = {
  USER_CREATED: "Trabajador creado", ROLE_CREATED: "Rol creado", SESSION_POLICY_UPDATED: "Política de sesión actualizada",
  TEMP_PASSWORD_ISSUED: "Contraseña temporal emitida", USER_DEACTIVATED: "Trabajador desactivado",
};

async function mockApi(page: Page, initial: Partial<State> = {}) {
  const s: State = {
    stage: "anon", role: "COMPANY_ADMIN", policy: 120, calls: [], audit: [],
    roles: [{ id: "role-1", name: "Cajero", modules: { SALES: "OPERATE" }, specialPermissions: [], assignedWorkers: 1 }],
    workers: [{ id: "w-1", fullName: "Laura Jiménez Mora", username: "laura.j", email: "laura@example.com", active: true, roleId: "role-1", roleName: "Cajero" }],
    ...initial,
  };
  const log = (action: string, target: string) => s.audit.unshift({ id: `e${s.audit.length}`, actorId: "admin", actorName: "Andrea Solís", action,
    actionLabel: labels[action], targetType: "USER", targetId: target, targetLabel: target, occurredAt: "2026-11-09T16:30:00Z" });
  const fail = (status: number, code: string, message: string) => ({ status, json: { code, message, fieldErrors: {}, requestId: "r" } });
  await page.route("**/api/**", (route) => {
    const req = route.request(); const path = new URL(req.url()).pathname; const method = req.method();
    const body = req.postDataJSON?.() ?? null; s.calls.push(`${method} ${path}`);
    const me = { id: "admin", fullName: s.role === "WORKER" ? "Laura Jiménez Mora" : "Andrea Solís", username: "andrea", email: "andrea@example.com",
      role: s.role, mustChangePassword: s.stage === "change", companyId: "company-1", modules: [], specialPermissions: [] };
    if (path === "/api/auth/csrf") return route.fulfill({ json: { csrfToken: "t", csrfHeaderName: "X-CSRF-TOKEN" } });
    if (path === "/api/auth/me") return s.stage === "change" || s.stage === "in" ? route.fulfill({ json: me }) : route.fulfill(fail(401, "UNAUTHENTICATED", "Inicia sesión para continuar."));
    if (path === "/api/auth/login") {
      if (body.password !== TEMP) return route.fulfill(fail(401, "INVALID_CREDENTIALS", "No se pudo iniciar sesión. Revisa las credenciales o recupera el acceso."));
      s.stage = "challenge"; return route.fulfill({ json: { status: "DEVICE_CODE_REQUIRED", challengeId: "ch-1", message: "Te enviamos un código." } });
    }
    if (path === "/api/auth/device-challenges/verify") {
      if (body.code !== CODE) return route.fulfill(fail(400, "INVALID_CODE", "El código no es válido. Revisa el correo e inténtalo de nuevo."));
      s.stage = "change"; return route.fulfill({ json: { ...me, mustChangePassword: true } });
    }
    if (path === "/api/auth/password/first-change") { s.stage = "in"; return route.fulfill({ json: { ...me, mustChangePassword: false } }); }
    if (path === "/api/auth/password-recovery") return route.fulfill({ json: { message: "Si el correo corresponde a una cuenta activa y verificada, recibirás un enlace de recuperación." } });
    if (path === "/api/auth/password-recovery/complete")
      return body.token === "valido.secreto" ? route.fulfill({ json: { message: "Contraseña actualizada." } })
        : route.fulfill(fail(400, "INVALID_RECOVERY", "El enlace no es válido, venció o ya fue utilizado. Solicita uno nuevo."));
    if (path === "/api/auth/logout") { s.stage = "anon"; return route.fulfill({ status: 204 }); }
    if (path === "/api/users" && method === "GET") return route.fulfill({ json: s.workers });
    if (path === "/api/users" && method === "POST") {
      const role = s.roles.find(r => r.id === body.roleId)!;
      const worker = { id: `w-${s.workers.length + 1}`, fullName: body.fullName, username: body.username, email: body.email, active: true, roleId: role.id, roleName: role.name };
      s.workers.push(worker); role.assignedWorkers++; log("USER_CREATED", body.fullName);
      return route.fulfill({ json: { worker, temporaryPassword: TEMP, temporaryPasswordExpiresAt: "2026-11-10T16:30:00Z" } });
    }
    const userAction = path.match(/^\/api\/users\/([^/]+)\/(deactivate|temporary-password|role)$/);
    if (userAction) {
      const w = s.workers.find(x => x.id === userAction[1])!;
      if (userAction[2] === "deactivate") { w.active = false; log("USER_DEACTIVATED", w.fullName); return route.fulfill({ status: 200, body: "" }); }
      if (userAction[2] === "temporary-password") { log("TEMP_PASSWORD_ISSUED", w.fullName); return route.fulfill({ json: { worker: w, temporaryPassword: "Otra-temporal-de-un-uso-24", temporaryPasswordExpiresAt: "2026-11-10T16:30:00Z" } }); }
    }
    if (path === "/api/roles" && method === "GET") return route.fulfill({ json: s.roles });
    if (path === "/api/roles" && method === "POST") {
      const role = { id: `role-${s.roles.length + 1}`, name: body.name, modules: body.modules, specialPermissions: body.specialPermissions, assignedWorkers: 0 };
      s.roles.push(role); log("ROLE_CREATED", body.name); return route.fulfill({ json: role });
    }
    if (path === "/api/permissions/modules") return route.fulfill({ json: [{ key: "CATALOG", label: "Catálogo" }, { key: "INVENTORY", label: "Existencias" }, { key: "SALES", label: "Ventas" }] });
    if (path === "/api/permissions/special") return route.fulfill({ json: [{ key: "MANAGE_USERS", label: "Gestionar usuarios" }, { key: "VOID_SALES", label: "Anular ventas" }] });
    if (path === "/api/company/security/session-policy") {
      if (method === "PUT") { s.policy = body.idleTimeoutMinutes; log("SESSION_POLICY_UPDATED", "Sesiones"); }
      return route.fulfill({ json: { idleTimeoutMinutes: s.policy } });
    }
    if (path === "/api/audit") return route.fulfill({ json: { items: s.audit, total: s.audit.length, page: 0, size: 20 } });
    if (path === "/api/subscription") return route.fulfill({ json: { state: "ACTIVE", limited: false } });
    return route.fulfill({ status: 404, json: { code: "NOT_FOUND", message: "Sin datos." } });
  });
  return s;
}

async function capture(page: Page, name: string, project: string) {
  const dir = resolve("../docs/04-Testing/STK-005/screenshots");
  await mkdir(dir, { recursive: true });
  await page.screenshot({ path: resolve(dir, `${name}-${project}.png`), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
}

async function go(page: Page, name: string, mobile: boolean) {
  if (mobile) await page.getByRole("button", { name: "Abrir menú" }).click();
  await page.getByRole("link", { name, exact: true }).click();
}

test("ingreso, código de dispositivo y cambio de contraseña temporal", async ({ page }, testInfo) => {
  const p = testInfo.project.name;
  const s = await mockApi(page);
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Iniciar sesión" })).toBeVisible();
  await capture(page, "01-ingreso", p);
  // Teclado: el formulario se recorre con Tab y se envía con Enter.
  await page.getByLabel("Usuario o correo").focus();
  await page.keyboard.type("andrea");
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("Contraseña")).toBeFocused();
  await page.keyboard.type("una contraseña incorrecta");
  await page.keyboard.press("Enter");
  await expect(page.getByRole("alert")).toHaveText(/No se pudo iniciar sesión/);
  expect(s.stage).toBe("anon");
  await page.getByLabel("Contraseña").fill(TEMP);
  await page.getByRole("button", { name: "Ingresar" }).click();

  await expect(page.getByRole("heading", { name: "Verifica este dispositivo" })).toBeVisible();
  await page.getByLabel("Código de verificación").fill("12ab34");
  await expect(page.getByLabel("Código de verificación")).toHaveValue("1234");
  await page.getByLabel("Código de verificación").fill("111111");
  await page.getByRole("button", { name: "Verificar código" }).click();
  await expect(page.getByRole("alert")).toHaveText(/El código no es válido/);
  await capture(page, "02-codigo", p);
  await page.getByLabel("Código de verificación").fill(CODE);
  await page.getByLabel("Recordar este dispositivo por 30 días").check();
  await page.getByRole("button", { name: "Verificar código" }).click();

  await expect(page.getByRole("heading", { name: "Cambia tu contraseña temporal" })).toBeVisible();
  await page.getByLabel("Nueva contraseña").fill("una contraseña nueva y larga");
  await page.getByLabel("Repite la contraseña").fill("otra contraseña distinta");
  await page.getByRole("button", { name: "Guardar contraseña" }).click();
  await expect(page.getByRole("alert")).toHaveText("Las contraseñas no coinciden.");
  expect(s.calls).not.toContain("POST /api/auth/password/first-change");
  await capture(page, "03-cambio-temporal", p);
  await page.getByLabel("Repite la contraseña").fill("una contraseña nueva y larga");
  await page.getByRole("button", { name: "Guardar contraseña" }).click();
  await expect(page.getByRole("heading", { name: "Usuarios" })).toBeVisible();
  const verify = s.calls.filter(c => c.endsWith("/device-challenges/verify")).length;
  expect(verify).toBe(2);
});

test("recuperación con respuesta genérica y enlace de un solo uso", async ({ page }, testInfo) => {
  const p = testInfo.project.name;
  await mockApi(page);
  await page.goto("/login");
  await page.getByRole("link", { name: "¿Olvidaste tu contraseña?" }).click();
  await expect(page.getByRole("heading", { name: "Recuperar acceso" })).toBeVisible();
  await page.getByLabel("Correo electrónico").fill("cualquiera@example.com");
  await page.getByRole("button", { name: "Enviar enlace" }).click();
  await expect(page.getByRole("status")).toHaveText(/Si el correo corresponde a una cuenta activa y verificada/);
  await capture(page, "04-recuperacion", p);

  await page.goto("/reset-password?token=usado.viejo");
  await page.getByLabel("Nueva contraseña").fill("una contraseña nueva y larga");
  await page.getByLabel("Repite la contraseña").fill("una contraseña nueva y larga");
  await page.getByRole("button", { name: "Cambiar contraseña" }).click();
  await expect(page.getByRole("alert")).toHaveText(/no es válido, venció o ya fue utilizado/);
  await page.goto("/reset-password?token=valido.secreto");
  await page.getByLabel("Nueva contraseña").fill("una contraseña nueva y larga");
  await page.getByLabel("Repite la contraseña").fill("una contraseña nueva y larga");
  await page.getByRole("button", { name: "Cambiar contraseña" }).click();
  await expect(page.getByRole("status")).toHaveText(/Contraseña actualizada/);
  await capture(page, "05-contrasena-recuperada", p);
  await page.goto("/reset-password");
  await expect(page.getByRole("button", { name: "Cambiar contraseña" })).toBeDisabled();
});

test("administrador gestiona trabajadores, roles, sesiones y actividad", async ({ page }, testInfo) => {
  const p = testInfo.project.name; const mobile = p === "mobile";
  const s = await mockApi(page, { stage: "in" });
  page.on("dialog", d => d.accept());
  await page.goto("/users");
  await expect(page.getByRole("heading", { name: "Usuarios" })).toBeVisible();
  await expect(page.getByText("Laura Jiménez Mora", { exact: true })).toBeVisible();
  await capture(page, "06-usuarios", p);

  await go(page, "Roles y permisos", mobile);
  await page.getByRole("button", { name: "Crear rol" }).click();
  await page.getByLabel("Nombre del rol").fill("Bodega");
  await page.locator(".permission-row", { hasText: "Existencias" }).getByRole("combobox").selectOption("READ");
  await page.locator(".permission-row", { hasText: "Gestionar usuarios" }).getByRole("checkbox").check();
  await capture(page, "07-rol", p);
  await page.getByRole("button", { name: "Guardar rol" }).click();
  await expect(page.getByRole("heading", { name: "Bodega" })).toBeVisible();
  expect(s.roles.find(r => r.name === "Bodega")).toMatchObject({ modules: { INVENTORY: "READ" }, specialPermissions: ["MANAGE_USERS"] });
  // Un rol con trabajadores asignados no se puede eliminar.
  await expect(page.locator(".role-card", { hasText: "Cajero" }).getByRole("button", { name: "Eliminar" })).toBeDisabled();

  await go(page, "Usuarios", mobile);
  await page.getByRole("button", { name: "Crear trabajador" }).click();
  await page.getByLabel("Nombre completo").fill("Mario Rojas");
  await page.getByLabel("Nombre de usuario").fill("mario.r");
  await page.getByLabel("Correo electrónico").fill("mario@example.com");
  await page.getByRole("combobox", { name: "Rol de trabajador" }).selectOption({ label: "Bodega" });
  await page.getByRole("button", { name: "Crear trabajador" }).last().click();
  const reveal = page.locator(".credential-reveal");
  await expect(reveal).toContainText("Contraseña temporal de un solo uso");
  await expect(reveal.locator("code")).toHaveText(TEMP);
  await capture(page, "08-contrasena-temporal", p);
  await reveal.getByRole("button", { name: "Ocultar" }).click();
  await expect(page.getByText(TEMP)).toHaveCount(0);
  await page.reload();
  await expect(page.getByText("Mario Rojas", { exact: true })).toBeVisible();
  await expect(page.getByText(TEMP)).toHaveCount(0);

  const row = page.getByRole("row", { name: /Laura Jiménez Mora/ });
  await row.getByRole("button", { name: "Desactivar" }).click();
  await expect(row.getByText("Desactivado")).toBeVisible();
  await expect(row.getByRole("button", { name: "Nueva contraseña" })).toHaveCount(0);

  await go(page, "Seguridad", mobile);
  await expect(page.getByRole("heading", { name: "Sesiones" })).toBeVisible();
  await expect(page.locator('input[type="number"]')).toHaveValue("120");
  await page.getByLabel("No cerrar por inactividad").check();
  await page.getByRole("button", { name: "Guardar cambios" }).click();
  await expect(page.getByRole("status")).toHaveText(/Configuración guardada/);
  expect(s.policy).toBeNull();
  await capture(page, "09-sesiones", p);

  await go(page, "Actividad", mobile);
  await expect(page.getByRole("heading", { name: "Actividad" })).toBeVisible();
  const list = page.locator(mobile ? ".audit-mobile-list" : ".audit-desktop");
  for (const label of ["Trabajador creado", "Rol creado", "Trabajador desactivado", "Política de sesión actualizada"])
    await expect(list.getByText(label, { exact: true }).first()).toBeVisible();
  await expect(page.getByText(TEMP)).toHaveCount(0);
  await capture(page, "10-actividad", p);
});

test("un trabajador no ve la gestión de acceso", async ({ page }) => {
  const s = await mockApi(page, { stage: "in", role: "WORKER" });
  await page.goto("/users");
  await expect(page.getByRole("heading", { name: "Acceso" })).toBeVisible();
  for (const name of ["Usuarios", "Roles y permisos", "Seguridad", "Actividad"])
    await expect(page.getByRole("link", { name, exact: true })).toHaveCount(0);
  expect(s.calls.some(c => c.includes("/api/users") || c.includes("/api/audit"))).toBe(false);
});

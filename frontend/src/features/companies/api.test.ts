import { afterEach, describe, expect, it, vi } from "vitest";
import { api, type Company } from "./api";
import { ApiError } from "../../lib/http";

afterEach(() => {
  vi.unstubAllGlobals();
  delete (window as Window & { stocklySession?: unknown }).stocklySession;
});
describe("contrato de escritura de empresas", () => {
  it("envía únicamente campos editables y usa sesión/CSRF sin inventar permisos", async () => {
    const fetch = vi
      .fn()
      .mockResolvedValue(new Response("{}", { status: 200 }));
    vi.stubGlobal("fetch", fetch);
    Object.assign(window, { stocklySession: { csrfToken: "test-csrf-token" } });
    const company = {
      id: "company-id",
      commercialName: "El Sol",
      legalName: "El Sol SA",
      identificationType: "LEGAL",
      identification: "3101765432",
      contactEmail: "contact@example.com",
      phone: "",
      address: "",
      status: "ACTIVE",
      createdAt: "2026-11-05T16:00:00Z",
      version: 4,
      administrator: {
        id: "admin-id",
        fullName: "Admin",
        username: "admin",
        email: "admin@example.com",
      },
    } satisfies Company;
    await api.update(company.id, company, company.version);
    const [, options] = fetch.mock.calls[0];
    const payload = JSON.parse(options.body);
    expect(payload.version).toBe(4);
    expect(payload.company).not.toHaveProperty("administrator");
    expect(payload.company).not.toHaveProperty("status");
    expect(payload.company).not.toHaveProperty("id");
    expect(options.credentials).toBe("same-origin");
    expect(options.headers.get("X-CSRF-TOKEN")).toBe("test-csrf-token");
    expect(options.headers.has("Authorization")).toBe(false);
  });
  it("preserva conflictos del servidor sin informar éxito", async () => {
    vi.stubGlobal(
      "fetch",
      vi
        .fn()
        .mockResolvedValue(
          new Response(
            JSON.stringify({
              code: "STALE_VERSION",
              message: "La empresa cambió.",
              fieldErrors: {},
            }),
            { status: 409 },
          ),
        ),
    );
    await expect(api.get("company-id")).rejects.toBeInstanceOf(ApiError);
  });
});

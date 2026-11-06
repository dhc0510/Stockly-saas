import { describe, expect, it, vi, afterEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { CompanyForm } from "./CompanyForm";
import { api } from "./api";
import { ApiError } from "../../lib/http";

vi.mock("./api", async (original) => ({
  ...(await original<typeof import("./api")>()),
  api: { create: vi.fn(), get: vi.fn(), update: vi.fn() },
}));
afterEach(() => vi.clearAllMocks());
describe("registro de empresa", () => {
  it("muestra campos obligatorios y lleva el foco al primer error sin llamar a la API", async () => {
    render(
      <MemoryRouter>
        <CompanyForm />
      </MemoryRouter>,
    );
    await userEvent.click(
      screen.getByRole("button", { name: "Registrar empresa" }),
    );
    await waitFor(() =>
      expect(screen.getByLabelText("Nombre comercial *")).toHaveFocus(),
    );
    expect(api.create).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Tipo de identificación *")).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });
  it("conserva datos y explica un duplicado devuelto por el servidor", async () => {
    vi.mocked(api.create).mockRejectedValue(
      new ApiError(409, "CONFLICT", "La identificación ya está registrada."),
    );
    render(
      <MemoryRouter>
        <CompanyForm />
      </MemoryRouter>,
    );
    const user = userEvent.setup();
    await user.type(screen.getByLabelText("Nombre comercial *"), "El Sol");
    await user.type(
      screen.getByLabelText("Nombre o razón social *"),
      "El Sol SA",
    );
    await user.selectOptions(
      screen.getByLabelText("Tipo de identificación *"),
      "LEGAL",
    );
    await user.type(
      screen.getByLabelText("Número de identificación *"),
      "3101765432",
    );
    await user.type(
      screen.getByLabelText("Correo de contacto *"),
      "contact@example.com",
    );
    await user.type(
      screen.getByLabelText("Nombre completo *"),
      "Admin Inicial",
    );
    await user.type(screen.getByLabelText("Nombre de usuario *"), "avarela");
    await user.type(
      screen.getByLabelText("Correo del administrador *"),
      "admin@example.com",
    );
    await user.type(
      screen.getByLabelText("Contraseña temporal *"),
      "contraseña segura de prueba",
    );
    await user.click(screen.getByRole("button", { name: "Registrar empresa" }));
    expect(
      await screen.findByText("La identificación ya está registrada."),
    ).toBeVisible();
    expect(screen.getByLabelText("Nombre comercial *")).toHaveValue("El Sol");
    expect(api.create).toHaveBeenCalledTimes(1);
  });
});

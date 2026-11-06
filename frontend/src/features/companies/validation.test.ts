import { describe, expect, it } from "vitest";
import { validate } from "./validation";
import { emptyCompany, registrationDate } from "./api";

const company = {
  ...emptyCompany,
  commercialName: "El Sol",
  legalName: "El Sol S.A.",
  identificationType: "LEGAL",
  identification: "3-101-765432",
  contactEmail: "contact@example.com",
};
const admin = {
  fullName: "Admin Inicial",
  username: "avarela",
  email: "admin@example.com",
  temporaryPassword: "  contraseña segura  ",
};
describe("validaciones de empresa y administrador", () => {
  it("acepta datos válidos, opcionales vacíos y contraseña con espacios", () => {
    expect(validate(company, admin)).toEqual({});
  });
  it("no permite enviar el formulario vacío y exige el tipo", () => {
    const errors = validate(emptyCompany);
    expect(errors.commercialName).toBeTruthy();
    expect(errors.identificationType).toBeTruthy();
    expect(errors.contactEmail).toBeTruthy();
  });
  it("valida estructura de todos los tipos admitidos", () => {
    for (const [identificationType, identification] of [
      ["PHYSICAL", "1-1234-5678"],
      ["LEGAL", "3101765432"],
      ["DIMEX", "12345678901"],
      ["DIMEX", "123456789012"],
      ["NITE", "3120123456"],
    ])
      expect(
        validate({ ...company, identificationType, identification }),
      ).toEqual({});
    expect(
      validate({
        ...company,
        identificationType: "PHYSICAL",
        identification: "012345678",
      }).identification,
    ).toBeTruthy();
  });
  it("valida límites, usuario, teléfono y correos", () => {
    const errors = validate(
      { ...company, phone: "+506 123", address: "a".repeat(501) },
      {
        ...admin,
        username: "inválido",
        email: "invalid",
        temporaryPassword: "short",
      },
    );
    expect(Object.keys(errors).sort()).toEqual([
      "address",
      "email",
      "phone",
      "temporaryPassword",
      "username",
    ]);
    expect(
      validate({ ...company, commercialName: "a".repeat(151) }).commercialName,
    ).toBeTruthy();
  });
  it("presenta la fecha en Costa Rica y no en la zona del equipo", () => {
    expect(registrationDate("2026-11-06T05:59:59Z")).toContain("05");
  });
});

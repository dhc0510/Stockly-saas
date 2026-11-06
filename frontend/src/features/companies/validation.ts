import type { CompanyInput, Administrator } from "./api.ts";

export function validate(company: CompanyInput, admin?: Administrator) {
  const errors: Record<string, string> = {};
  const name = (key: string, value: string) => {
    if (value.trim().length < 2 || value.trim().length > 150)
      errors[key] = "Usa entre 2 y 150 caracteres.";
  };
  const email = (key: string, value: string) => {
    if (
      value.trim().length > 254 ||
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value.trim())
    )
      errors[key] = "Ingresa un correo válido.";
  };
  name("commercialName", company.commercialName);
  name("legalName", company.legalName);
  const patterns: Record<string, RegExp> = {
    PHYSICAL: /^[1-9][0-9]{8}$/,
    LEGAL: /^[1-9][0-9]{9}$/,
    DIMEX: /^[1-9][0-9]{10,11}$/,
    NITE: /^[0-9]{10}$/,
  };
  if (!patterns[company.identificationType])
    errors.identificationType = "Selecciona el tipo de identificación.";
  else if (
    !patterns[company.identificationType].test(
      company.identification.replace(/[\s-]/g, ""),
    )
  )
    errors.identification =
      "La identificación no corresponde al tipo seleccionado.";
  email("contactEmail", company.contactEmail);
  if (
    company.phone.trim() &&
    (company.phone.trim().length > 30 ||
      !/^\+?[0-9 ()-]+$/.test(company.phone.trim()) ||
      !/^[0-9]{8,20}$/.test(company.phone.replace(/\D/g, "")))
  )
    errors.phone = "Ingresa un teléfono de 8 a 20 dígitos.";
  if (company.address.trim().length > 500)
    errors.address = "Usa un máximo de 500 caracteres.";
  if (admin) {
    name("fullName", admin.fullName);
    email("email", admin.email);
    if (!/^[A-Za-z0-9._-]{3,30}$/.test(admin.username.trim()))
      errors.username =
        "Usa 3–30 letras sin tildes, números, punto, guion o guion bajo.";
    if (
      admin.temporaryPassword.length < 15 ||
      admin.temporaryPassword.length > 128
    )
      errors.temporaryPassword = "Usa entre 15 y 128 caracteres.";
  }
  return errors;
}

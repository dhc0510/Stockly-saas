import { request } from "../../lib/http";

export type CompanyInput = {
  commercialName: string;
  legalName: string;
  identificationType: string;
  identification: string;
  contactEmail: string;
  phone: string;
  address: string;
};
export type Administrator = {
  fullName: string;
  username: string;
  email: string;
  temporaryPassword: string;
};
export type Company = CompanyInput & {
  id: string;
  status: "ACTIVE" | "SUSPENDED";
  createdAt: string;
  version: number;
  administrator: Omit<Administrator, "temporaryPassword"> & { id: string };
};
export type Filters = {
  search: string;
  status: string;
  registeredFrom: string;
  registeredTo: string;
  page: number;
};
export type CompanyPage = {
  items: Company[];
  total: number;
  page: number;
  size: number;
};
const base = "/api/companies";
function companyPayload(c: CompanyInput): CompanyInput {
  return {
    commercialName: c.commercialName,
    legalName: c.legalName,
    identificationType: c.identificationType,
    identification: c.identification,
    contactEmail: c.contactEmail,
    phone: c.phone,
    address: c.address,
  };
}
export const api = {
  list(filters: Filters, signal?: AbortSignal) {
    const params = new URLSearchParams({ size: "20" });
    for (const [key, value] of Object.entries(filters))
      if (value !== "") params.set(key, String(value));
    return request<CompanyPage>(`${base}?${params}`, { signal });
  },
  get: (id: string, signal?: AbortSignal) =>
    request<Company>(`${base}/${encodeURIComponent(id)}`, { signal }),
  create: (company: CompanyInput, administrator: Administrator) =>
    request<Company>(base, {
      method: "POST",
      body: JSON.stringify({ company: companyPayload(company), administrator }),
    }),
  update: (id: string, company: CompanyInput, version: number) =>
    request<Company>(`${base}/${id}`, {
      method: "PATCH",
      body: JSON.stringify({ company: companyPayload(company), version }),
    }),
  state: (company: Company) =>
    request<Company>(
      `${base}/${company.id}/${company.status === "ACTIVE" ? "suspend" : "reactivate"}`,
      { method: "POST", body: JSON.stringify({ version: company.version }) },
    ),
  delete: (company: Company, confirmationName: string) =>
    request<void>(`${base}/${company.id}/delete`, {
      method: "POST",
      body: JSON.stringify({ version: company.version, confirmationName }),
    }),
};
export const emptyCompany: CompanyInput = {
  commercialName: "",
  legalName: "",
  identificationType: "",
  identification: "",
  contactEmail: "",
  phone: "",
  address: "",
};
export const identificationTypes: Record<string, string> = {
  PHYSICAL: "Cédula física",
  LEGAL: "Cédula jurídica",
  DIMEX: "DIMEX",
  NITE: "NITE",
};
export function registrationDate(value: string) {
  return new Intl.DateTimeFormat("es-CR", {
    day: "2-digit",
    month: "short",
    year: "numeric",
    timeZone: "America/Costa_Rica",
  }).format(new Date(value));
}

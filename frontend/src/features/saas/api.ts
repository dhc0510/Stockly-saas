import { ApiError, request } from "../../lib/http";

// STK-018 · Contratos de /api/saas y /api/subscription. Fechas AAAA-MM-DD (días de Costa Rica); montos en colones.
export type SubscriptionState = "TRIAL" | "ACTIVE" | "GRACE" | "SUSPENDED" | "CANCELLED";
export type Settings = { trialDays:number; graceDays:number; noticeDays:number; trialProducts:number; trialUsers:number; trialBranches:number;
  paymentInstructions:string|null; version:number };
export type Plan = { id:string; name:string; monthlyPrice:number; annualPrice:number|null; description:string|null; active:boolean;
  companies:number; hasPayments:boolean; version:number };
export type PlanInput = { name:string; monthlyPrice:number|null; annualPrice:number|null; description:string; active:boolean; version?:number };
export type Payment = { id:string; planName:string; cycle:"MONTHLY"|"ANNUAL"; periods:number; amount:number; paidOn:string; method:"SINPE"|"TRANSFER"|"CASH";
  reference:string|null; coversFrom:string; coversUntil:string; createdBy:string; createdAt:string; voidedAt:string|null; voidedBy:string|null; voidReason:string|null };
export type Subscription = { companyId:string; companyName:string; legalName:string; state:SubscriptionState; planId:string|null; planName:string|null;
  cycle:"MONTHLY"|"ANNUAL"|null; price:number|null; trialEndsOn:string; paidUntil:string|null; expiresOn:string; suspendsOn:string; limited:boolean;
  cancelledAt:string|null; cancelReason:string|null; createdAt:string; payments:Payment[]; noticesSent:string[]; version:number };
export type FiscalDocument = { id:string; paymentId:string; docType:"01"|"03"; environment:"TEST"|"PROD"; consecutive:string; key:string; issuedAt:string;
  status:"PENDING"|"PROCESSING"|"ACCEPTED"|"REJECTED"; statusReason:string|null; mailStatus:string };
export type Detail = { subscription:Subscription; documents:FiscalDocument[] };
export type Summary = { companyId:string; state:SubscriptionState; planName:string|null; cycle:string|null; expiresOn:string; suspendsOn:string;
  lastPayment:string|null; limited:boolean };
export type Attention = { companyId:string|null; companyName:string; state:SubscriptionState|"INVOICE_REJECTED"; planName:string|null; date:string; reason:string };
export type PanelView = { panel:{ states:Record<SubscriptionState, number>; monthTotal:number; monthPayments:number; monthVoided:number; attention:Attention[] };
  invoices:{ accepted:number; problems:number; pending:number } };
export type Billing = { legalName:string|null; idType:string|null; idNumber:string|null; email:string|null; activityCode:string|null; cabysCode:string|null;
  province:string|null; canton:string|null; district:string|null; otherSigns:string|null; environment:"TEST"|"PROD"; hasCredentials:boolean;
  keySubject:string|null; keyExpiresAt:string|null; credentialsCheckedAt:string|null; serverReady:boolean; issuerComplete:boolean; productionReady:boolean; version:number };
export type IssuerInput = Pick<Billing, "legalName"|"idType"|"idNumber"|"email"|"activityCode"|"cabysCode"|"province"|"canton"|"district"|"otherSigns">;
export type PaymentInput = { requestKey:string; planId:string; cycle:"MONTHLY"|"ANNUAL"; periods:number; amount:number|null; paidOn:string;
  method:"SINPE"|"TRANSFER"|"CASH"; reference:string };
export type Mine = { subscription:{ state:SubscriptionState; planName:string|null; cycle:string|null; price:number|null; trialEndsOn:string; paidUntil:string|null;
  expiresOn:string; suspendsOn:string; limited:boolean; usage:{ products:number; users:number; branches:number }; limits:{ products:number; users:number; branches:number };
  paymentInstructions:string|null; plans:Plan[]; payments:Payment[]; noticeDays:number }; documents:FiscalDocument[] };

const json = (method:string, body:unknown) => ({ method, body: JSON.stringify(body) });
export const saasApi = {
  panel: () => request<PanelView>("/api/saas/panel"),
  settings: () => request<Settings>("/api/saas/settings"),
  saveSettings: (s:Settings) => request<Settings>("/api/saas/settings", json("PUT", s)),
  plans: () => request<Plan[]>("/api/saas/plans"),
  savePlan: (id:string|undefined, input:PlanInput) => request<Plan>(id ? `/api/saas/plans/${id}` : "/api/saas/plans", json(id ? "PUT" : "POST", input)),
  billing: () => request<Billing>("/api/saas/billing"),
  saveIssuer: (input:IssuerInput) => request<Billing>("/api/saas/billing/issuer", json("PUT", input)),
  saveCredentials: (input:{ username:string; password:string; keyStore:string; pin:string }) => request<Billing>("/api/saas/billing/credentials", json("PUT", input)),
  testBilling: () => request<Billing>("/api/saas/billing/test", { method: "POST" }),
  environment: (environment:"TEST"|"PROD") => request<Billing>("/api/saas/billing/environment", json("PUT", { environment })),
  summaries: (ids:string[]) => ids.length ? request<Summary[]>(`/api/saas/subscriptions?companyIds=${ids.map(encodeURIComponent).join(",")}`) : Promise.resolve([]),
  detail: (companyId:string) => request<Detail>(`/api/saas/companies/${companyId}/subscription`),
  pay: (companyId:string, input:PaymentInput) => request<Detail>(`/api/saas/companies/${companyId}/payments`, json("POST", input)),
  voidPayment: (companyId:string, paymentId:string, reason:string) => request<Detail>(`/api/saas/companies/${companyId}/payments/${paymentId}/void`, json("POST", { reason })),
  cancel: (companyId:string, reason:string) => request<Detail>(`/api/saas/companies/${companyId}/cancel`, json("POST", { reason })),
  retry: (documentId:string) => request<void>(`/api/saas/documents/${documentId}/retry`, { method: "POST" }),
  documentUrl: (documentId:string, kind:"pdf"|"xml") => `/api/saas/documents/${documentId}/${kind}`,
  mine: () => request<Mine|undefined>("/api/subscription"),
  myDocumentUrl: (documentId:string, kind:"pdf"|"xml") => `/api/subscription/documents/${documentId}/${kind}`,
};

/** Descarga un PDF o XML con la sesión actual; no descarga nada si falla. */
export async function download(url:string) {
  let response:Response;
  try { response = await fetch(url, { credentials: "same-origin", cache: "no-store" }); }
  catch { throw new ApiError(0, "NETWORK", "No se pudo descargar. Revisa la conexión."); }
  if (!response.ok) { const body = await response.json().catch(() => ({})); throw new ApiError(response.status, body.code ?? "DOWNLOAD_FAILED", body.message ?? "No se pudo descargar."); }
  const name = /filename="?([^";]+)"?/.exec(response.headers.get("Content-Disposition") ?? "")?.[1] ?? "comprobante";
  const href = URL.createObjectURL(await response.blob()); const a = document.createElement("a"); a.href = href; a.download = name;
  document.body.append(a); a.click(); a.remove(); window.setTimeout(() => URL.revokeObjectURL(href), 1000);
}

export const stateLabel:Record<SubscriptionState, string> = { TRIAL: "Prueba", ACTIVE: "Activa", GRACE: "Vencida en gracia",
  SUSPENDED: "Suspendida por falta de pago", CANCELLED: "Cancelada" };
export const methodLabel:Record<Payment["method"], string> = { SINPE: "SINPE Móvil", TRANSFER: "Transferencia", CASH: "Efectivo" };
export const crc = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC", minimumFractionDigits: 0, maximumFractionDigits: 2 });
export const day = (iso:string|null|undefined) => iso ? new Intl.DateTimeFormat("es-CR", { day: "2-digit", month: "2-digit", year: "numeric", timeZone: "UTC" })
  .format(new Date(iso.slice(0, 10) + "T00:00:00Z")) : "—";
export const cycleLabel = (c:string|null|undefined) => c === "ANNUAL" ? "anual" : c === "MONTHLY" ? "mensual" : "";
/** Base e IVA (13 %) de un monto con IVA incluido, como en la factura: base = total ÷ 1,13 a céntimos. */
export function taxBreakdown(total:number) {
  const base = Math.round(total * 100 / 1.13) / 100;
  return { base, tax: Math.round((total - base) * 100) / 100 };
}
/** Fin del período en el cliente (solo vista previa; el servidor calcula el definitivo). */
export function addPeriods(start:string, cycle:"MONTHLY"|"ANNUAL", periods:number) {
  const [y, m, d] = start.split("-").map(Number);
  const months = cycle === "ANNUAL" ? 12 * periods : periods;
  const target = new Date(Date.UTC(y, m - 1 + months, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(d, last));
  return target.toISOString().slice(0, 10);
}

import { ApiError, request } from "../../lib/http";

// STK-017 · Contratos de /api/reports (montos y cantidades llegan como números JSON).
export type ReportTab = "summary" | "products" | "payments" | "sellers" | "idle";
export type ReportContext = { companyName:string; branchId:string|null; branchName:string|null; from:string; to:string;
  previousFrom:string; previousTo:string; grouping:"DAY"|"MONTH"; branches:{ id:string; name:string }[] };
export type Kpis = { gross:number; returns:number; laterVoids:number; net:number; sales:number; average:number; sameDayVoids:number; sameDayVoidAmount:number };
export type Bucket = { start:string; sales:number; gross:number; returns:number; voids:number; net:number };
export type ProductRow = { itemId:string; productName:string; variantName:string|null; sku:string; category:string; unit:string;
  quantity:number; amount:number; returned:number; share:number };
export type CategoryRow = { category:string; products:number; amount:number; share:number };
export type Summary = { context:ReportContext; current:Kpis; previous:Kpis; series:Bucket[]; top:ProductRow[] };
export type Products = { context:ReportContext; gross:number; returned:number; categories:CategoryRow[]; products:ProductRow[]; categoryOptions:string[] };
export type PaymentRow = { method:string; label:string; payments:number; collected:number; refunded:number; net:number };
export type Payments = { context:ReportContext; methods:PaymentRow[]; total:PaymentRow; unpaidSales:number; unpaidAmount:number; netSales:number };
export type SellerRow = { sellerId:string; name:string; sales:number; gross:number; returns:number; voids:number; net:number; average:number };
export type Sellers = { context:ReportContext; rows:SellerRow[]; total:SellerRow };
export type IdleRow = { productId:string; productName:string; variantName:string|null; sku:string; category:string; unit:string;
  branchId:string; branchName:string; available:number; lastSale:string|null };
export type Idle = { context:ReportContext; rows:IdleRow[] };
export type ReportData = { summary:Summary; products:Products; payments:Payments; sellers:Sellers; idle:Idle };

export const reportsApi = {
  load:<T extends ReportTab>(tab:T, params:URLSearchParams) => request<ReportData[T]>(`/api/reports/${tab}?${params}`),
  /** Descarga el archivo generado por el servidor; no descarga nada si la respuesta falla. */
  async download(params:URLSearchParams):Promise<string> {
    let response:Response;
    try { response = await fetch(`/api/reports/export?${params}`, { credentials: "same-origin", cache: "no-store" }); }
    catch { throw new ApiError(0, "NETWORK", "No pudimos generar el archivo. Revisa la conexión e intenta de nuevo."); }
    if (!response.ok) { const body = await response.json().catch(() => ({})); throw new ApiError(response.status, body.code ?? "EXPORT_FAILED", body.message ?? "No pudimos generar el archivo."); }
    const blob = await response.blob();
    const name = /filename="?([^";]+)"?/.exec(response.headers.get("Content-Disposition") ?? "")?.[1] ?? `reportes.${params.get("format")}`;
    const url = URL.createObjectURL(blob); const a = document.createElement("a"); a.href = url; a.download = name; document.body.append(a); a.click(); a.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    return name;
  },
};

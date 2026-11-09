import { request } from "../../lib/http";

export type Branch = {
  id: string; name: string; code: string; address: string; phone: string;
  contactEmail: string; managerName: string; active: boolean;
};
export type BranchInput = Omit<Branch,"id"|"active">;
export type BranchAccess = { userId:string; fullName:string; email:string; role:string; selected:boolean };
export type StockItem = {
  productId:string; productName:string; category:string|null; itemId:string; variantName:string|null;
  sku:string; barcode:string|null; unit:string; branchId:string; branchName:string;
  onHand:number; reserved:number; available:number; minimumQuantity:number|null; lowThreshold:number;
  recoveryMargin:number; exhaustedBasis:"AVAILABLE"|"ON_HAND"; state:"AVAILABLE"|"LOW"|"MINIMUM"|"EXHAUSTED";
  targetQuantity:number|null; restockPaused:boolean;
};
export type PendingItem = { productId:string; productName:string; category:string|null; itemId:string; variantName:string|null; sku:string; unit:string };
export type StockAlert = {
  id:string; type:"MINIMUM"|"EXHAUSTED"; productId:string; productName:string; variantName:string|null;
  sku:string; unit:string; branchId:string; branchName:string; available:number; minimumQuantity:number|null; lastNotifiedAt:string|null;
};
export type ThresholdInput = { minimumQuantity:number|null; lowThreshold:number; recoveryMargin:number; exhaustedBasis:"AVAILABLE"|"ON_HAND";
  targetQuantity?:number|null; restockPaused?:boolean };
/** STK-016 · Por reponer. */
export type RestockRow = {
  productId:string; productName:string; category:string|null; itemId:string; variantName:string|null; sku:string; unit:string;
  branchId:string; branchName:string; state:"EXHAUSTED"|"MINIMUM"|"LOW"; available:number; minimumQuantity:number|null;
  recoveryMargin:number; targetQuantity:number|null; toOrder:number|null; basis:"TARGET"|"RECOVERY"|"NONE";
};
export type PausedRow = { productId:string; productName:string; itemId:string; variantName:string|null; sku:string; unit:string;
  branchId:string; branchName:string; available:number; pausedBy:string|null; pausedAt:string|null };
export type Restock = { companyName:string; rows:RestockRow[]; totals:{exhausted:number;minimum:number;low:number}; paused:PausedRow[] };
export const inventoryApi = {
  branches:(includeInactive=false)=>request<Branch[]>(`/api/inventory/branches?includeInactive=${includeInactive}`),
  createBranch:(input:BranchInput)=>request<Branch>("/api/inventory/branches",{method:"POST",body:JSON.stringify(input)}),
  updateBranch:(id:string,input:BranchInput)=>request<Branch>(`/api/inventory/branches/${id}`,{method:"PUT",body:JSON.stringify(input)}),
  deactivateBranch:(id:string)=>request<void>(`/api/inventory/branches/${id}/deactivate`,{method:"POST"}),
  activateBranch:(id:string)=>request<void>(`/api/inventory/branches/${id}/activate`,{method:"POST"}),
  deleteBranch:(id:string)=>request<void>(`/api/inventory/branches/${id}`,{method:"DELETE"}),
  branchAccess:(id:string)=>request<BranchAccess[]>(`/api/inventory/branches/${id}/access`),
  setBranchAccess:(id:string,userIds:string[])=>request<void>(`/api/inventory/branches/${id}/access`,{method:"PUT",body:JSON.stringify({userIds})}),
  items:(params:URLSearchParams)=>request<StockItem[]>(`/api/inventory/items?${params}`),
  pendingItems:()=>request<PendingItem[]>("/api/inventory/pending-items"),
  assignItem:(branchId:string,input:ThresholdInput&{itemId:string})=>request<void>(`/api/inventory/branches/${branchId}/items`,{method:"POST",body:JSON.stringify(input)}),
  updateThresholds:(branchId:string,itemId:string,input:ThresholdInput)=>request<void>(`/api/inventory/branches/${branchId}/items/${itemId}/thresholds`,{method:"PUT",body:JSON.stringify(input)}),
  unassignItem:(branchId:string,itemId:string)=>request<void>(`/api/inventory/branches/${branchId}/items/${itemId}`,{method:"DELETE"}),
  alerts:()=>request<StockAlert[]>("/api/inventory/alerts"),
  restock:(params:URLSearchParams)=>request<Restock>(`/api/inventory/restock?${params}`),
  restockCount:()=>request<{count:number}>("/api/inventory/restock/count"),
  dismissAlert:(id:string)=>request<void>(`/api/inventory/alerts/${id}/dismiss`,{method:"POST"}),
};

import { request } from "../../lib/http";

export type Worker = { id:string; fullName:string; username:string; email:string; active:boolean; roleId:string; roleName:string };
export type CreatedWorker = { worker:Worker; temporaryPassword:string; temporaryPasswordExpiresAt:string };
export type Role = { id:string; name:string; modules:Record<string,string>; specialPermissions:string[]; assignedWorkers:number };
export type Permission = { key:string; label:string };
export type SessionPolicy = { idleTimeoutMinutes:number|null };
export type AuditItem = { id:string; actorId:string; actorName:string; action:string; actionLabel:string; targetType:string; targetId:string; targetLabel:string; occurredAt:string };
export type AuditPage = { items:AuditItem[]; total:number; page:number; size:number };
export const identityApi = {
  users:()=>request<Worker[]>("/api/users"),
  createUser:(input:Pick<Worker,"fullName"|"username"|"email"|"roleId">)=>request<CreatedWorker>("/api/users",{method:"POST",body:JSON.stringify(input)}),
  deactivateUser:(id:string)=>request<void>(`/api/users/${id}/deactivate`,{method:"POST"}),
  assignRole:(id:string,roleId:string)=>request<Worker>(`/api/users/${id}/role`,{method:"PATCH",body:JSON.stringify({roleId})}),
  issueTempPassword:(id:string)=>request<CreatedWorker>(`/api/users/${id}/temporary-password`,{method:"POST"}),
  roles:()=>request<Role[]>("/api/roles"),
  createRole:(input:Pick<Role,"name"|"modules"|"specialPermissions">)=>request<Role>("/api/roles",{method:"POST",body:JSON.stringify(input)}),
  updateRole:(id:string,input:Pick<Role,"name"|"modules"|"specialPermissions">)=>request<Role>(`/api/roles/${id}`,{method:"PATCH",body:JSON.stringify(input)}),
  deleteRole:(id:string)=>request<void>(`/api/roles/${id}`,{method:"DELETE"}),
  modules:()=>request<Permission[]>("/api/permissions/modules"),
  special:()=>request<Permission[]>("/api/permissions/special"),
  sessionPolicy:()=>request<SessionPolicy>("/api/company/security/session-policy"),
  saveSessionPolicy:(idleTimeoutMinutes:number|null)=>request<SessionPolicy>("/api/company/security/session-policy",{method:"PUT",body:JSON.stringify({idleTimeoutMinutes})}),
  audit:(action:string,page:number)=>{const p=new URLSearchParams({page:String(page),size:"20"});if(action)p.set("action",action);return request<AuditPage>(`/api/audit?${p}`);},
};

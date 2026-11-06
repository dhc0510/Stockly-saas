import type { IncomingMessage, ServerResponse } from 'node:http'
import { randomBytes, randomUUID } from 'node:crypto'

const companyId = 'demo-company-stockly'
const admin = { id: 'demo-company-admin', fullName: 'Ana Administradora Demo', username: 'admin-demo', email: 'admin@stockly-demo.local', role: 'COMPANY_ADMIN', mustChangePassword: false, companyId, modules: [], specialPermissions: [] }
type Worker = { id: string; fullName: string; username: string; email: string; active: boolean; roleId: string; roleName: string }
type Role = { id: string; name: string; modules: Record<string, string>; specialPermissions: string[]; assignedWorkers: number }
type Audit = { id: string; actorId: string; actorName: string; action: string; actionLabel: string; targetType: string; targetId: string; targetLabel: string; occurredAt: string }
let roles: Role[] = [{ id: 'demo-role-operator', name: 'Operador de prueba', modules: {}, specialPermissions: [], assignedWorkers: 2 }]
let workers: Worker[] = [
  { id: 'demo-worker-1', fullName: 'Luis Trabajador Demo', username: 'luis-demo', email: 'luis@stockly-demo.local', active: true, roleId: roles[0].id, roleName: roles[0].name },
  { id: 'demo-worker-2', fullName: 'Marta Colaboradora Demo', username: 'marta-demo', email: 'marta@stockly-demo.local', active: true, roleId: roles[0].id, roleName: roles[0].name },
]
let idleTimeoutMinutes: number | null = 120
let audits: Audit[] = []
const special = [
  { key: 'MANAGE_USERS', label: 'Gestionar usuarios' },
  { key: 'MANAGE_PERMISSIONS', label: 'Gestionar permisos' },
  { key: 'IRREVERSIBLE_ACTIONS', label: 'Operaciones irreversibles' },
]
const send = (res: ServerResponse, status: number, data?: unknown) => { res.statusCode=status; res.setHeader('Cache-Control','no-store'); res.setHeader('Content-Type','application/json; charset=utf-8'); res.end(status===204?undefined:JSON.stringify(data)) }
const fail = (res: ServerResponse, status: number, code: string, message: string) => send(res,status,{code,message,fieldErrors:{},requestId:randomUUID()})
const read = async (req: IncomingMessage) => { let raw=''; for await(const c of req){raw+=c.toString();if(Buffer.byteLength(raw)>32768)throw new Error('too_large')} return raw?JSON.parse(raw):{} }
function isLoggedIn(req:IncomingMessage){ return (req.headers.cookie||'').split(';').some(v=>v.trim()==='stockly_stk005_demo=company-admin') }
function addAudit(action:string,label:string,targetType:string,targetId:string,targetLabel:string){ audits.unshift({id:randomUUID(),actorId:admin.id,actorName:admin.fullName,action,actionLabel:label,targetType,targetId,targetLabel,occurredAt:new Date().toISOString()}) }
function roleCounts(){for(const role of roles)role.assignedWorkers=workers.filter(w=>w.active&&w.roleId===role.id).length}

/** Local-only STK-005 preview. Vite installs this middleware only in stk005-demo mode. */
export function stk005DemoMiddleware(){
  return async(req:IncomingMessage,res:ServerResponse,next:()=>void)=>{
    const url=new URL(req.url||'/', 'http://localhost'); if(!url.pathname.startsWith('/api/')){next();return}
    const method=req.method||'GET', path=url.pathname
    if(path==='/api/auth/csrf'&&method==='GET'){send(res,200,{csrfToken:'stk005-demo-only',csrfHeaderName:'X-CSRF-TOKEN'});return}
    if(path==='/api/auth/login'&&method==='POST'){
      const body=await read(req)
      if(!['admin@stockly-demo.local','admin-demo'].includes(String(body.identifier||'').toLowerCase())||body.password!=='Stockly-Demo-2026!'){fail(res,401,'INVALID_CREDENTIALS','No se pudo iniciar sesión. Revisa las credenciales o recupera el acceso.');return}
      res.setHeader('Set-Cookie','stockly_stk005_demo=company-admin; Path=/; HttpOnly; SameSite=Lax');send(res,200,{status:'AUTHENTICATED',challengeId:null,message:'Acceso de demostración iniciado.'});return
    }
    if(path==='/api/auth/me'&&method==='GET'){if(!isLoggedIn(req)){fail(res,401,'UNAUTHENTICATED','Inicia sesión para continuar.');return}send(res,200,admin);return}
    if(path==='/api/auth/logout'&&method==='POST'){res.setHeader('Set-Cookie','stockly_stk005_demo=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0');send(res,204);return}
    if(!isLoggedIn(req)){fail(res,401,'UNAUTHENTICATED','Inicia sesión para continuar.');return}
    if(path==='/api/users'&&method==='GET'){roleCounts();send(res,200,workers);return}
    if(path==='/api/users'&&method==='POST'){
      const b=await read(req), role=roles.find(x=>x.id===b.roleId);if(!role){fail(res,400,'INVALID_ROLE','Selecciona un rol disponible.');return}
      const worker:Worker={id:randomUUID(),fullName:String(b.fullName).trim(),username:String(b.username).trim().toLowerCase(),email:String(b.email).trim().toLowerCase(),active:true,roleId:role.id,roleName:role.name};workers.push(worker);roleCounts();const password=`Demo-${randomBytes(12).toString('base64url')}!`;addAudit('USER_CREATED','Trabajador creado','USER',worker.id,worker.fullName);send(res,201,{worker,temporaryPassword:password,temporaryPasswordExpiresAt:new Date(Date.now()+86400000).toISOString()});return
    }
    const userMatch=path.match(/^\/api\/users\/([^/]+)\/(deactivate|role|temporary-password)$/)
    if(userMatch){const [,id,action]=userMatch,worker=workers.find(x=>x.id===id);if(!worker){fail(res,404,'NOT_FOUND','El trabajador no está disponible.');return}
      if(action==='deactivate'&&method==='POST'){worker.active=false;addAudit('USER_DEACTIVATED','Trabajador desactivado','USER',worker.id,worker.fullName);send(res,204);return}
      if(action==='role'&&method==='PATCH'){const b=await read(req),role=roles.find(x=>x.id===b.roleId);if(!role){fail(res,400,'INVALID_ROLE','Selecciona un rol disponible.');return}worker.roleId=role.id;worker.roleName=role.name;roleCounts();addAudit('USER_ROLE_ASSIGNED','Rol de trabajador actualizado','USER',worker.id,worker.fullName);send(res,200,worker);return}
      if(action==='temporary-password'&&method==='POST'){const password=`Demo-${randomBytes(12).toString('base64url')}!`;addAudit('TEMP_PASSWORD_ISSUED','Contraseña temporal emitida','USER',worker.id,worker.fullName);send(res,200,{worker,temporaryPassword:password,temporaryPasswordExpiresAt:new Date(Date.now()+86400000).toISOString()});return}
    }
    if(path==='/api/roles'&&method==='GET'){roleCounts();send(res,200,roles);return}
    if(path==='/api/roles'&&method==='POST'){const b=await read(req),role:Role={id:randomUUID(),name:String(b.name).trim(),modules:b.modules||{},specialPermissions:b.specialPermissions||[],assignedWorkers:0};roles.push(role);addAudit('ROLE_CREATED','Rol creado','ROLE',role.id,role.name);send(res,201,role);return}
    const roleMatch=path.match(/^\/api\/roles\/([^/]+)$/)
    if(roleMatch){const role=roles.find(x=>x.id===roleMatch[1]);if(!role){fail(res,404,'NOT_FOUND','El rol no está disponible.');return}
      if(method==='PATCH'){const b=await read(req);role.name=String(b.name).trim();role.modules=b.modules||{};role.specialPermissions=b.specialPermissions||[];for(const w of workers)if(w.roleId===role.id)w.roleName=role.name;addAudit('ROLE_UPDATED','Rol o permisos actualizados','ROLE',role.id,role.name);send(res,200,role);return}
      if(method==='DELETE'){roleCounts();if(role.assignedWorkers){fail(res,409,'ROLE_ASSIGNED','Reasigna los trabajadores antes de eliminar el rol.');return}roles=roles.filter(x=>x.id!==role.id);addAudit('ROLE_DELETED','Rol eliminado','ROLE',role.id,role.name);send(res,204);return}
    }
    if(path==='/api/permissions/modules'&&method==='GET'){send(res,200,[]);return}
    if(path==='/api/permissions/special'&&method==='GET'){send(res,200,special);return}
    if(path==='/api/company/security/session-policy'&&method==='GET'){send(res,200,{idleTimeoutMinutes});return}
    if(path==='/api/company/security/session-policy'&&method==='PUT'){const b=await read(req);idleTimeoutMinutes=b.idleTimeoutMinutes??null;addAudit('SESSION_POLICY_UPDATED','Política de sesión actualizada','COMPANY',companyId,idleTimeoutMinutes===null?'Nunca':`${idleTimeoutMinutes} minutos`);send(res,200,{idleTimeoutMinutes});return}
    if(path==='/api/audit'&&method==='GET'){const action=url.searchParams.get('action')||'',page=Math.max(0,Number(url.searchParams.get('page')||0)),size=Math.max(1,Math.min(100,Number(url.searchParams.get('size')||20))),items=audits.filter(x=>!action||x.action===action);send(res,200,{items:items.slice(page*size,(page+1)*size),total:items.length,page,size});return}
    fail(res,404,'NOT_FOUND','Ruta no disponible en esta simulación local de STK-005.')
  }
}

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
type DemoBranch = { id:string; name:string; code:string; address:string; phone:string; contactEmail:string; managerName:string; active:boolean; userIds:string[] }
type DemoProduct = { id:string; name:string; category:string|null; description:string|null; active:boolean; hasImage:boolean; items:Array<{id:string;variantName:string|null;sku:string;barcode:string|null;unit:string;price:number|null;active:boolean;hasMovements:boolean}> }
type DemoBalance = { branchId:string; itemId:string; onHand:number; reserved:number; minimumQuantity:number|null; lowThreshold:number; recoveryMargin:number; exhaustedBasis:'AVAILABLE'|'ON_HAND'; dismissedBy:Set<string>; lastNotifiedAt:string }
const demoBranches:DemoBranch[]=[
  {id:'branch-sjo',name:'San José Centro',code:'SJO-001',address:'San José, Costa Rica',phone:'2222-0101',contactEmail:'sjo@stockly-demo.local',managerName:'Luis Trabajador Demo',active:true,userIds:[admin.id,'demo-worker-1']},
  {id:'branch-heredia',name:'Heredia',code:'HER-001',address:'Heredia, Costa Rica',phone:'2260-0202',contactEmail:'heredia@stockly-demo.local',managerName:'Marta Colaboradora Demo',active:true,userIds:[admin.id,'demo-worker-2']},
]
const demoProducts:DemoProduct[]=[
  {id:'prod-cafe',name:'Café molido 500 g',category:'Abarrotes',description:'Café de prueba',active:true,hasImage:false,items:[{id:'item-cafe',variantName:null,sku:'CAF-500',barcode:'744100100001',unit:'unidades',price:3200,active:true,hasMovements:true}]},
  {id:'prod-arroz',name:'Arroz 1 kg',category:'Abarrotes',description:'Arroz de prueba',active:true,hasImage:false,items:[{id:'item-arroz',variantName:null,sku:'ARR-001',barcode:'744100100002',unit:'unidades',price:950,active:true,hasMovements:true}]},
  {id:'prod-camiseta',name:'Camiseta básica',category:'Ropa',description:'Variantes de prueba',active:true,hasImage:false,items:[{id:'item-cam-red',variantName:'Roja · S',sku:'CAM-RO-S',barcode:null,unit:'unidades',price:6500,active:true,hasMovements:true},{id:'item-cam-blue',variantName:'Azul · M',sku:'CAM-AZ-M',barcode:null,unit:'unidades',price:6500,active:true,hasMovements:true}]},
  {id:'prod-filtro',name:'Filtro de aceite',category:'Automotriz',description:null,active:true,hasImage:false,items:[{id:'item-filtro',variantName:null,sku:'FIL-010',barcode:null,unit:'unidades',price:4800,active:true,hasMovements:false}]},
]
const demoBalances:DemoBalance[]=[
  {branchId:'branch-sjo',itemId:'item-cafe',onHand:13.875,reserved:4,minimumQuantity:10,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
  {branchId:'branch-heredia',itemId:'item-cafe',onHand:30,reserved:8,minimumQuantity:15,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
  {branchId:'branch-sjo',itemId:'item-arroz',onHand:7,reserved:1,minimumQuantity:5,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
  {branchId:'branch-heredia',itemId:'item-arroz',onHand:11,reserved:0,minimumQuantity:5,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
  {branchId:'branch-sjo',itemId:'item-cam-red',onHand:17,reserved:2,minimumQuantity:10,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
  {branchId:'branch-heredia',itemId:'item-cam-red',onHand:10,reserved:2,minimumQuantity:6,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
  {branchId:'branch-sjo',itemId:'item-cam-blue',onHand:0,reserved:0,minimumQuantity:6,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
  {branchId:'branch-heredia',itemId:'item-cam-blue',onHand:0,reserved:0,minimumQuantity:6,lowThreshold:2,recoveryMargin:1,exhaustedBasis:'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()},
]
const demoAlertsHidden=new Set<string>()
function allDemoItems(){return demoProducts.flatMap(p=>p.items.map(i=>({product:p,item:i})))}
function demoStockRows(){return demoBalances.flatMap(balance=>{const entry=allDemoItems().find(v=>v.item.id===balance.itemId),branch=demoBranches.find(v=>v.id===balance.branchId);if(!entry||!branch)return[];const available=balance.onHand-balance.reserved, basis=balance.exhaustedBasis==='AVAILABLE'?available:balance.onHand, state=basis<=0?'EXHAUSTED':balance.minimumQuantity!=null&&available<=balance.minimumQuantity?'MINIMUM':balance.minimumQuantity!=null&&available<=balance.minimumQuantity+balance.lowThreshold?'LOW':'AVAILABLE';return[{productId:entry.product.id,productName:entry.product.name,category:entry.product.category,itemId:entry.item.id,variantName:entry.item.variantName,sku:entry.item.sku,barcode:entry.item.barcode,unit:entry.item.unit,branchId:branch.id,branchName:branch.name,onHand:balance.onHand,reserved:balance.reserved,available,minimumQuantity:balance.minimumQuantity,lowThreshold:balance.lowThreshold,recoveryMargin:balance.recoveryMargin,exhaustedBasis:balance.exhaustedBasis,state}]} )}
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
export function stk005DemoMiddleware(includeInventory=false){
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
    if(includeInventory&&path==='/api/inventory/location-setup'&&method==='GET'){send(res,200,{branches:[],needsPrincipal:false,pending:false});return}
    if(includeInventory&&path==='/api/inventory/branches'&&method==='GET'){send(res,200,demoBranches.filter(b=>url.searchParams.get('includeInactive')==='true'||b.active).map(({userIds,...b})=>b));return}
    if(includeInventory&&path==='/api/inventory/branches'&&method==='POST'){
      const b=await read(req),branch:DemoBranch={id:randomUUID(),name:String(b.name||'').trim(),code:String(b.code||'').trim(),address:String(b.address||''),phone:String(b.phone||''),contactEmail:String(b.contactEmail||''),managerName:String(b.managerName||''),active:true,userIds:[admin.id]};
      if(!branch.name||!branch.code||demoBranches.some(v=>v.code.toLowerCase()===branch.code.toLowerCase())){fail(res,400,'INVALID_BRANCH','Ingresa nombre y código único.');return}demoBranches.push(branch);send(res,201,(({userIds,...v})=>v)(branch));return
    }
    const branchMatch=includeInventory&&path.match(/^\/api\/inventory\/branches\/([^/]+)(?:\/(access|items|deactivate|activate))?$/)
    if(branchMatch){const [,branchId,action]=branchMatch,branch=demoBranches.find(v=>v.id===branchId);if(!branch){fail(res,404,'NOT_FOUND','Sucursal no disponible.');return}
      if(!action&&method==='PUT'){const b=await read(req);Object.assign(branch,{name:b.name,code:b.code,address:b.address,phone:b.phone,contactEmail:b.contactEmail,managerName:b.managerName});send(res,200,(({userIds,...v})=>v)(branch));return}
      if(!action&&method==='DELETE'){if(demoBalances.some(v=>v.branchId===branchId)){fail(res,409,'BRANCH_HAS_ITEMS','Quita los productos antes de borrar la sucursal.');return}demoBranches.splice(demoBranches.indexOf(branch),1);send(res,204);return}
      if(action==='deactivate'&&method==='POST'){branch.active=false;send(res,204);return}if(action==='activate'&&method==='POST'){branch.active=true;send(res,204);return}
      if(action==='access'&&method==='GET'){send(res,200,workers.map(w=>({userId:w.id,fullName:w.fullName,email:w.email,role:'WORKER',selected:branch.userIds.includes(w.id)})).concat([{userId:admin.id,fullName:admin.fullName,email:admin.email,role:'COMPANY_ADMIN',selected:true}]));return}
      if(action==='access'&&method==='PUT'){const b=await read(req);branch.userIds=[admin.id,...(Array.isArray(b.userIds)?b.userIds.filter((id:string)=>workers.some(w=>w.id===id)):[])];send(res,204);return}
      if(action==='items'&&method==='POST'){const b=await read(req);if(!allDemoItems().some(v=>v.item.id===b.itemId)){fail(res,404,'ITEM_NOT_FOUND','Producto no encontrado.');return}if(demoBalances.some(v=>v.branchId===branchId&&v.itemId===b.itemId)){fail(res,409,'ALREADY_ASSIGNED','Ya está asignado.');return}demoBalances.push({branchId,itemId:String(b.itemId),onHand:0,reserved:0,minimumQuantity:b.minimumQuantity??null,lowThreshold:Number(b.lowThreshold??2),recoveryMargin:Number(b.recoveryMargin??1),exhaustedBasis:b.exhaustedBasis==='ON_HAND'?'ON_HAND':'AVAILABLE',dismissedBy:new Set(),lastNotifiedAt:new Date().toISOString()});send(res,201);return}
    }
    const branchItemMatch=includeInventory&&path.match(/^\/api\/inventory\/branches\/([^/]+)\/items\/([^/]+)(?:\/(thresholds))?$/)
    if(branchItemMatch){const [,branchId,itemId,action]=branchItemMatch,index=demoBalances.findIndex(v=>v.branchId===branchId&&v.itemId===itemId);if(index<0){fail(res,404,'NOT_FOUND','Producto no asignado a esta sucursal.');return}if(action==='thresholds'&&method==='PUT'){const b=await read(req);Object.assign(demoBalances[index],{minimumQuantity:b.minimumQuantity??null,lowThreshold:Number(b.lowThreshold),recoveryMargin:Number(b.recoveryMargin),exhaustedBasis:b.exhaustedBasis});send(res,204);return}if(!action&&method==='DELETE'){demoBalances.splice(index,1);send(res,204);return}}
    if(includeInventory&&path==='/api/inventory/items'&&method==='GET'){send(res,200,demoStockRows());return}
    if(includeInventory&&path==='/api/inventory/pending-items'&&method==='GET'){const assigned=new Set(demoBalances.map(v=>v.itemId));send(res,200,allDemoItems().filter(v=>v.item.active&&!assigned.has(v.item.id)).map(v=>({productId:v.product.id,productName:v.product.name,category:v.product.category,itemId:v.item.id,variantName:v.item.variantName,sku:v.item.sku,unit:v.item.unit})));return}
    if(includeInventory&&path==='/api/inventory/alerts'&&method==='GET'){
      const hiddenKey=(b:DemoBalance)=>`${b.branchId}:${b.itemId}`;const alerts=demoBalances.flatMap(b=>{const entry=allDemoItems().find(v=>v.item.id===b.itemId),branch=demoBranches.find(v=>v.id===b.branchId),available=b.onHand-b.reserved;if(!entry||!branch||demoAlertsHidden.has(`${hiddenKey(b)}:${admin.id}`))return[];const basis=b.exhaustedBasis==='AVAILABLE'?available:b.onHand,type=basis<=0?'EXHAUSTED':b.minimumQuantity!=null&&available<=b.minimumQuantity?'MINIMUM':null;if(!type)return[];return[{id:`${b.branchId}:${b.itemId}`,type,productId:entry.product.id,productName:entry.product.name,variantName:entry.item.variantName,sku:entry.item.sku,unit:entry.item.unit,branchId:branch.id,branchName:branch.name,available,minimumQuantity:b.minimumQuantity,lastNotifiedAt:b.lastNotifiedAt}]});send(res,200,alerts);return
    }
    const alertMatch=includeInventory&&path.match(/^\/api\/inventory\/alerts\/([^/]+)\/dismiss$/);if(alertMatch&&method==='POST'){demoAlertsHidden.add(`${alertMatch[1]}:${admin.id}`);send(res,204);return}
    if(includeInventory&&path==='/api/catalog'&&method==='GET'){const term=(url.searchParams.get('search')||'').toLowerCase(),active=url.searchParams.get('active'),category=url.searchParams.get('category')||'';const products=demoProducts.filter(p=>(active==null||String(p.active)===active)&&(!category||p.category===category)&&(!term||`${p.name} ${p.category||''} ${p.items.map(i=>i.sku).join(' ')}`.toLowerCase().includes(term)));send(res,200,{products,categories:[...new Set(demoProducts.map(p=>p.category).filter(Boolean))],hasMore:false});return}
    if(includeInventory&&path==='/api/catalog/products'&&method==='POST'){
      const b=await read(req),product:DemoProduct={id:randomUUID(),name:String(b.name||'Producto de prueba'),category:String(b.category||'').trim()||null,description:String(b.description||'').trim()||null,active:true,hasImage:false,items:[]};const make=(value:any):DemoProduct['items'][number]=>({id:randomUUID(),variantName:value?.variantName||null,sku:String(value?.sku||`DEMO-${randomBytes(3).toString('hex').toUpperCase()}`),barcode:value?.barcode||null,unit:String(value?.unit||'unidades'),price:value?.price==null?null:Number(value.price),active:true,hasMovements:false});if(b.item)product.items.push(make(b.item));if(Array.isArray(b.variants))for(const v of b.variants)product.items.push(make(v));if(!product.items.length)product.items.push(make({}));demoProducts.unshift(product);send(res,201,product);return
    }
    if(includeInventory&&path==='/api/permissions/modules'&&method==='GET'){send(res,200,[{key:'CATALOG',label:'Catálogo'},{key:'INVENTORY',label:'Existencias'}]);return}
    if(path==='/api/permissions/special'&&method==='GET'){send(res,200,special);return}
    if(path==='/api/company/security/session-policy'&&method==='GET'){send(res,200,{idleTimeoutMinutes});return}
    if(path==='/api/company/security/session-policy'&&method==='PUT'){const b=await read(req);idleTimeoutMinutes=b.idleTimeoutMinutes??null;addAudit('SESSION_POLICY_UPDATED','Política de sesión actualizada','COMPANY',companyId,idleTimeoutMinutes===null?'Nunca':`${idleTimeoutMinutes} minutos`);send(res,200,{idleTimeoutMinutes});return}
    if(path==='/api/audit'&&method==='GET'){const action=url.searchParams.get('action')||'',page=Math.max(0,Number(url.searchParams.get('page')||0)),size=Math.max(1,Math.min(100,Number(url.searchParams.get('size')||20))),items=audits.filter(x=>!action||x.action===action);send(res,200,{items:items.slice(page*size,(page+1)*size),total:items.length,page,size});return}
    fail(res,404,'NOT_FOUND','Ruta no disponible en esta simulación local de STK-005.')
  }
}

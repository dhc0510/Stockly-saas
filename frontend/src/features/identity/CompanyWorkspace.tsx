import { SalesPage } from "../sales/SalesPage";
import { PaymentMethodsPage, PaymentSummaryPage } from "../sales/PaymentPages";
import { BillingPage } from "../billing/BillingPage";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, NavLink, Navigate, Route, Routes, useNavigate } from "react-router-dom";
import { KeyRound, LogOut, Shield, Users, Settings2, UserRound, CheckCircle2, History, Menu, Package, PackageMinus, PackagePlus, X, Boxes, MapPin, ShoppingCart, BarChart3, Wallet, FileText } from "lucide-react";
import { CatalogPage, catalogAccess } from "../catalog/CatalogPage";
import { ReceiptsPage, receiptAccess } from "../receipts/ReceiptsPage";
import { MovementsPage, movementAccess } from "../movements/MovementsPage";
import { HistoryPage, historyAccess } from "../history/HistoryPage";
import { InventoryPage, BranchesPage, InventoryBell } from "../inventory/InventoryPages";
import { RestockNavLink, RestockPage } from "../inventory/RestockPage";
import { useAuth } from "../auth/AuthContext";
import { identityApi, type AuditPage, type CreatedWorker, type Permission, type Role, type SessionPolicy, type Worker } from "./api";

function Failure({error}:{error:unknown}) { return error?<div className="notice error" role="alert">{error instanceof Error?error.message:"No se pudo completar la operación."}</div>:null; }
function Busy() { return <div className="state-panel" role="status">Cargando…</div>; }

export function CompanyWorkspace() {
  const {user,logout}=useAuth();const navigate=useNavigate();
  const [menuOpen,setMenuOpen]=useState(false);
  const menuToggle = useRef<HTMLButtonElement>(null);
  const drawerClose = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const mobile = window.matchMedia("(max-width: 640px)");
    if (!mobile.matches) {
      setMenuOpen(false);
      return;
    }

    const scrollY = window.scrollY;
    const body = document.body.style;
    const previous = { position: body.position, top: body.top, width: body.width };
    Object.assign(body, { position: "fixed", top: `-${scrollY}px`, width: "100%" });
    drawerClose.current?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") setMenuOpen(false);
    };
    const onResize = (event: MediaQueryListEvent) => {
      if (!event.matches) setMenuOpen(false);
    };
    window.addEventListener("keydown", onKeyDown);
    mobile.addEventListener("change", onResize);

    return () => {
      window.removeEventListener("keydown", onKeyDown);
      mobile.removeEventListener("change", onResize);
      Object.assign(body, previous);
      window.scrollTo({ top: scrollY, behavior: "instant" });
      if (mobile.matches && !menuToggle.current?.closest("[inert]")) {
        menuToggle.current?.focus({ preventScroll: true });
      }
    };
  }, [menuOpen]);
  if(!user) return <Navigate to="/login" replace/>;
  const canUsers=user.role==="COMPANY_ADMIN"||user.specialPermissions.includes("MANAGE_USERS");
  const canRoles=user.role==="COMPANY_ADMIN"||user.specialPermissions.includes("MANAGE_PERMISSIONS");
  const canPolicy=user.role==="COMPANY_ADMIN";
  const catalog=catalogAccess(user);
  const canCatalog=catalog.read && import.meta.env.MODE !== "stk005-demo";
  const inventoryLevel=user.modules.find(module=>module.moduleKey==="INVENTORY")?.accessLevel;
  const canInventory=import.meta.env.MODE!=="stk005-demo"&&(user.role==="COMPANY_ADMIN"||!!inventoryLevel&&inventoryLevel!=="NONE"||catalog.edit);
  const canBranches=user.role==="COMPANY_ADMIN";
  const receipts=receiptAccess(user);
  const canReceipts=receipts.read && import.meta.env.MODE !== "stk005-demo";
  const movements=movementAccess(user);
  const canMovements=movements.read && import.meta.env.MODE !== "stk005-demo";
  const canHistory=historyAccess(user) && import.meta.env.MODE !== "stk005-demo";
  const salesLevel = user.modules.find(module => module.moduleKey === "SALES")?.accessLevel;
  const canSales = import.meta.env.MODE !== "stk005-demo" && import.meta.env.MODE !== "stk007-demo" &&
    (user.role === "COMPANY_ADMIN" || !!salesLevel && salesLevel !== "NONE");
  const defaultPath=canUsers?"/users":canRoles?"/roles":canCatalog?"/catalog":canInventory?"/inventory":canReceipts?"/inventory/receipts":canMovements?"/inventory/movements":canHistory?"/inventory/history":canSales?"/sales":"/account";
  const closeMenu=()=>setMenuOpen(false);
  return <div className={`app-shell company-shell${import.meta.env.MODE === "stk005-demo" ? " stk005-demo-shell" : ""}`}>
    <aside className={`sidebar company-sidebar${menuOpen?" sidebar-open":""}`} id="primary-navigation" aria-label="Navegación de empresa"><div className="company-sidebar-heading">
        <Link className="brand" to="/" onClick={closeMenu}><img className="brand-mark" src="/brand/stockly-caja.svg" alt="" width="30" height="30" /><span>Stockly<span className="brand-dot">.</span></span></Link>
        {menuOpen && <button ref={drawerClose} className="company-drawer-close icon-button" type="button"
          aria-label="Cerrar menú" onClick={closeMenu}><X size={20} aria-hidden="true" /></button>}
      </div><nav className="company-sidebar-links" aria-label="Secciones de empresa"><div className="nav-caption">MI EMPRESA</div>
      {canUsers&&<NavLink className="identity-nav" to="/users" onClick={closeMenu}><Users size={18}/> Usuarios</NavLink>}
      {canRoles&&<NavLink className="identity-nav" to="/roles" onClick={closeMenu}><Shield size={18}/> Roles y permisos</NavLink>}
      {canPolicy&&<NavLink className="identity-nav" to="/security" onClick={closeMenu}><Settings2 size={18}/> Seguridad</NavLink>}
      {canPolicy&&<NavLink className="identity-nav" to="/audit" onClick={closeMenu}><Shield size={18}/> Actividad</NavLink>}
      {(canCatalog||canInventory||canBranches||canReceipts||canMovements||canHistory)&&<div className="nav-caption inventory-caption">INVENTARIO</div>}
      {canCatalog&&<NavLink className="identity-nav" to="/catalog" onClick={closeMenu}><Package size={18}/> Catálogo</NavLink>}
      {canInventory&&<NavLink className="identity-nav" to="/inventory" end onClick={closeMenu}><Boxes size={18}/> Existencias</NavLink>}
      {canInventory&&<RestockNavLink onClick={closeMenu}/>}
      {canBranches&&<NavLink className="identity-nav" to="/branches" onClick={closeMenu}><MapPin size={18}/> Sucursales</NavLink>}
      {canReceipts&&<NavLink className="identity-nav" to="/inventory/receipts" onClick={closeMenu}><PackagePlus size={18}/> Entradas de inventario</NavLink>}
      {canMovements&&<NavLink className="identity-nav" to="/inventory/movements" onClick={closeMenu}><PackageMinus size={18}/> Salidas y ajustes</NavLink>}
      {canHistory&&<NavLink className="identity-nav" to="/inventory/history" onClick={closeMenu}><History size={18}/> Historial</NavLink>}
      {canSales && <><div className="nav-caption inventory-caption">OPERACIÓN COMERCIAL</div><NavLink className="identity-nav" to="/sales" onClick={closeMenu}><ShoppingCart size={18}/> Ventas</NavLink><NavLink className="identity-nav" to="/payments" end onClick={closeMenu}><BarChart3 size={18}/> Resumen de cobros</NavLink>{user.role === "COMPANY_ADMIN" && <NavLink className="identity-nav" to="/payments/methods" onClick={closeMenu}><Wallet size={18}/> Medios de pago</NavLink>}{user.role === "COMPANY_ADMIN" && <NavLink className="identity-nav" to="/billing" onClick={closeMenu}><FileText size={18}/> Facturación</NavLink>}</>}
      </nav>
      <div className="sidebar-footer"><span className="avatar"><UserRound size={18}/></span><span><strong>{user.fullName}</strong><br/><small>{user.role === "COMPANY_ADMIN" ? "Administración de empresa" : user.email}</small></span></div>
    </aside>
    {menuOpen&&<button className="mobile-drawer-scrim" type="button" tabIndex={-1} aria-label="Cerrar menú" onClick={closeMenu}/>}
    <div className="workspace" inert={menuOpen}>{(import.meta.env.MODE === "stk005-demo"||import.meta.env.MODE === "stk007-demo")&&<div className="demo-notice" role="status"><strong>{import.meta.env.MODE==="stk007-demo"?"Simulación STK-007":"Simulación STK-005"}</strong><span>Cuenta y datos ficticios · Los cambios se reinician al cerrar el servidor de demostración.</span></div>}<header className="topbar"><button ref={menuToggle} className="mobile-menu-toggle" type="button" aria-label={menuOpen?"Cerrar menú":"Abrir menú"} aria-expanded={menuOpen} aria-controls="primary-navigation" onClick={()=>setMenuOpen(v=>!v)}>{menuOpen?<X size={19}/>:<Menu size={19}/>}<span>Menú</span></button><span className="topbar-title"><img className="topbar-mark" src="/brand/stockly-caja.svg" alt="Stockly" width="26" height="26" /><span className="topbar-name">Mi empresa</span></span><div className="company-top-actions">{canInventory&&<InventoryBell/>}<button className="session-logout" onClick={async()=>{await logout();navigate("/login");}}><LogOut size={16}/> Cerrar sesión</button></div></header>
      <main id="main" tabIndex={-1}><Routes>
        {canUsers&&<Route path="/users" element={<UsersPage/>}/>}
        {canRoles&&<Route path="/roles" element={<RolesPage/>}/>}
        {canPolicy&&<Route path="/security" element={<SessionPolicyPage/>}/>}
        {canPolicy&&<Route path="/audit" element={<AuditPageView/>}/>}
        {canCatalog&&<Route path="/catalog" element={<CatalogPage access={catalog}/>}/>}
        {canInventory&&<Route path="/inventory" element={<InventoryPage canEditCatalog={catalog.edit} isAdmin={user.role==="COMPANY_ADMIN"}/>}/>}
        {canInventory&&<Route path="/inventory/restock" element={<RestockPage canEdit={catalog.edit}/>}/>}
        {canBranches&&<Route path="/branches" element={<BranchesPage/>}/>}
        {canReceipts&&<Route path="/inventory/receipts/*" element={<ReceiptsPage user={user} access={receipts}/>}/>}
        {canMovements&&<Route path="/inventory/movements/*" element={<MovementsPage user={user} access={movements}/>}/>}
        {canHistory&&<Route path="/inventory/history/*" element={<HistoryPage/>}/>}
        {canSales && <Route path="/sales/*" element={<SalesPage />} />}
        {canSales && <Route path="/payments" element={<PaymentSummaryPage />} />}
        {canSales && user.role === "COMPANY_ADMIN" && <Route path="/payments/methods" element={<PaymentMethodsPage />} />}
        {canSales && user.role === "COMPANY_ADMIN" && <Route path="/billing" element={<BillingPage />} />}
        <Route path="/account" element={<AccountPage/>}/>
        <Route path="*" element={<Navigate to={defaultPath} replace/>}/>
      </Routes></main>
    </div>
  </div>;
}

function UsersPage() {
  const [users,setUsers]=useState<Worker[]>([]);const [roles,setRoles]=useState<Role[]>([]);const [loading,setLoading]=useState(true);const [error,setError]=useState<unknown>();const [created,setCreated]=useState<CreatedWorker>();const [reload,setReload]=useState(0);const [showForm,setShowForm]=useState(false);const [busy,setBusy]=useState(false);
  const [fullName,setFullName]=useState("");const [username,setUsername]=useState("");const [email,setEmail]=useState("");const [roleId,setRoleId]=useState("");
  useEffect(()=>{let active=true;setLoading(true);Promise.all([identityApi.users(),identityApi.roles()]).then(([a,b])=>{if(active){setUsers(a);setRoles(b);}}).catch(e=>{if(active)setError(e);}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[reload]);
  async function create(event:FormEvent){event.preventDefault();setBusy(true);setError(undefined);try{const value=await identityApi.createUser({fullName,username,email,roleId});setCreated(value);setShowForm(false);setFullName("");setUsername("");setEmail("");setRoleId("");setReload(x=>x+1);}catch(e){setError(e);}finally{setBusy(false);}}
  async function deactivate(user:Worker){if(!window.confirm(`¿Desactivar a ${user.fullName}? Se cerrarán sus sesiones y dispositivos recordados.`))return;setError(undefined);try{await identityApi.deactivateUser(user.id);setReload(x=>x+1);}catch(e){setError(e);}}
  async function issue(user:Worker){if(!window.confirm(`¿Generar una nueva contraseña temporal para ${user.fullName}? Tendrá que cambiarla al ingresar.`))return;setError(undefined);try{setCreated(await identityApi.issueTempPassword(user.id));}catch(e){setError(e);}}
  async function assign(user:Worker,nextRole:string){setError(undefined);try{await identityApi.assignRole(user.id,nextRole);setReload(x=>x+1);}catch(e){setError(e);}}
  return <>
    <div className="page-heading"><div><p className="eyebrow">ACCESO A LA EMPRESA</p><h1>Usuarios</h1><p className="subtitle">Crea trabajadores, asigna su rol y administra su acceso.</p></div><button className="button primary" onClick={()=>setShowForm(!showForm)}><Users size={17}/> Crear trabajador</button></div>
    <Failure error={error}/>
    {created&&<section className="notice success credential-reveal" role="status"><CheckCircle2 size={20}/><div><strong>Contraseña temporal de un solo uso</strong><p>Entrégala ahora a {created.worker.fullName}. No volverá a mostrarse y vence el {new Intl.DateTimeFormat("es-CR",{dateStyle:"medium",timeStyle:"short",timeZone:"America/Costa_Rica"}).format(new Date(created.temporaryPasswordExpiresAt))}.</p><code>{created.temporaryPassword}</code><button className="text-button" onClick={()=>setCreated(undefined)}>Ocultar</button></div></section>}
    {showForm&&<section className="panel form-panel identity-form"><h2>Nuevo trabajador</h2><form className="form-grid" onSubmit={create}>
      <label className="field"><span>Nombre completo</span><input value={fullName} onChange={e=>setFullName(e.target.value)} minLength={2} maxLength={150} required/></label>
      <label className="field"><span>Nombre de usuario</span><input autoComplete="off" value={username} onChange={e=>setUsername(e.target.value)} pattern="[A-Za-z0-9._-]{3,30}" minLength={3} maxLength={30} required/></label>
      <label className="field"><span>Correo electrónico</span><input type="email" autoComplete="off" value={email} onChange={e=>setEmail(e.target.value)} maxLength={254} required/></label>
      <label className="field"><span>Rol de trabajador</span><select value={roleId} onChange={e=>setRoleId(e.target.value)} required><option value="">Selecciona un rol</option>{roles.map(r=><option key={r.id} value={r.id}>{r.name}</option>)}</select></label>
      {!roles.length&&<p className="notice info full-width">Crea primero un rol para asignarlo al trabajador.</p>}
      <div className="form-actions full-width"><button type="button" className="button secondary" onClick={()=>setShowForm(false)}>Cancelar</button><button className="button primary" disabled={busy||!roles.length}>{busy?"Creando…":"Crear trabajador"}</button></div>
    </form></section>}
    {loading?<Busy/>:users.length===0?<div className="empty-state panel"><div className="empty-icon"><Users size={28}/></div><h2>Aún no hay trabajadores</h2><p>Crea un trabajador y asígnale un rol de acceso.</p></div>:<section className="panel list-panel"><div className="identity-table-wrap"><table className="company-table identity-table"><thead><tr><th>Trabajador</th><th>Usuario</th><th>Rol</th><th>Estado</th><th>Acciones</th></tr></thead><tbody>{users.map(u=><tr key={u.id}><td><strong>{u.fullName}</strong><small className="cell-secondary">{u.email}</small></td><td>{u.username}</td><td><select aria-label={`Rol de ${u.fullName}`} value={u.roleId} disabled={!u.active} onChange={e=>assign(u,e.target.value)}>{roles.map(r=><option key={r.id} value={r.id}>{r.name}</option>)}</select></td><td>{u.active?"Activo":"Desactivado"}</td><td className="identity-actions">{u.active&&<><button className="text-button" onClick={()=>issue(u)}>Nueva contraseña</button><button className="text-button danger-text" onClick={()=>deactivate(u)}>Desactivar</button></>}</td></tr>)}</tbody></table></div></section>}
  </>;
}

function RolesPage() {
  const [roles,setRoles]=useState<Role[]>([]);const [modules,setModules]=useState<Permission[]>([]);const [special,setSpecial]=useState<Permission[]>([]);const [loading,setLoading]=useState(true);const [error,setError]=useState<unknown>();const [editing,setEditing]=useState<Role>();const [showForm,setShowForm]=useState(false);const [name,setName]=useState("");const [levels,setLevels]=useState<Record<string,string>>({});const [selected,setSelected]=useState<string[]>([]);const [busy,setBusy]=useState(false);const [reload,setReload]=useState(0);
  useEffect(()=>{let active=true;setLoading(true);Promise.all([identityApi.roles(),identityApi.modules(),identityApi.special()]).then(([r,m,s])=>{if(active){setRoles(r);setModules(m);setSpecial(s);}}).catch(e=>{if(active)setError(e);}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[reload]);
  function reset(){setEditing(undefined);setShowForm(false);setName("");setLevels({});setSelected([]);}
  function edit(role?:Role){setEditing(role);setShowForm(true);setName(role?.name||"");setLevels(role?.modules||{});setSelected(role?.specialPermissions||[]);}
  async function save(event:FormEvent){event.preventDefault();setBusy(true);setError(undefined);try{const payload={name,modules:levels,specialPermissions:selected};if(editing)await identityApi.updateRole(editing.id,payload);else await identityApi.createRole(payload);reset();setReload(x=>x+1);}catch(e){setError(e);}finally{setBusy(false);}}
  async function remove(role:Role){if(role.assignedWorkers||!window.confirm(`¿Eliminar el rol ${role.name}?`))return;try{await identityApi.deleteRole(role.id);setReload(x=>x+1);}catch(e){setError(e);}}
  function toggle(key:string){setSelected(old=>old.includes(key)?old.filter(x=>x!==key):[...old,key]);}
  return <>
    <div className="page-heading"><div><p className="eyebrow">ACCESO POR MÓDULO</p><h1>Roles y permisos</h1><p className="subtitle">Define un único rol para cada trabajador.</p></div><button className="button primary" onClick={()=>edit()}><Shield size={17}/> Crear rol</button></div>
    <Failure error={error}/>
    {showForm&&<section className="panel form-panel identity-form"><h2>{editing?"Editar rol":"Nuevo rol"}</h2><form className="form-grid single-column" onSubmit={save}>
      <label className="field"><span>Nombre del rol</span><input value={name} onChange={e=>setName(e.target.value)} minLength={2} maxLength={80} required/></label>
      <fieldset className="permission-set"><legend>Permisos por módulo</legend>{modules.length===0?<p className="subtitle">Los módulos aparecerán aquí cuando sus funcionalidades estén aprobadas y registradas.</p>:modules.map(m=><label className="permission-row" key={m.key}><span>{m.label}</span><select value={levels[m.key]||"NONE"} onChange={e=>setLevels(old=>({...old,[m.key]:e.target.value}))}><option value="NONE">Sin acceso</option><option value="READ">Solo lectura</option><option value="OPERATE">Operar</option><option value="ADMIN">Administrar</option></select></label>)}</fieldset>
      <fieldset className="permission-set"><legend>Permisos especiales</legend>{special.map(p=><label className="permission-row" key={p.key}><span>{p.label}</span><input type="checkbox" checked={selected.includes(p.key)} onChange={()=>toggle(p.key)}/></label>)}</fieldset>
      <div className="form-actions"><button type="button" className="button secondary" onClick={reset}>Cancelar</button><button className="button primary" disabled={busy}>{busy?"Guardando…":"Guardar rol"}</button></div>
    </form></section>}
    {loading?<Busy/>:<section className="role-list">{roles.map(r=><article className="panel role-card" key={r.id}><div><h2>{r.name}</h2><p>{r.assignedWorkers} trabajador{r.assignedWorkers===1?"":"es"} asignado{r.assignedWorkers===1?"":"s"}</p></div><div className="form-actions"><button className="button secondary" onClick={()=>edit(r)}>Editar</button><button className="button danger-outline" disabled={r.assignedWorkers>0} onClick={()=>remove(r)}>Eliminar</button></div></article>)}{!roles.length&&<div className="empty-state panel"><h2>Aún no hay roles</h2><p>Crea roles para asignar permisos a los trabajadores.</p></div>}</section>}
  </>;
}

function SessionPolicyPage() {
  const [policy,setPolicy]=useState<SessionPolicy>();const [never,setNever]=useState(false);const [minutes,setMinutes]=useState(120);const [loading,setLoading]=useState(true);const [error,setError]=useState<unknown>();const [saved,setSaved]=useState(false);const [busy,setBusy]=useState(false);
  useEffect(()=>{identityApi.sessionPolicy().then(p=>{setPolicy(p);setNever(p.idleTimeoutMinutes===null);setMinutes(p.idleTimeoutMinutes??120);}).catch(setError).finally(()=>setLoading(false));},[]);
  async function save(event:FormEvent){event.preventDefault();setBusy(true);setError(undefined);setSaved(false);try{const next=await identityApi.saveSessionPolicy(never?null:minutes);setPolicy(next);setSaved(true);}catch(e){setError(e);}finally{setBusy(false);}}
  if(loading)return <Busy/>;
  return <><div className="page-heading"><div><p className="eyebrow">SEGURIDAD DE LA EMPRESA</p><h1>Sesiones</h1><p className="subtitle">Configura cuándo vence una sesión por inactividad.</p></div></div>
    <section className="panel form-panel security-policy"><form className="form-grid single-column" onSubmit={save}>
      <label className="remember-device"><input type="checkbox" checked={never} onChange={e=>setNever(e.target.checked)}/> No cerrar por inactividad</label>
      {!never&&<label className="field"><span>Cerrar después de inactividad</span><div className="time-input"><input type="number" min={30} step={30} value={minutes} onChange={e=>setMinutes(Number(e.target.value))} required/><span>minutos</span></div><small>El valor inicial es 120 minutos. Puedes usar cualquier múltiplo de 30, sin máximo.</small></label>}
      <p className="notice info">«Nunca» permite que la sesión persista entre aperturas del navegador. Cerrar sesión, cambiar credenciales, desactivar usuarios o suspender la empresa sí revoca el acceso.</p>
      <Failure error={error}/>{saved&&<div className="notice success" role="status">Configuración guardada. Las sesiones se actualizarán con esta política.</div>}
      <button className="button primary" disabled={busy}>{busy?"Guardando…":"Guardar cambios"}</button>
    </form></section>
  </>;
}

function AuditPageView() {
  const [data,setData]=useState<AuditPage>();const [action,setAction]=useState("");const [page,setPage]=useState(0);const [loading,setLoading]=useState(true);const [error,setError]=useState<unknown>();
  useEffect(()=>{let active=true;setLoading(true);setError(undefined);identityApi.audit(action,page).then(v=>{if(active)setData(v);}).catch(e=>{if(active)setError(e);}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[action,page]);
  const date=(value:string)=>new Intl.DateTimeFormat("es-CR",{day:"2-digit",month:"short",year:"numeric",hour:"2-digit",minute:"2-digit",timeZone:"America/Costa_Rica"}).format(new Date(value));
  return <><div className="page-heading"><div><p className="eyebrow">SEGURIDAD DE LA EMPRESA</p><h1>Actividad</h1><p className="subtitle">Registro de cambios de usuarios, roles y sesiones.</p></div></div>
    <section className="panel list-panel audit-panel">
      <label className="field audit-filter"><span>Filtrar por acción</span><select value={action} onChange={e=>{setAction(e.target.value);setPage(0);}}><option value="">Todas las acciones</option><option value="USER_CREATED">Trabajador creado</option><option value="USER_DEACTIVATED">Trabajador desactivado</option><option value="USER_ROLE_ASSIGNED">Rol de trabajador actualizado</option><option value="TEMP_PASSWORD_ISSUED">Contraseña temporal emitida</option><option value="ROLE_CREATED">Rol creado</option><option value="ROLE_UPDATED">Rol o permisos actualizados</option><option value="ROLE_DELETED">Rol eliminado</option><option value="SESSION_POLICY_UPDATED">Política de sesión actualizada</option><option value="PAYMENT_METHODS_UPDATED">Medios de pago actualizados</option></select></label>
      <Failure error={error}/>{loading?<Busy/>:!data?.items.length?<div className="empty-state"><h2>Sin actividad para mostrar</h2><p>Los cambios de usuarios, roles y sesiones aparecerán aquí.</p></div>:<>
        <div className="identity-table-wrap audit-desktop"><table className="company-table audit-table"><thead><tr><th>Fecha y hora</th><th>Persona</th><th>Acción</th><th>Objetivo</th></tr></thead><tbody>{data.items.map(e=><tr key={e.id}><td>{date(e.occurredAt)}</td><td>{e.actorName||"Cuenta administradora"}</td><td>{e.actionLabel}</td><td>{e.targetLabel}</td></tr>)}</tbody></table></div>
        <div className="audit-mobile-list">{data.items.map(e=><article className="audit-card" key={e.id}><small>{date(e.occurredAt)}</small><strong>{e.actionLabel}</strong><span>{e.actorName||"Cuenta administradora"} · {e.targetLabel}</span></article>)}</div>
        <footer className="table-footer audit-footer"><span>{data.total} eventos</span><nav className="pagination" aria-label="Paginación de actividad"><button className="icon-button" disabled={!page} aria-label="Página anterior" onClick={()=>setPage(v=>v-1)}>‹</button><span className="page-number" aria-current="page">{page+1}</span><button className="icon-button" disabled={(page+1)*data.size>=data.total} aria-label="Página siguiente" onClick={()=>setPage(v=>v+1)}>›</button></nav></footer>
      </>}
      <p className="audit-note">Los eventos muestran quién hizo cada cambio y cuándo. No incluyen credenciales ni códigos. Solo el administrador de esta empresa puede consultar la actividad.</p>
    </section>
  </>;
}

function AccountPage() {
  const {user}=useAuth();
  return <><div className="page-heading"><div><p className="eyebrow">MI CUENTA</p><h1>Acceso</h1><p className="subtitle">Tu cuenta no tiene módulos operativos asignados.</p></div></div><section className="panel form-panel account-panel"><UserRound size={30}/><h2>{user?.fullName}</h2><p>{user?.email}</p><p>Usuario: {user?.username}</p><p>Rol: Trabajador</p></section></>;
}

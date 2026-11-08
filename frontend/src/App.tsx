import { useEffect, useState } from "react";
import { NavLink, Route, Routes, Navigate, useLocation, useNavigate } from "react-router-dom";
import { Building2, ChevronDown, LogOut, Menu, X } from "lucide-react";
import { CompanyList } from "./features/companies/CompanyList";
import { CompanyDetail } from "./features/companies/CompanyDetail";
import { CompanyForm } from "./features/companies/CompanyForm";
import { AuthProvider, useAuth } from "./features/auth/AuthContext";
import { CompleteRecoveryPage, FirstPasswordChangePage, LoginPage, RecoveryPage, VerifyDevicePage } from "./features/auth/AuthPages";
import { CompanyWorkspace } from "./features/identity/CompanyWorkspace";

const pageTitles: Record<string, string> = {
  "/users": "Usuarios", "/roles": "Roles y permisos", "/security": "Seguridad", "/audit": "Actividad",
};

export function App() {
  const { pathname } = useLocation();
  useEffect(() => {
    const page = pathname.startsWith("/inventory/receipts") ? "Entradas de inventario"
      : pathname.startsWith("/inventory/movements") ? "Salidas y ajustes"
      : pathname.startsWith("/inventory/history") ? "Historial"
      : pathname.startsWith("/sales") ? "Ventas"
      : pathname.startsWith("/catalog") ? "Catálogo de productos"
      : pathname.startsWith("/companies") ? "Empresas"
      : pageTitles[pathname];
    document.title = page ? page + " · Stockly" : "Stockly";
  }, [pathname]);
  if (import.meta.env.MODE === "demo") return <DemoApp/>;
  return <AuthProvider><AuthenticatedApp/></AuthProvider>;
}

function DemoApp() {
  return <div className="app-shell">
    <a className="skip-link" href="#main">Saltar al contenido</a>
    <aside className="sidebar">
      <NavLink className="brand" to="/companies" aria-label="Stockly, empresas">Stockly<span className="brand-dot" aria-hidden="true">.</span></NavLink>
      <div className="nav-caption">ADMINISTRACIÓN</div>
      <NavLink className="nav-item" to="/companies"><Building2 size={19}/> Empresas</NavLink>
      <div className="sidebar-footer"><span className="avatar"><Building2 size={19}/></span><span>Demostración<br/><strong>local</strong></span><ChevronDown size={15}/></div>
    </aside>
    <div className="workspace">
      <div className="demo-notice" role="status"><strong>Demostración local</strong><span>Datos ficticios · Cambios simulados, sin conexión al backend real.</span></div>
      <header className="topbar"><span>Administración de Stockly</span><span className="topbar-caption">Modo de demostración</span></header>
      <main id="main" tabIndex={-1}><CompanyRoutes/></main>
    </div>
  </div>;
}

function AuthenticatedApp() {
  const {user,loading,logout}=useAuth();const navigate=useNavigate();
  const [menuOpen,setMenuOpen]=useState(false);
  if(loading)return <div className="auth-loading" role="status">Stockly · Comprobando sesión…</div>;
  if(!user)return <Routes>
    <Route path="/verify-device" element={<VerifyDevicePage/>}/>
    <Route path="/forgot-password" element={<RecoveryPage/>}/>
    <Route path="/reset-password" element={<CompleteRecoveryPage/>}/>
    <Route path="*" element={<LoginPage/>}/>
  </Routes>;
  if(user.mustChangePassword)return <Routes><Route path="*" element={<FirstPasswordChangePage/>}/></Routes>;
  if(user.role!=="SAAS_ADMIN")return <CompanyWorkspace/>;
  const closeMenu=()=>setMenuOpen(false);
  return <div className={`app-shell${import.meta.env.MODE === "stk005-demo" ? " stk005-demo-shell" : ""}`}>
    <a className="skip-link" href="#main">Saltar al contenido</a>
    <aside className={`sidebar${menuOpen?" sidebar-open":""}`} id="primary-navigation" aria-label="Navegación principal">
      <NavLink className="brand" to="/companies" aria-label="Stockly, empresas">Stockly<span className="brand-dot" aria-hidden="true">.</span></NavLink>
      <div className="nav-caption">ADMINISTRACIÓN</div>
      <NavLink className="nav-item" to="/companies" onClick={closeMenu}><Building2 size={19}/> Empresas</NavLink>
      <div className="sidebar-footer"><span className="avatar"><Building2 size={19}/></span><span>{user.fullName}<br/><strong>{user.email}</strong></span><ChevronDown size={15}/></div>
    </aside>
    {menuOpen&&<button className="mobile-drawer-scrim" type="button" aria-label="Cerrar menú" onClick={closeMenu}/>}
    <div className="workspace">
      {import.meta.env.DEV && import.meta.env.MODE === "demo" && <div className="demo-notice" role="status"><strong>Demostración local</strong><span>Datos ficticios · Cambios simulados, sin conexión al backend real.</span></div>}
      <header className="topbar"><button className="mobile-menu-toggle" type="button" aria-label={menuOpen?"Cerrar menú":"Abrir menú"} aria-expanded={menuOpen} aria-controls="primary-navigation" onClick={()=>setMenuOpen(v=>!v)}>{menuOpen?<X size={19}/>:<Menu size={19}/>}<span>Menú</span></button><span>Administración de Stockly</span><button className="session-logout" onClick={async()=>{await logout();navigate("/login");}}><LogOut size={16}/> Cerrar sesión</button></header>
      <main id="main" tabIndex={-1}><CompanyRoutes/></main>
    </div>
  </div>;
}

function CompanyRoutes() {
  return <Routes>
    <Route path="/companies" element={<CompanyList/>}/>
    <Route path="/companies/new" element={<CompanyForm/>}/>
    <Route path="/companies/:id" element={<CompanyDetail/>}/>
    <Route path="/companies/:id/edit" element={<CompanyForm/>}/>
    <Route path="*" element={<Navigate to="/companies" replace/>}/>
  </Routes>;
}

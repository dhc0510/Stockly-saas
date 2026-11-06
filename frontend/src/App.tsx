import { NavLink, Route, Routes, Navigate } from "react-router-dom";
import { Building2, ChevronDown } from "lucide-react";
import { CompanyList } from "./features/companies/CompanyList";
import { CompanyDetail } from "./features/companies/CompanyDetail";
import { CompanyForm } from "./features/companies/CompanyForm";

export function App() {
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main">
        Saltar al contenido
      </a>
      <aside className="sidebar">
        <NavLink
          className="brand"
          to="/companies"
          aria-label="Stockly, empresas"
        >
          Stockly
          <span className="brand-dot" aria-hidden="true">
            .
          </span>
        </NavLink>
        <div className="nav-caption">ADMINISTRACIÓN</div>
        <NavLink className="nav-item" to="/companies">
          <Building2 size={19} /> Empresas
        </NavLink>
        <div className="sidebar-footer">
          <span className="avatar">
            <Building2 size={19} />
          </span>
          <span>
            Administración
            <br />
            <strong>de Stockly</strong>
          </span>
          <ChevronDown size={15} />
        </div>
      </aside>
      <div className="workspace">
        {import.meta.env.DEV && import.meta.env.MODE === 'demo' && <div className="demo-notice" role="status"><strong>Demostración local</strong><span>Datos ficticios · Cambios simulados, sin conexión al backend real.</span></div>}
        <header className="topbar">
          <span>Administración de Stockly</span>
          <span className="topbar-caption">Gestión de empresas</span>
        </header>
        <main id="main" tabIndex={-1}>
          <Routes>
            <Route path="/companies" element={<CompanyList />} />
            <Route path="/companies/new" element={<CompanyForm />} />
            <Route path="/companies/:id" element={<CompanyDetail />} />
            <Route path="/companies/:id/edit" element={<CompanyForm />} />
            <Route path="*" element={<Navigate to="/companies" replace />} />
          </Routes>
        </main>
      </div>
    </div>
  );
}

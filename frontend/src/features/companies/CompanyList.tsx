import { useEffect, useState } from "react";
import { Link, useLocation } from "react-router-dom";
import {
  Building2,
  Plus,
  Search,
  SlidersHorizontal,
  ChevronLeft,
  ChevronRight,
  ArrowUpRight,
} from "lucide-react";
import { api, registrationDate, type CompanyPage, type Filters } from "./api";
import { ErrorNotice, Loading, Status, Success } from "./shared";

const initial: Filters = {
  search: "",
  status: "",
  registeredFrom: "",
  registeredTo: "",
  page: 0,
};
export function CompanyList() {
  const [filters, setFilters] = useState(initial);
  const [draftSearch, setDraftSearch] = useState("");
  const [data, setData] = useState<CompanyPage>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const location = useLocation();
  useEffect(() => {
    const timer = setTimeout(
      () =>
        setFilters((old) =>
          old.search === draftSearch
            ? old
            : { ...old, search: draftSearch, page: 0 },
        ),
      300,
    );
    return () => clearTimeout(timer);
  }, [draftSearch]);
  useEffect(() => {
    const abort = new AbortController();
    setLoading(true);
    setError(undefined);
    api
      .list(filters, abort.signal)
      .then(setData)
      .catch((e) => {
        if (!abort.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!abort.signal.aborted) setLoading(false);
      });
    return () => abort.abort();
  }, [filters, reload]);
  function change(key: keyof Filters, value: string | number) {
    setFilters((old) => ({
      ...old,
      [key]: value,
      ...(key === "page" ? {} : { page: 0 }),
    }));
  }
  const filtered = Boolean(
    filters.search ||
      filters.status ||
      filters.registeredFrom ||
      filters.registeredTo,
  );
  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">TU PLATAFORMA, EN ORDEN</p>
          <h1>Empresas</h1>
          <p className="subtitle">
            Administra los negocios registrados en Stockly.
          </p>
        </div>
        <Link className="button primary" to="/companies/new">
          <Plus size={19} /> Registrar empresa
        </Link>
      </div>
      {location.state?.message && <Success>{location.state.message}</Success>}
      <section className="panel list-panel" aria-label="Listado de empresas">
        <div className="filters">
          <label className="search-field">
            <Search size={19} />
            <span className="sr-only">Buscar empresas</span>
            <input
              type="search"
              maxLength={150}
              placeholder="Buscar por nombre o identificación"
              value={draftSearch}
              onChange={(e) => setDraftSearch(e.target.value)}
            />
          </label>
          <button
            className="button secondary filter-toggle"
            aria-expanded={expanded}
            onClick={() => setExpanded(!expanded)}
          >
            <SlidersHorizontal size={17} /> Filtros
          </button>
          <div className={`filter-fields ${expanded ? "expanded" : ""}`}>
            <label>
              <span>Estado</span>
              <select
                aria-label="Estado"
                value={filters.status}
                onChange={(e) => change("status", e.target.value)}
              >
                <option value="">Todas las empresas</option>
                <option value="ACTIVE">Activas</option>
                <option value="SUSPENDED">Suspendidas</option>
              </select>
            </label>
            <label>
              <span>Registrada desde</span>
              <input
                type="date"
                value={filters.registeredFrom}
                onChange={(e) => change("registeredFrom", e.target.value)}
              />
            </label>
            <label>
              <span>Hasta</span>
              <input
                type="date"
                value={filters.registeredTo}
                onChange={(e) => change("registeredTo", e.target.value)}
              />
            </label>
          </div>
        </div>
        {filtered && (
          <div className="filter-summary">
            <span>Filtros aplicados</span>
            <button
              className="text-button"
              onClick={() => {
                setDraftSearch("");
                setFilters(initial);
              }}
            >
              Limpiar filtros
            </button>
          </div>
        )}
        {loading ? (
          <Loading />
        ) : error ? (
          <ErrorNotice error={error} retry={() => setReload((x) => x + 1)} />
        ) : !data?.items.length ? (
          <div className="empty-state">
            <div className="empty-icon">
              <Building2 size={30} />
            </div>
            <h2>
              {filtered
                ? "No encontramos empresas"
                : "Tu primera empresa empieza aquí"}
            </h2>
            <p>
              {filtered
                ? "Prueba con otra búsqueda o ajusta los filtros."
                : "Registra un negocio y su administrador para comenzar."}
            </p>
            {!filtered && (
              <Link className="button primary" to="/companies/new">
                <Plus size={18} /> Registrar empresa
              </Link>
            )}
          </div>
        ) : (
          <>
            <table className="company-table">
              <thead>
                <tr>
                  <th scope="col">Empresa</th>
                  <th scope="col">Identificación</th>
                  <th scope="col">Administrador</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Fecha de registro</th>
                  <th scope="col">
                    <span className="sr-only">Acciones</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((company) => (
                  <tr key={company.id}>
                    <td>
                      <span className="company-name">
                        <span className="company-icon">
                          <Building2 size={19} />
                        </span>
                        <strong>{company.commercialName}</strong>
                      </span>
                    </td>
                    <td data-label="Identificación">
                      {company.identification}
                    </td>
                    <td data-label="Administrador">
                      {company.administrator.fullName}
                    </td>
                    <td>
                      <Status status={company.status} />
                    </td>
                    <td data-label="Registro">
                      {registrationDate(company.createdAt)}
                    </td>
                    <td>
                      <Link
                        className="detail-link"
                        to={`/companies/${company.id}`}
                        aria-label={`Ver detalle de ${company.commercialName}`}
                      >
                        Ver detalle <ArrowUpRight size={14} />
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            <footer className="table-footer">
              <span>
                Mostrando {data.page * data.size + 1}–
                {Math.min((data.page + 1) * data.size, data.total)} de{" "}
                {data.total} empresas
              </span>
              <nav className="pagination" aria-label="Paginación">
                <button
                  className="icon-button"
                  aria-label="Página anterior"
                  disabled={!filters.page}
                  onClick={() => change("page", filters.page - 1)}
                >
                  <ChevronLeft size={17} />
                </button>
                <span className="page-number" aria-current="page">
                  {filters.page + 1}
                </span>
                <button
                  className="icon-button"
                  aria-label="Página siguiente"
                  disabled={(data.page + 1) * data.size >= data.total}
                  onClick={() => change("page", filters.page + 1)}
                >
                  <ChevronRight size={17} />
                </button>
              </nav>
            </footer>
          </>
        )}
      </section>
      <p className="footnote">
        Cada negocio mantiene su información de forma independiente.
      </p>
    </>
  );
}

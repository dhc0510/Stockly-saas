import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Building2,
  Check,
  Pause,
  Pencil,
  Play,
  Trash2,
  TriangleAlert,
  UserRound,
  X,
} from "lucide-react";
import {
  api,
  identificationTypes,
  registrationDate,
  type Company,
} from "./api";
import { ErrorNotice, Loading, Status, Success } from "./shared";

export function CompanyDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const [company, setCompany] = useState<Company>();
  const [error, setError] = useState<unknown>();
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [action, setAction] = useState<"state" | "delete">();
  const [message, setMessage] = useState("");
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError(undefined);
    api
      .get(id!, controller.signal)
      .then(setCompany)
      .catch((e) => {
        if (!controller.signal.aborted) setError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, reload]);
  if (loading) return <Loading />;
  if (error)
    return <ErrorNotice error={error} retry={() => setReload((n) => n + 1)} />;
  if (!company) return null;
  const values = [
    ["Nombre comercial", company.commercialName],
    ["Nombre o razón social", company.legalName],
    ["Tipo de identificación", identificationTypes[company.identificationType]],
    ["Identificación", company.identification],
    ["Correo de contacto", company.contactEmail],
    ["Teléfono", company.phone || "No registrado"],
    ["Dirección", company.address || "No registrada"],
    ["Fecha de registro", registrationDate(company.createdAt)],
  ];
  return (
    <>
      <Link className="back-link" to="/companies">
        <ArrowLeft size={17} /> Empresas
      </Link>
      <div className="page-heading detail-heading">
        <div>
          <div className="title-row">
            <h1>{company.commercialName}</h1>
            <Status status={company.status} />
          </div>
          <p className="subtitle">
            Información del negocio y su administrador inicial.
          </p>
        </div>
        <div className="detail-actions">
          <Link className="button primary" to={`/companies/${id}/edit`}>
            <Pencil size={17} /> Editar empresa
          </Link>
          <button
            className="button secondary"
            onClick={() => setAction("state")}
          >
            {company.status === "ACTIVE" ? (
              <Pause size={17} />
            ) : (
              <Play size={17} />
            )}{" "}
            {company.status === "ACTIVE" ? "Suspender" : "Reactivar"}
          </button>
          <button
            className="button danger-outline"
            onClick={() => setAction("delete")}
          >
            <Trash2 size={17} /> Eliminar empresa
          </button>
        </div>
      </div>
      {(message || location.state?.message) && (
        <Success>{message || location.state.message}</Success>
      )}
      <div className="detail-grid">
        <section className="panel form-panel">
          <h2 className="section-title">
            <Building2 size={21} /> Datos de la empresa
          </h2>
          <dl className="company-data">
            {values.map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        </section>
        <section className="panel form-panel">
          <h2 className="section-title">
            <UserRound size={21} /> Administrador
          </h2>
          <dl>
            <dt>Nombre completo</dt>
            <dd>{company.administrator.fullName}</dd>
            <dt>Nombre de usuario</dt>
            <dd>{company.administrator.username}</dd>
            <dt>Correo del administrador</dt>
            <dd>{company.administrator.email}</dd>
          </dl>
          <p className="administrator-note">
            Vinculado únicamente a esta empresa.
          </p>
        </section>
      </div>
      {action && (
        <Confirmation
          company={company}
          action={action}
          close={() => setAction(undefined)}
          done={(updated) => {
            setAction(undefined);
            if (!updated)
              navigate("/companies", {
                replace: true,
                state: { message: "Empresa eliminada definitivamente." },
              });
            else {
              setCompany(updated);
              setMessage(
                updated.status === "ACTIVE"
                  ? "Empresa reactivada."
                  : "Empresa suspendida. Sus sesiones fueron invalidadas.",
              );
            }
          }}
        />
      )}
    </>
  );
}

export function Confirmation({
  company,
  action,
  close,
  done,
}: {
  company: Company;
  action: "state" | "delete";
  close: () => void;
  done: (updated?: Company) => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [name, setName] = useState("");
  const [saving, setSaving] = useState(false);
  const busy = useRef(false);
  const [error, setError] = useState<unknown>();
  const deleting = action === "delete";
  const suspending = company.status === "ACTIVE";
  const title = deleting
    ? "Eliminar empresa"
    : suspending
      ? "Suspender empresa"
      : "Reactivar empresa";
  useEffect(() => {
    const element = dialog.current!;
    const previous = document.activeElement as HTMLElement | null;
    element.showModal();
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    return () => {
      element.close();
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy.current || (deleting && name.trim() !== company.commercialName))
      return;
    busy.current = true;
    setSaving(true);
    setError(undefined);
    try {
      if (deleting) {
        await api.delete(company, name.trim());
        done();
      } else done(await api.state(company));
    } catch (e) {
      setError(e);
    } finally {
      busy.current = false;
      setSaving(false);
    }
  }
  return (
    <dialog
      ref={dialog}
      className="confirmation"
      aria-labelledby="confirmation-title"
      aria-describedby="confirmation-description"
      onCancel={(e) => {
        e.preventDefault();
        if (!busy.current) close();
      }}
    >
      <form onSubmit={submit}>
        <header className="dialog-header">
          <span>
            Stockly<span className="brand-dot">.</span>
          </span>
          <button
            type="button"
            className="icon-button"
            aria-label="Cerrar confirmación"
            onClick={close}
            disabled={saving}
          >
            <X size={19} />
          </button>
        </header>
        <div className="dialog-body">
          <span className={`dialog-icon ${deleting ? "destructive" : ""}`}>
            {deleting ? (
              <Trash2 size={25} />
            ) : suspending ? (
              <Pause size={25} />
            ) : (
              <Check size={25} />
            )}
          </span>
          <h2 id="confirmation-title">{title}</h2>
          <p className="dialog-company">{company.commercialName}</p>
          <div id="confirmation-description">
            {deleting ? (
              <>
                <div className="notice error">
                  <TriangleAlert size={22} />
                  <span>
                    Esta acción eliminará los datos operativos y el acceso. No
                    se puede deshacer.
                  </span>
                </div>
                <p>
                  Se conservará únicamente lo exigido legalmente y los respaldos
                  hasta su vencimiento.
                </p>
              </>
            ) : (
              <p>
                {suspending
                  ? "Se bloqueará el acceso y se invalidarán las sesiones abiertas. Sus datos se conservarán. Los dispositivos sin internet aplicarán el bloqueo al reconectarse."
                  : "La empresa volverá a estar activa y podrá acceder a Stockly con una nueva sesión."}
              </p>
            )}
          </div>
          {deleting && (
            <label className="field" htmlFor="confirmationName">
              <span>
                Escribe <strong>{company.commercialName}</strong> para
                confirmar.
              </span>
              <input
                id="confirmationName"
                autoComplete="off"
                placeholder="Nombre exacto de la empresa"
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={saving}
              />
            </label>
          )}
          {error ? <ErrorNotice error={error} /> : null}
        </div>
        <footer className="dialog-actions">
          <button
            type="button"
            className="button secondary"
            onClick={close}
            disabled={saving}
          >
            Cancelar
          </button>
          <button
            type="submit"
            className={`button ${deleting ? "danger" : "primary"}`}
            disabled={
              saving || (deleting && name.trim() !== company.commercialName)
            }
          >
            {saving
              ? "Procesando…"
              : deleting
                ? "Eliminar definitivamente"
                : title}
          </button>
        </footer>
      </form>
    </dialog>
  );
}

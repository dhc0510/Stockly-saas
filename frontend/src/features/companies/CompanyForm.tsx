import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  ArrowLeft,
  Building2,
  CheckCircle2,
  Eye,
  EyeOff,
  Info,
  UserRound,
} from "lucide-react";
import {
  api,
  emptyCompany,
  identificationTypes,
  type Administrator,
  type Company,
  type CompanyInput,
} from "./api";
import { ApiError } from "../../lib/http";
import { validate } from "./validation";
import { ErrorNotice, Loading } from "./shared";

export function CompanyForm() {
  const { id } = useParams();
  const navigate = useNavigate();
  const editing = Boolean(id);
  const [company, setCompany] = useState<CompanyInput>({ ...emptyCompany });
  const [original, setOriginal] = useState<Company>();
  const [admin, setAdmin] = useState<Administrator>({
    fullName: "",
    username: "",
    email: "",
    temporaryPassword: "",
  });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<unknown>();
  const [loadError, setLoadError] = useState<unknown>();
  const [loading, setLoading] = useState(editing);
  const [reload, setReload] = useState(0);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const [showPassword, setShowPassword] = useState(false);
  const form = useRef<HTMLFormElement>(null);
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    setLoading(true);
    setLoadError(undefined);
    api
      .get(id, controller.signal)
      .then((c) => {
        setOriginal(c);
        setCompany({ ...c, phone: c.phone || "", address: c.address || "" });
      })
      .catch((e) => {
        if (!controller.signal.aborted) setLoadError(e);
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [id, reload]);
  async function submit(event: FormEvent) {
    event.preventDefault();
    if (savingRef.current) return;
    const invalid = validate(company, editing ? undefined : admin);
    setErrors(invalid);
    setError(undefined);
    if (Object.keys(invalid).length) {
      setTimeout(
        () =>
          form.current
            ?.querySelector<HTMLElement>('[aria-invalid="true"]')
            ?.focus(),
        0,
      );
      return;
    }
    savingRef.current = true;
    setSaving(true);
    try {
      const saved = editing
        ? await api.update(id!, company, original!.version)
        : await api.create(company, admin);
      setAdmin((a) => ({ ...a, temporaryPassword: "" }));
      navigate(`/companies/${saved.id}`, {
        replace: true,
        state: {
          message: editing
            ? "Datos de la empresa actualizados."
            : "Empresa registrada y administrador inicial creado.",
        },
      });
    } catch (e) {
      setError(e);
      if (e instanceof ApiError)
        setErrors(
          Object.fromEntries(
            Object.entries(e.fieldErrors).map(([k, v]) => [
              k.split(".").at(-1)!,
              v,
            ]),
          ),
        );
      setTimeout(
        () =>
          form.current?.querySelector<HTMLElement>('[role="alert"]')?.focus(),
        0,
      );
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }
  const set = (field: keyof CompanyInput, value: string) =>
    setCompany((c) => ({ ...c, [field]: value }));
  function field(
    key: keyof CompanyInput,
    label: string,
    options: { type?: string; optional?: boolean; max?: number } = {},
  ) {
    return (
      <label className="field" key={key} htmlFor={key}>
        <span id={`label-${key}`}>
          {label}{" "}
          {options.optional ? (
            <small>(opcional)</small>
          ) : (
            <span className="required">*</span>
          )}
        </span>
        <input
          id={key}
          aria-labelledby={`label-${key}`}
          type={options.type || "text"}
          maxLength={options.max || 150}
          required={!options.optional}
          value={company[key]}
          onChange={(e) => set(key, e.target.value)}
          aria-invalid={!!errors[key]}
          aria-describedby={errors[key] ? `${key}-error` : undefined}
        />
        {errors[key] && (
          <span className="field-error" id={`${key}-error`}>
            {errors[key]}
          </span>
        )}
      </label>
    );
  }
  if (loading) return <Loading />;
  if (loadError)
    return (
      <ErrorNotice error={loadError} retry={() => setReload((n) => n + 1)} />
    );
  return (
    <>
      <Link
        className="back-link"
        to={editing ? `/companies/${id}` : "/companies"}
      >
        <ArrowLeft size={17} /> {editing ? "Detalle de empresa" : "Empresas"}
      </Link>
      <div className="page-heading">
        <div>
          <h1>{editing ? "Editar empresa" : "Registrar empresa"}</h1>
          <p className="subtitle">
            {editing
              ? "Actualiza la información del negocio."
              : "Crea un negocio y su administrador inicial en Stockly."}
          </p>
        </div>
      </div>
      <form
        ref={form}
        onSubmit={submit}
        noValidate
        className="company-form"
        aria-busy={saving}
      >
        {error ? (
          <div tabIndex={-1} role="alert">
            <ErrorNotice error={error} />
            {error instanceof ApiError && error.code === "STALE_VERSION" && (
              <button
                type="button"
                className="button secondary"
                onClick={() => {
                  setReload((n) => n + 1);
                  setError(undefined);
                }}
              >
                Recargar datos de la empresa
              </button>
            )}
          </div>
        ) : null}
        <div className="form-layout">
          <section className="panel form-panel">
            <h2 className="section-title">
              <Building2 size={21} /> Datos de la empresa
            </h2>
            <div className="form-grid">
              {field("commercialName", "Nombre comercial")}
              {field("legalName", "Nombre o razón social")}
              <label className="field" htmlFor="identificationType">
                <span id="label-identificationType">
                  Tipo de identificación <span className="required">*</span>
                </span>
                <select
                  id="identificationType"
                  aria-labelledby="label-identificationType"
                  required
                  value={company.identificationType}
                  onChange={(e) => set("identificationType", e.target.value)}
                  aria-invalid={!!errors.identificationType}
                  aria-describedby={
                    errors.identificationType
                      ? "identificationType-error"
                      : undefined
                  }
                >
                  <option value="">Seleccionar tipo</option>
                  {Object.entries(identificationTypes).map(([key, title]) => (
                    <option key={key} value={key}>
                      {title}
                    </option>
                  ))}
                </select>
                {errors.identificationType && (
                  <span className="field-error" id="identificationType-error">
                    {errors.identificationType}
                  </span>
                )}
              </label>
              {field("identification", "Número de identificación", { max: 24 })}
              {field("contactEmail", "Correo de contacto", {
                type: "email",
                max: 254,
              })}
              {field("phone", "Teléfono", {
                type: "tel",
                optional: true,
                max: 30,
              })}
              <label className="field full-width" htmlFor="address">
                <span>
                  Dirección <small>(opcional)</small>
                </span>
                <textarea
                  id="address"
                  maxLength={500}
                  rows={3}
                  value={company.address}
                  onChange={(e) => set("address", e.target.value)}
                  aria-invalid={!!errors.address}
                  aria-describedby={
                    errors.address ? "address-error" : undefined
                  }
                />
                {errors.address && (
                  <span className="field-error" id="address-error">
                    {errors.address}
                  </span>
                )}
              </label>
            </div>
          </section>
          {!editing ? (
            <section className="panel form-panel">
              <h2 className="section-title">
                <UserRound size={21} /> Administrador inicial
              </h2>
              <p className="section-description">
                Esta persona administrará únicamente este negocio.
              </p>
              <div className="form-grid single-column">
                {(
                  [
                    "fullName",
                    "username",
                    "email",
                    "temporaryPassword",
                  ] as const
                ).map((key) => (
                  <label className="field" key={key} htmlFor={`admin-${key}`}>
                    <span id={`label-admin-${key}`}>
                      {
                        {
                          fullName: "Nombre completo",
                          username: "Nombre de usuario",
                          email: "Correo del administrador",
                          temporaryPassword: "Contraseña temporal",
                        }[key]
                      }{" "}
                      <span className="required">*</span>
                    </span>
                    <div
                      className={
                        key === "temporaryPassword" ? "password-input" : ""
                      }
                    >
                      <input
                        id={`admin-${key}`}
                        aria-labelledby={`label-admin-${key}`}
                        type={
                          key === "email"
                            ? "email"
                            : key === "temporaryPassword"
                              ? showPassword
                                ? "text"
                                : "password"
                              : "text"
                        }
                        autoComplete={
                          key === "temporaryPassword"
                            ? "new-password"
                            : key === "username"
                              ? "username"
                              : key === "email"
                                ? "email"
                                : "name"
                        }
                        maxLength={
                          key === "temporaryPassword"
                            ? 128
                            : key === "username"
                              ? 30
                              : key === "email"
                                ? 254
                                : 150
                        }
                        value={admin[key]}
                        required
                        onChange={(e) =>
                          setAdmin((a) => ({ ...a, [key]: e.target.value }))
                        }
                        aria-invalid={!!errors[key]}
                        aria-describedby={`${key}-hint${errors[key] ? ` ${key}-error` : ""}`}
                      />
                      {key === "temporaryPassword" && (
                        <button
                          type="button"
                          className="password-toggle"
                          aria-label={
                            showPassword
                              ? "Ocultar contraseña"
                              : "Mostrar contraseña"
                          }
                          aria-pressed={showPassword}
                          onClick={() => setShowPassword(!showPassword)}
                        >
                          {showPassword ? (
                            <EyeOff size={19} />
                          ) : (
                            <Eye size={19} />
                          )}
                        </button>
                      )}
                    </div>
                    <small id={`${key}-hint`}>
                      {key === "temporaryPassword"
                        ? "Entre 15 y 128 caracteres. Se permiten espacios."
                        : key === "username"
                          ? "3–30 caracteres: letras sin tildes, números, punto o guion."
                          : ""}
                    </small>
                    {errors[key] && (
                      <span className="field-error" id={`${key}-error`}>
                        {errors[key]}
                      </span>
                    )}
                  </label>
                ))}
              </div>
              <div className="notice info">
                <Info size={19} />
                <span>
                  El administrador deberá cambiar la contraseña al ingresar.
                </span>
              </div>
              <div className="notice success">
                <CheckCircle2 size={19} />
                <span>La empresa se registrará activa.</span>
              </div>
            </section>
          ) : (
            original && (
              <section className="panel form-panel administrator-summary">
                <h2 className="section-title">
                  <UserRound size={21} /> Administrador
                </h2>
                <p className="section-description">
                  Solo consulta. Editar la empresa no modifica el acceso.
                </p>
                <dl>
                  <dt>Nombre completo</dt>
                  <dd>{original.administrator.fullName}</dd>
                  <dt>Nombre de usuario</dt>
                  <dd>{original.administrator.username}</dd>
                  <dt>Correo</dt>
                  <dd>{original.administrator.email}</dd>
                </dl>
              </section>
            )
          )}
        </div>
        <footer className="form-footer">
          <small>
            <span className="required">*</span> Campos obligatorios
          </small>
          <div className="form-actions">
            <Link
              className={`button secondary ${saving ? "disabled-link" : ""}`}
              to={editing ? `/companies/${id}` : "/companies"}
              aria-disabled={saving}
              tabIndex={saving ? -1 : 0}
            >
              Cancelar
            </Link>
            <button type="submit" className="button primary" disabled={saving}>
              {saving
                ? "Guardando…"
                : editing
                  ? "Guardar cambios"
                  : "Registrar empresa"}
            </button>
          </div>
        </footer>
      </form>
    </>
  );
}

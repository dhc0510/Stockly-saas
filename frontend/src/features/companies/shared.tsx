import { AlertCircle, CheckCircle2, LoaderCircle } from "lucide-react";
import { ApiError } from "../../lib/http";
import type { Company } from "./api";

export function Status({ status }: { status: Company["status"] }) {
  return (
    <span className={`status ${status.toLowerCase()}`}>
      <span aria-hidden="true" />
      {status === "ACTIVE" ? "Activa" : "Suspendida"}
    </span>
  );
}
export function Loading() {
  return (
    <div className="state-panel" role="status">
      <LoaderCircle className="spin" size={24} /> Cargando información…
    </div>
  );
}
export function ErrorNotice({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  const restricted =
    error instanceof ApiError && [401, 403].includes(error.status);
  return (
    <div className="notice error" role="alert">
      <AlertCircle size={20} />
      <div>
        <strong>
          {restricted
            ? "Acceso restringido"
            : "No se pudo completar la operación"}
        </strong>
        <p>{error instanceof Error ? error.message : "Intenta de nuevo."}</p>
        {restricted && (
          <p>La gestión requiere una sesión de administrador de Stockly.</p>
        )}
        {retry && (
          <button type="button" className="button secondary" onClick={retry}>
            Volver a intentar
          </button>
        )}
      </div>
    </div>
  );
}
export function Success({ children }: { children: React.ReactNode }) {
  return (
    <div className="notice success" role="status">
      <CheckCircle2 size={20} />
      <span>{children}</span>
    </div>
  );
}

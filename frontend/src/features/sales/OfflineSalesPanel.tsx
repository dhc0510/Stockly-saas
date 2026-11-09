import { useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { ensureCsrf } from "../auth/api";
import { receiptDate } from "../receipts/receiptApi";
import { Heading } from "./SalesPage";
import { listReview, resolveSale, syncOffline, type Entry, type OfflineState } from "./offlineSales";

const money = new Intl.NumberFormat("es-CR", { style: "currency", currency: "CRC" });
const reference = (id: string) => "LOCAL-" + id.toUpperCase();
const errorText = (error: unknown) => error instanceof Error ? error.message : "No se pudo completar la operación.";

export function OfflineSalesPanel({ owner, offline, canReview }: { owner: string; offline: OfflineState; canReview: boolean }) {
  const [syncing, setSyncing] = useState(false);
  const [error, setError] = useState("");
  const pending = offline.queue.filter(row => !row.result);
  const prepared = offline.shellReady ? offline.branches.filter(row => Date.parse(row.expiresAt) > Date.now()) : [];
  return <section className="sales-panel sales-work-panel sales-offline-panel">
    <div className="sales-offline-top">
      <div><h2>{offline.connected
        ? prepared.length ? "Listo para vender sin internet" : "Falta preparar este equipo"
        : prepared.length ? "Sin conexión" : "Reconecta para preparar este equipo"}</h2>
      {prepared.length ? <p>{prepared.map(row => row.branch.name).join(", ")} · Datos guardados</p>
        : <p>{offline.problem || "Stockly prepara la aplicación y los datos de las sucursales autorizadas con conexión."}</p>}</div>
      {canReview && offline.connected && <Link className="button secondary" to="/sales/offline/review">Requiere revisión</Link>}
    </div>
    {!!pending.length && <>
      <div className="sales-offline-top">
        <strong>{pending.length === 1 ? "1 venta pendiente" : pending.length + " ventas pendientes"}</strong>
        <button type="button" className="button secondary" disabled={!offline.connected || syncing} onClick={async () => {
          setSyncing(true); setError("");
          try { await syncOffline(owner); } catch (failure) { setError(errorText(failure)); }
          finally { setSyncing(false); }
        }}>{syncing ? "Sincronizando…" : "Sincronizar ahora"}</button>
      </div>
      {error && <p role="alert" className="notice error">{error}</p>}
    </>}
    {offline.queue.map(row => <div key={row.key} className="sales-offline-row">
      <div><strong>{row.result?.folio ?? reference(row.sale.requestKey)}</strong><small>{row.branch.name} · <time>{receiptDate(row.soldAt)}</time></small>
        {row.error && <p className="sales-line-note">{row.error}</p>}</div>
      <span>{row.result?.state === "SYNCED" ? "Sincronizada" : row.result?.state === "REVIEW" ? "Requiere revisión" : row.result?.state === "CLOSED" ? "Cerrada" : "Pendiente de sincronización"}</span>
      <Link className="button secondary" to={row.result?.folio ? "/sales/" + row.result.folio : "/sales/offline/" + row.sale.requestKey}>Ver comprobante</Link>
    </div>)}
  </section>;
}

export function OfflineReceipt({ offline }: { offline: OfflineState }) {
  const { id } = useParams();
  const row = offline.queue.find(sale => sale.sale.requestKey === id);
  return <>
    <Heading title="Comprobante provisional" description="Constancia interna de la venta registrada en este equipo.">
      <Link className="button secondary" to="/sales">Volver a ventas</Link>
    </Heading>
    {row ? <section className="sales-panel sales-work-panel">
      <strong>{row.result?.state === "REVIEW" ? "Requiere revisión" : row.result?.state === "CLOSED" ? "Cerrada" : row.result?.folio ? "Sincronizada" : "Pendiente de sincronización"}</strong>
      <dl className="sales-details">
        <div><dt>Referencia local</dt><dd className="sales-offline-ref">{reference(row.sale.requestKey)}</dd></div>
        <div><dt>Sucursal</dt><dd>{row.branch.name}</dd></div>
        <div><dt>Fecha de venta</dt><dd><time>{receiptDate(row.soldAt)}</time></dd></div>
      </dl>
      <ul className="sales-lines">{row.sale.lines.map(line => <li key={line.itemId}>
        <strong>{row.items[line.itemId]}</strong><span>{line.quantity}</span>
        <strong>{money.format(Number(line.quantity) * (line.expectedPrice ?? 0))}</strong>
      </li>)}</ul>
      <h2>Cobros registrados</h2>
      <ul>{row.sale.payments.map((payment, index) => <li key={index}>
        {{ CASH: "Efectivo recibido", CARD: "Tarjeta", SINPE: "SINPE Móvil", TRANSFER: "Transferencia" }[payment.method] ?? payment.method} · {money.format(Number(payment.amount))}
      </li>)}</ul>
      {row.result?.folio && <Link to={"/sales/" + row.result.folio}>Abrir venta {row.result.folio}</Link>}
      {row.error && <p className="notice error" role="alert">{row.error}</p>}
    </section> : <p role="status">No se encontró esta venta entre los registros de tu cuenta en este equipo.</p>}
  </>;
}

export function OfflineReview() {
  const [entries, setEntries] = useState<Entry[]>();
  const [closing, setClosing] = useState<string>();
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  useEffect(() => {
    let active = true;
    listReview().then(items => { if (active) setEntries(items); }).catch(failure => { if (active) setError(errorText(failure)); });
    return () => { active = false; };
  }, []);
  async function resolve(id: string, action: "retry" | "close", details?: { reason: string; refundDetail: string }) {
    setBusy(id); setError(""); setMessage("");
    try {
      await ensureCsrf();
      const entry = await resolveSale(id, action, details);
      setEntries(items => items?.map(item => item.id === id ? entry : item));
      setClosing(undefined);
      setMessage(entry.state === "SYNCED" ? "Venta sincronizada · " + entry.folio
        : entry.state === "CLOSED" ? "Cierre registrado." : "La venta todavía requiere revisión.");
    } catch (failure) { setError(errorText(failure)); }
    finally { setBusy(undefined); }
  }
  function close(event: FormEvent<HTMLFormElement>, id: string) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const reason = String(form.get("reason") ?? "").trim();
    if (!reason) { setError("Escribe el motivo del cierre."); event.currentTarget.querySelector("textarea")?.focus(); return; }
    void resolve(id, "close", { reason, refundDetail: String(form.get("refundDetail") ?? "").trim() });
  }
  return <>
    <Heading title="Ventas que requieren revisión" description="Resuelve los faltantes y conflictos de cobro de tus sucursales.">
      <Link className="button secondary" to="/sales">Volver a ventas</Link>
    </Heading>
    {error && <p role="alert" className="notice error">{error}</p>}
    {message && <p role="status">{message}</p>}
    {entries?.length === 0 && <p className="sales-panel">No hay ventas para revisar.</p>}
    {!entries && !error && <p role="status">Cargando ventas…</p>}
    {entries?.map(entry => <section className="sales-panel sales-work-panel sales-offline-panel" key={entry.id}>
      <h2 className="sales-offline-ref">{entry.reference}</h2>
      <p>{entry.branchName} · <time>{receiptDate(entry.soldAt)}</time></p>
      {entry.state === "REVIEW" && <>
        <p className="sales-line-note">{entry.issue?.message}</p>
        {Object.entries(entry.issue?.fields ?? {}).map(([key, detail]) => <p key={key} className="sales-line-note">{detail}</p>)}
        <div className="sales-actions">
          <button type="button" className="button secondary" disabled={!!busy} onClick={() => setClosing(entry.id)}>Cerrar con motivo</button>
          <button type="button" className="catalog-primary" disabled={!!busy} onClick={() => void resolve(entry.id, "retry")}>Reintentar</button>
        </div>
        {closing === entry.id && <form onSubmit={event => close(event, entry.id)} noValidate>
          <label>Motivo del cierre<textarea name="reason" autoFocus maxLength={150} /></label>
          <label>Detalle del reintegro efectuado<textarea name="refundDetail" maxLength={300} /></label>
          <div className="sales-actions">
            <button type="button" className="button secondary" onClick={() => setClosing(undefined)}>Cancelar</button>
            <button type="submit" className="catalog-primary" disabled={!!busy}>Confirmar cierre</button>
          </div>
        </form>}
      </>}
      {entry.state === "CLOSED" && <><p><strong>Cerrada por {entry.resolver ?? "usuario registrado"}</strong> · {entry.closeReason}</p>
        {entry.refundDetail && <p>Reintegro · {entry.refundDetail}</p>}</>}
      {entry.folio && <Link to={"/sales/" + entry.folio}>Abrir venta {entry.folio}</Link>}
    </section>)}
  </>;
}

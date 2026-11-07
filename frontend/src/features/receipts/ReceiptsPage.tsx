import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, Navigate, useLocation, useNavigate, useParams } from "react-router-dom";
import { Plus, Search } from "lucide-react";
import type { SessionUser } from "../auth/api";
import { ReceiptEditor, receiptDraftStatus } from "./ReceiptEditor";
import { amount, failureMessage, receiptApi, receiptDate, type Movement, type Receipt } from "./receiptApi";
import "./receipts.css";

export function receiptAccess(user: SessionUser) {
  const admin = user.role === "COMPANY_ADMIN";
  const level = user.modules.find(module => module.moduleKey === "INVENTORY_RECEIPTS")?.accessLevel;
  return {
    read: admin || (!!level && level !== "NONE"),
    write: admin || level === "OPERATE" || level === "ADMIN",
    reverse: admin || user.specialPermissions.includes("VOID_INVENTORY_RECEIPTS"),
  };
}
type Access = ReturnType<typeof receiptAccess>;

function Status({ entry }: { entry: Movement }) {
  return <span className={entry.voidId ? "catalog-badge" : "catalog-badge active"}>
    {entry.voidId ? "Anulada" : entry.kind === "VOID" ? "Movimiento inverso" : "Confirmada"}
  </span>;
}

export function ReceiptsPage({ user, access }: { user: SessionUser; access: Access }) {
  const [search, setSearch] = useState("");
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [entries, setEntries] = useState<Movement[]>([]);
  const [more, setMore] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [version, setVersion] = useState(0);

  const [discard, setDiscard] = useState(false);
  const { "*": segment = "" } = useParams();
  const navigate = useNavigate();
  const location = useLocation();
  const base = "/inventory/receipts";
  const storageKey = "stockly:receipt:" + user.companyId + ":" + user.id;
  const editing = segment === "new";
  const detailId = segment && !editing ? segment : "";
  const draft = !segment && access.write ? receiptDraftStatus(storageKey) : undefined;
  const returnToList = () => navigate(base);
  const open = (id: string) => navigate(base + "/" + encodeURIComponent(id));

  useEffect(() => {
    if (segment) return;
    const controller = new AbortController();
    setLoading(true);
    setError("");
    const timer = window.setTimeout(() => {
      receiptApi.list(search, status, page, controller.signal).then(result => {
        if (!controller.signal.aborted) { setEntries(result.entries); setMore(result.hasMore); }
      }).catch(failure => {
        if (!controller.signal.aborted) setError(failureMessage(failure));
      }).finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    }, 150);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [search, status, page, version, segment]);

  if (editing) {
    if (!access.write) return <Navigate to={base} replace />;
    return <ReceiptEditor key={storageKey} storageKey={storageKey}
      close={returnToList}
      saved={result => navigate(base + "/" + result.movement.id, { replace: true, state: { confirmed: true } })} />;
  }

  if (detailId) return <ReceiptRoute key={detailId} id={detailId} canReverse={access.reverse}
    confirmed={location.state?.confirmed === true} open={open} back={returnToList} />;

  function discardDraft() {
    try { localStorage.removeItem(storageKey); setDiscard(false); setVersion(value => value + 1); }
    catch { setError("El navegador no pudo descartar el borrador."); }
  }


  return <section className="catalog-page receipts-page">
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Entradas</p>
      <h1>Entradas de inventario</h1><p className="subtitle">Registra mercancía recibida y consulta las entradas confirmadas.</p></div>
      {access.write && <button className="catalog-primary" onClick={() => navigate(base + "/new")}><Plus size={18} /> Nueva entrada</button>}
    </div>
    {draft && <div className="receipt-note receipt-draft-notice">
      <strong>Tienes una entrada sin confirmar</strong>
      {draft === "pending" && <p>Hay una confirmación pendiente. Continúa para comprobar el resultado.</p>}
      {discard && <p>Se borrará la entrada guardada en este navegador.</p>}
      <div className="catalog-actions">
        {discard ? <>
          <button onClick={() => setDiscard(false)}>Cancelar</button>
          <button className="receipt-danger" onClick={discardDraft}>Confirmar descarte</button>
        </> : <>
          <button className="catalog-primary" onClick={() => navigate(base + "/new")}>Continuar</button>
          {draft !== "pending" && <button onClick={() => setDiscard(true)}>Descartar</button>}
        </>}
      </div>
    </div>}
    <div className="catalog-panel">
      <div className="receipt-filters">
        <label>Buscar entrada<span className="catalog-search"><Search size={16} aria-hidden="true" />
          <input type="search" placeholder="Folio o referencia" maxLength={160} value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} />
        </span></label>
        <label>Estado<select aria-label="Estado" value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">Todos</option><option value="confirmed">Confirmadas</option><option value="voided">Anuladas</option>
        </select></label>
      </div>
      {error && <p className="notice error" role="alert">{error} <button onClick={() => setVersion(value => value + 1)}>Reintentar</button></p>}
      {loading ? <p className="catalog-state" role="status">Cargando entradas…</p> :
        !entries.length ? <p className="catalog-state">Sin entradas con estos filtros.</p> :
        <table className="receipt-table receipt-list"><thead><tr><th>Folio</th><th>Fecha</th><th>Motivo</th><th>Estado</th></tr></thead>
          <tbody>{entries.map(entry => <tr key={entry.id}>
            <td><Link className="receipt-link" to={base + "/" + entry.id}>{entry.folio}</Link></td>
            <td className="receipt-date"><time dateTime={entry.date}>{receiptDate(entry.date)}</time></td>
            <td>{entry.reason}{entry.reference && <small>{entry.reference}</small>}</td>
            <td><Status entry={entry} /></td>
          </tr>)}</tbody></table>}
      {!loading && !error && (page > 0 || more) && <div className="catalog-pagination">
        <button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</button>
        <span>Página {page + 1}</span><button disabled={!more} onClick={() => setPage(value => value + 1)}>Siguiente</button>
      </div>}
    </div>
  </section>;
}

function ReceiptRoute({ id, canReverse, confirmed, open, back }: {
  id: string; canReverse: boolean; confirmed: boolean; open: (id: string) => void; back: () => void;
}) {
  const [receipt, setReceipt] = useState<Receipt>();
  const [error, setError] = useState("");
  const [notice, setNotice] = useState(confirmed ? "Entrada confirmada." : "");
  useEffect(() => {
    const controller = new AbortController();
    receiptApi.get(id, controller.signal).then(result => {
      if (!controller.signal.aborted) setReceipt(result);
    }).catch(failure => {
      if (!controller.signal.aborted) setError(failureMessage(failure));
    });
    return () => controller.abort();
  }, [id]);

  if (!receipt) return <section className="catalog-page receipts-page">
    <button onClick={back}>Volver al listado</button>
    {error ? <p className="notice error" role="alert">{error}</p> : <p role="status">Cargando comprobante…</p>}
  </section>;
  return <ReceiptDetail receipt={receipt} canReverse={canReverse} notice={notice} open={open} back={back}
    reversed={result => { setReceipt(result); setNotice("Entrada anulada. Se registró el movimiento inverso."); }} />;
}

function ReceiptDetail({ receipt, canReverse, notice, open, back, reversed }: {
  receipt: Receipt; canReverse: boolean; notice: string;
  open: (id: string) => void; back: () => void; reversed: (receipt: Receipt) => void;
}) {
  const [showReverse, setShowReverse] = useState(false);
  const reverseForm = useRef<HTMLFormElement>(null);
  const [busy, setBusy] = useState(false);
  const [reverseError, setReverseError] = useState("");
  const movement = receipt.movement;
  const inverse = movement.kind === "VOID";

  useEffect(() => {
    if (!showReverse) return;
    reverseForm.current?.focus({ preventScroll: true });
    reverseForm.current?.scrollIntoView({ block: "center" });
  }, [showReverse]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const reason = String(new FormData(event.currentTarget).get("reason") ?? "").trim();
    if (!reason) { setReverseError("Escribe el motivo de la anulación."); return; }
    setBusy(true);
    setReverseError("");
    try { reversed(await receiptApi.reverse(movement.id, reason)); setShowReverse(false); }
    catch (failure) { setReverseError(failureMessage(failure)); }
    finally { setBusy(false); }
  }

  return <section className="catalog-page receipts-page">
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Entradas</p>
      <h1>{inverse ? "Movimiento inverso " : "Comprobante "}{movement.folio}</h1>
      <p className="subtitle">Registro interno de inventario.</p></div>
      <button onClick={back}>Volver al listado</button>
    </div>
    {notice && <p className="notice" role="status">{notice}</p>}
    <section className="catalog-panel receipt-panel">
      <div className="receipt-detail-title"><h2>Detalle del movimiento</h2><Status entry={movement} /></div>
      <dl className="receipt-metadata">
        <div><dt>Destino</dt><dd>{movement.location}</dd></div>
        <div className="receipt-date"><dt>Fecha y hora</dt><dd><time dateTime={movement.date}>{receiptDate(movement.date)}</time></dd></div>
        <div><dt>Responsable</dt><dd>{movement.actor}</dd></div>
        <div><dt>Motivo</dt><dd>{movement.reason}</dd></div>
        <div><dt>Referencia</dt><dd>{movement.reference || "—"}</dd></div>
      </dl>
      {movement.voidId && <p className="receipt-note"><strong>Anulación:</strong> {movement.voidReason}{" "}
        <button className="catalog-link" onClick={() => void open(movement.voidId!)}>Ver {movement.voidFolio}</button></p>}
      {movement.originalId && <button className="catalog-link" onClick={() => void open(movement.originalId!)}>Ver entrada {movement.originalFolio}</button>}
    </section>
    <section className="catalog-panel receipt-panel">
      <h2>Existencias tras {inverse ? "la anulación" : "la entrada"}</h2>
      <p className="subtitle">Saldo registrado al confirmar el movimiento.</p>
      <table className="receipt-table"><thead><tr><th>Artículo</th><th>{inverse ? "Cantidad retirada" : "Cantidad recibida"}</th><th>Existencia después</th></tr></thead>
        <tbody>{receipt.lines.map(line => <tr key={line.itemId}>
          <td><strong>{line.name}</strong><small>{line.sku}</small></td>
          <td><span className="receipt-mobile-label">{inverse ? "Retirado" : "Recibido"}</span>{!inverse && "+"}{amount(line.quantity, line.unit)}</td>
          <td><span className="receipt-mobile-label">Existencia después</span><strong>{amount(line.balanceAfter, line.unit)}</strong></td>
        </tr>)}</tbody>
      </table>
    </section>
    {!inverse && !movement.voidId && canReverse && (showReverse ?
      <form ref={reverseForm} tabIndex={-1} aria-labelledby="receipt-void-title" className="catalog-panel catalog-form" onSubmit={submit} noValidate>
        <h2 id="receipt-void-title">Anular entrada</h2><p className="receipt-note">Se registrará un movimiento inverso vinculado. La entrada original se conserva.</p>
        <label>Motivo de anulación<input name="reason" required maxLength={150} disabled={busy} aria-invalid={!!reverseError} /></label>
        {reverseError && <p className="notice error" role="alert">{reverseError}</p>}
        <div className="catalog-actions"><button type="button" disabled={busy} onClick={() => { setShowReverse(false); setReverseError(""); }}>Volver</button>
          <button className="receipt-danger" disabled={busy}>{busy ? "Anulando…" : "Confirmar anulación"}</button></div>
      </form> : <div className="catalog-actions"><button onClick={() => setShowReverse(true)}>Anular entrada</button></div>)}
  </section>;
}

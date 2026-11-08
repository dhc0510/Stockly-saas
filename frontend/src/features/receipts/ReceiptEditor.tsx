import { MovementBranch, useMovementBranch } from "../inventory/MovementBranch";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { Plus, Search, X } from "lucide-react";
import { ApiError } from "../../lib/http";
import { amount, failureMessage, receiptApi, type Article, type Receipt } from "./receiptApi";

const MAX_LINES = 200;
const MAX_QUANTITY = 1_000_000;
const reasons = { RECEIPT: "Recepción de mercancía", INITIAL: "Carga inicial", OTHER: "Otro" };
type DraftLine = { article: Article; quantity: string };
type Draft = {
  branchId?: string;
  reason: keyof typeof reasons | ""; detail: string; reference: string; lines: DraftLine[];
  search: string; article?: Article; quantity: string; requestKey?: string;
};
const empty = (): Draft => ({ reason: "", detail: "", reference: "", lines: [], search: "", quantity: "" });

function load(key: string): Draft {
  try { return { ...empty(), ...JSON.parse(localStorage.getItem(key) || "{}") }; }
  catch { return empty(); }
}
export function receiptDraftStatus(key: string) {
  const draft = load(key);
  if (draft.requestKey) return "pending";
  if (draft.lines.length || draft.reason || draft.detail.trim() || draft.reference.trim() || draft.article || draft.search.trim() || draft.quantity.trim())
    return "editing";
}

function parse(value: string) {
  const text = value.trim();
  return /^\d+(?:[.,]\d+)?$/.test(text) ? Number(text.replace(",", ".")) : NaN;
}
function quantityError(value: string, unit: string) {
  const quantity = parse(value);
  if (!Number.isFinite(quantity) || quantity <= 0) return "Ingresa una cantidad mayor que cero.";
  if (unit === "unidad" && !Number.isInteger(quantity)) return "Para unidad solo se aceptan números enteros.";
  if ((value.trim().split(/[.,]/)[1]?.length ?? 0) > 3) return "Ingresa hasta tres decimales.";
  if (quantity > MAX_QUANTITY) return "El máximo por línea es 1.000.000.";
  return "";
}

export function ReceiptEditor({ storageKey, close, saved }: {
  storageKey: string; close: () => void; saved: (receipt: Receipt) => void;
}) {
  const [draft, setDraft] = useState(() => load(storageKey));
  const currentDraft = useRef(draft);
  const [storageError, setStorageError] = useState(false);
  const [suggestions, setSuggestions] = useState<Article[]>([]);
  const [searching, setSearching] = useState(false);
  const [scanning, setScanning] = useState(false);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [review, setReview] = useState(!!draft.requestKey);
  const [discard, setDiscard] = useState(false);
  const discardPanel = useRef<HTMLElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [reasonInvalid, setReasonInvalid] = useState(false);
  const reasonInput = useRef<HTMLSelectElement>(null);
  const [quantityIssue, setQuantityIssue] = useState<{ id: string; message: string }>();
  const [notice, setNotice] = useState("");
  const [flash, setFlash] = useState("");
  const searchInput = useRef<HTMLInputElement>(null);
  const quantityInput = useRef<HTMLInputElement>(null);
  const results = useRef<HTMLDivElement>(null);
  const pending = !!draft.article || !!draft.quantity.trim();
  const branch = useMovementBranch("/api/inventory/receipts", draft.branchId);
  const branchId = draft.branchId ?? branch.selected?.id;
  const term = draft.search.trim();

  useEffect(() => {
    if (branch.problem || !showSuggestions || !term || draft.article) return;
    const controller = new AbortController();
    setSearching(true);
    setSuggestions([]);
    const timer = window.setTimeout(() => {
      receiptApi.items(term, false, controller.signal, branchId).then(items => {
        if (!controller.signal.aborted) setSuggestions(items);
      }).catch(failure => {
        if (!controller.signal.aborted) { setNotice(""); setError(failureMessage(failure)); }
      }).finally(() => { if (!controller.signal.aborted) setSearching(false); });
    }, 150);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [term, showSuggestions, draft.article, branchId, branch.problem]);

  useEffect(() => {
    function align() {
      const input = searchInput.current;
      if (!input || document.activeElement !== input || !matchMedia("(max-width: 760px)").matches) return;
      input.closest(".receipt-item-search")?.scrollIntoView({ block: "start", behavior: "instant" });
      requestAnimationFrame(() => {
        const viewport = window.visualViewport;
        const space = (viewport?.height ?? innerHeight) + (viewport?.offsetTop ?? 0) - input.getBoundingClientRect().bottom - 12;
        input.closest<HTMLElement>(".receipt-item-search")?.style.setProperty("--receipt-results-height", Math.max(72, Math.min(240, space)) + "px");
      });
    }
    const input = searchInput.current;
    input?.addEventListener("focus", align);
    window.visualViewport?.addEventListener("resize", align);
    return () => { input?.removeEventListener("focus", align); window.visualViewport?.removeEventListener("resize", align); };
  }, [review]);

  useEffect(() => {
    if (!discard) return;
    discardPanel.current?.focus({ preventScroll: true });
    discardPanel.current?.scrollIntoView({ block: "center" });
  }, [discard]);

  function store(next: Draft) {
    currentDraft.current = next;
    setDraft(next);
    try { localStorage.setItem(storageKey, JSON.stringify(next)); setStorageError(false); return true; }
    catch { setStorageError(true); return false; }
  }
  function clear() {
    try { localStorage.removeItem(storageKey); }
    catch { setStorageError(true); }
  }
  function change(patch: Partial<Draft>) {
    setError(""); setNotice(""); setFlash(""); setQuantityIssue(undefined);
    store({ ...currentDraft.current, ...patch });
  }
  function fail(message: string) { setNotice(""); setError(message); }
  function select(article: Article) {
    change({ article, search: article.name + " · " + article.sku, quantity: "" });
    setShowSuggestions(false);
    quantityInput.current?.focus();
  }
  function markQuantity(id: string, message: string) {
    setError(""); setNotice(""); setQuantityIssue({ id, message });
    document.getElementById("receipt-quantity-" + id)?.focus();
  }

  function add(article: Article, raw: string) {
    const draft = currentDraft.current;
    const validation = quantityError(raw, article.unit);
    if (validation) { markQuantity("pending", validation); return; }
    const line = draft.lines.find(value => value.article.id === article.id);
    const added = parse(raw);
    if (line) {
      const error = quantityError(line.quantity, article.unit);
      if (error) { markQuantity(article.id, error); return; }
    }
    const total = line ? Math.round((parse(line.quantity) + added) * 1000) / 1000 : added;
    if (total > MAX_QUANTITY) { markQuantity(line ? article.id : "pending", "El máximo por línea es 1.000.000."); return; }
    if (!line && draft.lines.length >= MAX_LINES) { fail("La entrada admite hasta 200 líneas."); return; }
    const quantity = String(total).replace(".", ",");
    const lines = line ? draft.lines.map(value => value === line ? { article, quantity } : value)
      : [...draft.lines, { article, quantity }];
    store({ ...draft, branchId, lines, article: undefined, search: "", quantity: "" });
    setShowSuggestions(false);
    setError("");
    setQuantityIssue(undefined);
    setFlash(article.id);
    setNotice(line ? (added === 1 ? "Se sumó " : "Se sumaron ") + amount(added, article.unit) + ". Total: " + amount(total, article.unit) + "." : "Artículo agregado.");
    searchInput.current?.focus();
  }

  async function scan() {
    if (branch.problem || !term || scanning) return;
    if (draft.article) { quantityInput.current?.focus(); return; }
    setScanning(true);
    setShowSuggestions(false);
    try {
      const [match] = await receiptApi.items(term, true, undefined, branchId);
      if (match) {
        if (match.unit === "unidad") add(match, "1");
        else select(match);
      } else {
        const matches = await receiptApi.items(term, false, undefined, branchId);
        const exact = matches.find(article => article.sku.toLocaleLowerCase() === term.toLocaleLowerCase());
        if (exact) select(exact);
        else if (matches.length) { setSuggestions(matches); setShowSuggestions(true); }
        else fail("No encontramos un artículo activo con ese código o búsqueda.");
      }
    } catch (failure) { fail(failureMessage(failure)); }
    finally { setScanning(false); }
  }

  function reviewFailure(message: string) {
    fail(message);
    requestAnimationFrame(() => document.querySelector(".receipt-items [role=alert]")?.scrollIntoView({ block: "nearest" }));
  }

  function startReview() {
    if (branch.problem) { fail(branch.problem); return; }
    if (pending) { reviewFailure("Hay un artículo sin agregar. Agrégalo o limpia el artículo y la cantidad antes de revisar."); return; }
    if (!draft.reason) {
      setError(""); setNotice(""); setQuantityIssue(undefined); setReasonInvalid(true);
      reasonInput.current?.focus({ preventScroll: true });
      reasonInput.current?.scrollIntoView({ block: "center" });
      return;
    }
    if (draft.reason === "OTHER" && !draft.detail.trim()) { reviewFailure("Escribe el detalle del motivo Otro."); return; }
    for (const line of draft.lines) {
      const validation = quantityError(line.quantity, line.article.unit);
      if (validation) { markQuantity(line.article.id, validation); return; }
    }
    setError(""); setNotice(""); setReview(true);
  }

  async function confirm() {
    if (busy) return;
    const key = draft.requestKey ?? crypto.randomUUID();
    const pendingDraft = { ...draft, requestKey: key, branchId: draft.requestKey ? draft.branchId : branchId };
    if (!store(pendingDraft)) { fail("Permite el almacenamiento local para confirmar con recuperación ante fallos."); return; }
    setBusy(true); setError("");
    try {
      const receipt = await receiptApi.confirm({ requestKey: key, branchId: pendingDraft.branchId, reason: draft.reason, detail: draft.detail,
        reference: draft.reference, lines: draft.lines.map(line => ({ itemId: line.article.id, quantity: parse(line.quantity) })) });
      clear();
      saved(receipt);
    } catch (failure) {
      if (failure instanceof ApiError && (failure.status === 400 || (failure.status === 409 && !draft.requestKey))) {
        store({ ...pendingDraft, requestKey: undefined });
        setReview(false);
      }
      fail(failureMessage(failure));
    } finally { setBusy(false); }
  }

  return <section className={"catalog-page receipts-page" + (review ? "" : " receipt-editor")}>
    <div className="catalog-heading"><div><p className="catalog-eyebrow">Inventario / Entradas</p>
      <h1>{review ? "Revisar entrada" : "Nueva entrada"}</h1>
      <p className="subtitle">{review ? "Verifica los artículos antes de registrar el movimiento." : "Las existencias cambian al confirmar la entrada."}</p></div>
      <button onClick={close} disabled={busy || scanning}>Volver al listado</button>
    </div>
    {storageError && <p className="notice error" role="alert">El navegador no pudo guardar el borrador local.</p>}
    {review ? <>
      {draft.requestKey && <p className="receipt-note">Hay una confirmación pendiente. Puedes reintentar con los mismos datos.</p>}
      <section className="catalog-panel receipt-panel">
        <h2>Destino {branch.selected?.name ?? "Principal"}</h2>
        <p>{draft.reason && reasons[draft.reason]}{draft.reason === "OTHER" && " · " + draft.detail}</p>
        {draft.reference && <p>Referencia: {draft.reference}</p>}
        <ul className="receipt-review-lines">{draft.lines.map(line => <li key={line.article.id}>
          <span>{line.article.name}<small>{line.article.sku}</small></span>
          <strong>{amount(parse(line.quantity), line.article.unit)}</strong>
        </li>)}</ul>
      </section>
      {error && <p className="notice error" role="alert">{error}</p>}
      <div className="catalog-actions">
        {!draft.requestKey && <button onClick={() => setReview(false)} disabled={busy}>Volver a editar</button>}
        <button className="catalog-primary" onClick={() => void confirm()} disabled={busy}>{busy ? "Confirmando…" : draft.requestKey ? "Reintentar confirmación" : "Confirmar entrada"}</button>
      </div>
    </> : <>
      <section className="catalog-panel receipt-panel">
        <h2>Datos de la entrada</h2>
        <div className="receipt-fields">
          <label>Motivo obligatorio<select ref={reasonInput} value={draft.reason} aria-invalid={reasonInvalid}
            aria-describedby={reasonInvalid ? "receipt-reason-error" : undefined}
            onChange={event => { setReasonInvalid(false); change({ reason: event.target.value as Draft["reason"] }); }}>
            <option value="">Selecciona un motivo</option>{Object.entries(reasons).map(([key, value]) => <option key={key} value={key}>{value}</option>)}
          </select>{reasonInvalid && <small className="receipt-quantity-error" id="receipt-reason-error" role="alert">Selecciona el motivo de la entrada.</small>}</label>
          <label>Referencia opcional<input maxLength={100} value={draft.reference} onChange={event => change({ reference: event.target.value })} /></label>
          {draft.reason === "OTHER" && <label className="receipt-reason-detail">Detalle obligatorio<input maxLength={150} value={draft.detail} onChange={event => change({ detail: event.target.value })} /></label>}
        </div>
        <p className="receipt-note"><strong>Destino</strong> · Principal</p>
      </section>
      <section className="catalog-panel receipt-panel receipt-items">
        <h2>Artículos recibidos</h2>
        <MovementBranch state={branch} locked={draft.lines.length > 0 || !!draft.requestKey} onChange={id => {
          setSuggestions([]); setShowSuggestions(false);
          change({ branchId: id, article: undefined, search: "", quantity: "" });
        }} />
        <form className="receipt-add" onSubmit={(event: FormEvent) => { event.preventDefault(); if (draft.article) add(draft.article, draft.quantity); }}>
          <div className="receipt-item-search">
            <label>Buscar artículo<span className="catalog-search"><Search size={16} aria-hidden="true" />
              <input ref={searchInput} type="search" autoComplete="off" maxLength={160} value={draft.search}
                placeholder="Nombre, SKU o código de barras" readOnly={scanning}
                onFocus={() => { if (!draft.article) setShowSuggestions(true); }}
                onChange={event => { change({ search: event.target.value, article: undefined }); setShowSuggestions(true); }}
                onKeyDown={event => {
                  if (event.key === "Escape") setShowSuggestions(false);
                  if (event.key === "ArrowDown") { event.preventDefault(); results.current?.querySelector("button")?.focus(); }
                  if (event.key === "Enter") { event.preventDefault(); void scan(); }
                }} />
            </span></label>
            <small className="subtitle">Escanea un código de barras y pulsa Enter.</small>
            {showSuggestions && term && !draft.article && <div className="receipt-results" ref={results}>
              {searching ? <p role="status">Buscando…</p> : suggestions.length ? suggestions.map(article =>
                <button type="button" key={article.id} onClick={() => select(article)}><strong>{article.name}</strong><small>{article.sku}</small></button>
              ) : <p>No encontramos artículos.</p>}
            </div>}
          </div>
          <div className="receipt-quantity">
            <label>Cantidad<input id="receipt-quantity-pending" ref={quantityInput} aria-invalid={quantityIssue?.id === "pending"} aria-describedby={quantityIssue?.id === "pending" ? "receipt-error-pending" : undefined} inputMode="decimal" autoComplete="off" value={draft.quantity}
              onChange={event => change({ quantity: event.target.value })} /></label>
            {draft.article && <><span>{draft.article.unit}</span><small>Existencia actual: {amount(draft.article.balance, draft.article.unit)}</small></>}
            {quantityIssue?.id === "pending" && <small className="receipt-quantity-error" id="receipt-error-pending" role="alert">{quantityIssue.message}</small>}
          </div>
          <button type="submit" disabled={!draft.article || scanning}><Plus size={16} /> Agregar línea</button>
          {pending && <button type="button" onClick={() => change({ article: undefined, search: "", quantity: "" })}>Limpiar artículo</button>}
        </form>
        {error && <p className="notice error" role="alert">{error}</p>}
        {notice && !error && <p className="notice receipt-success" role="status">{notice}</p>}
        {draft.lines.length ? <table className="receipt-table"><thead><tr><th>Artículo</th><th>Cantidad</th><th>Acción</th></tr></thead>
          <tbody>{draft.lines.map(line => <tr key={line.article.id} className={quantityIssue?.id === line.article.id ? "receipt-invalid-line" : flash === line.article.id ? "receipt-flash" : undefined}>
            <td><strong>{line.article.name}</strong><small>{line.article.sku}</small></td>
            <td><input id={"receipt-quantity-" + line.article.id} aria-label={"Cantidad de " + line.article.sku}
              aria-invalid={quantityIssue?.id === line.article.id} aria-describedby={quantityIssue?.id === line.article.id ? "receipt-error-" + line.article.id : undefined} inputMode="decimal" value={line.quantity}
              onChange={event => change({ lines: draft.lines.map(value => value === line ? { ...line, quantity: event.target.value } : value) })} /><small>{line.article.unit}</small>
              {quantityIssue?.id === line.article.id && <small className="receipt-quantity-error" id={"receipt-error-" + line.article.id} role="alert">{quantityIssue.message}</small>}</td>
            <td><button type="button" aria-label={"Quitar " + line.article.sku} onClick={() => change({ lines: draft.lines.filter(value => value !== line) })}><X size={17} /></button></td>
          </tr>)}</tbody></table> : <p className="subtitle">Todavía no agregaste artículos.</p>}
      </section>
      {discard ? <section ref={discardPanel} tabIndex={-1} aria-labelledby="receipt-discard-title" className="catalog-panel receipt-panel"><h2 id="receipt-discard-title">Descartar entrada</h2><p>Se borrará la entrada en curso de este navegador.</p>
        <div className="catalog-actions"><button onClick={() => setDiscard(false)}>Volver</button>
          <button className="receipt-danger" onClick={() => { clear(); close(); }}>Descartar entrada</button></div></section>
        : <div className="catalog-actions receipt-editor-actions"><button disabled={scanning} onClick={() => setDiscard(true)}>Descartar</button>
          <button className="catalog-primary" disabled={!!branch.problem || scanning || (!draft.lines.length && !pending)} onClick={startReview}>Revisar entrada</button></div>}
    </>}
  </section>;
}

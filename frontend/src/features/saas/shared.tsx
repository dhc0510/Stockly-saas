import { useEffect, useLayoutEffect, useRef, type FormEvent, type ReactNode, type TextareaHTMLAttributes } from "react";
import { X } from "lucide-react";
import { ApiError } from "../../lib/http";
import { stateLabel, type SubscriptionState } from "./api";
import "./saas.css";

export function StateBadge({ state }:{ state:SubscriptionState }) {
  return <span className={`saas-badge ${state.toLowerCase()}`}>{stateLabel[state]}</span>;
}

/** Campo de texto que crece con su contenido y nunca muestra barra de desplazamiento. */
export function AutoTextarea(props:TextareaHTMLAttributes<HTMLTextAreaElement>) {
  const ref = useRef<HTMLTextAreaElement>(null);
  // Mide solo cuando el campo es visible: dentro de una ventana que aún no se abrió mide 0 y quedaría cortado.
  const fit = () => {
    const t = ref.current; if (!t || !t.offsetParent) return;
    t.style.height = "auto"; t.style.height = `${t.scrollHeight + 2}px`;
  };
  useLayoutEffect(fit, [props.value, props.defaultValue]);
  useEffect(() => {
    const t = ref.current; if (!t) return;
    // Al aparecer o cambiar de ancho (ventana modal, celular) vuelve a medir; se compara el ancho para no entrar en bucle.
    let width = 0;
    const observer = new ResizeObserver(entries => { const w = entries[0].contentRect.width; if (w !== width) { width = w; fit(); } });
    observer.observe(t); requestAnimationFrame(fit);
    return () => observer.disconnect();
  }, []);
  return <textarea {...props} ref={ref} className={`auto-textarea ${props.className ?? ""}`} rows={props.rows ?? 3}
    onInput={e => { fit(); props.onInput?.(e); }}/>;
}

/** Ventana modal accesible con <dialog>: foco atrapado, Escape cierra salvo mientras guarda. */
export function Dialog({ title, subtitle, busy, onClose, onSubmit, children, actions, wide }:{
  title:string; subtitle?:string; busy?:boolean; onClose:()=>void; onSubmit:(e:FormEvent)=>void; children:ReactNode; actions:ReactNode; wide?:boolean }) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const element = ref.current!; const previous = document.activeElement as HTMLElement | null;
    element.showModal(); const overflow = document.body.style.overflow; document.body.style.overflow = "hidden";
    return () => { element.close(); document.body.style.overflow = overflow; previous?.focus(); };
  }, []);
  return <dialog ref={ref} className={`confirmation saas-dialog${wide ? " wide" : ""}`} aria-labelledby="saas-dialog-title"
    onCancel={e => { e.preventDefault(); if (!busy) onClose(); }}>
    <form onSubmit={onSubmit} noValidate>
      <header className="dialog-header"><span>Stockly<span className="brand-dot">.</span></span>
        <button type="button" className="icon-button" aria-label="Cerrar" onClick={onClose} disabled={busy}><X size={19}/></button></header>
      <div className="dialog-body saas-dialog-body">
        {subtitle && <p className="eyebrow">{subtitle}</p>}
        <h2 id="saas-dialog-title">{title}</h2>
        {children}
      </div>
      <footer className="dialog-actions">{actions}</footer>
    </form>
  </dialog>;
}

export function fieldError(error:unknown, field:string) {
  const text = error instanceof ApiError ? error.fieldErrors[field] : undefined;
  return text ? <span className="saas-field-error" role="alert">{text}</span> : null;
}

/** Error general del formulario cuando no corresponde a un campo concreto. */
export function GeneralError({ error }:{ error:unknown }) {
  if (!error) return null;
  if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) return null;
  return <div className="notice error saas-notice" role="alert">{errorMessage(error)}</div>;
}

export function errorMessage(error:unknown) {
  return error instanceof Error ? error.message : "No se pudo completar la operación.";
}

export function newRequestKey() {
  return typeof crypto !== "undefined" && "randomUUID" in crypto ? crypto.randomUUID()
    : "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, c => { const r = Math.random() * 16 | 0; return (c === "x" ? r : (r & 3) | 8).toString(16); });
}

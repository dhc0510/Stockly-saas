// STK-017 · Períodos en días calendario de Costa Rica (AAAA-MM-DD).
export type Preset = "today" | "yesterday" | "last7" | "month" | "previousMonth" | "custom";
export const MAX_DAYS = 366;

export const presets: { key:Preset; label:string }[] = [
  { key: "today", label: "Hoy" }, { key: "yesterday", label: "Ayer" }, { key: "last7", label: "Últimos 7 días" },
  { key: "month", label: "Este mes" }, { key: "previousMonth", label: "Mes anterior" }, { key: "custom", label: "Personalizado" },
];

/** Fecha de hoy en Costa Rica. */
export function todayCR(now = new Date()):string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Costa_Rica", year: "numeric", month: "2-digit", day: "2-digit" }).format(now);
}

const parse = (d:string) => { const [y, m, day] = d.split("-").map(Number); return new Date(Date.UTC(y, m - 1, day)); };
const iso = (d:Date) => d.toISOString().slice(0, 10);
export const addDays = (d:string, n:number) => { const x = parse(d); x.setUTCDate(x.getUTCDate() + n); return iso(x); };
export const daysBetween = (from:string, to:string) => Math.round((parse(to).getTime() - parse(from).getTime()) / 86_400_000) + 1;

export function presetRange(preset:Exclude<Preset, "custom">, today:string):{ from:string; to:string } {
  const t = parse(today);
  switch (preset) {
    case "today": return { from: today, to: today };
    case "yesterday": { const y = addDays(today, -1); return { from: y, to: y }; }
    case "last7": return { from: addDays(today, -6), to: today };
    case "month": return { from: iso(new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth(), 1))), to: today };
    case "previousMonth": return { from: iso(new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth() - 1, 1))), to: iso(new Date(Date.UTC(t.getUTCFullYear(), t.getUTCMonth(), 0))) };
  }
}

/** Mismo criterio que el servidor; devuelve el mensaje para mostrar en el filtro o null si el período es válido. */
export function periodError(from:string, to:string, today:string):string | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(from) || !/^\d{4}-\d{2}-\d{2}$/.test(to)) return "Elige las dos fechas del período.";
  if (from > today || to > today) return "No se aceptan fechas futuras.";
  if (from > to) return "La fecha «Desde» debe ser igual o anterior a «Hasta».";
  if (daysBetween(from, to) > MAX_DAYS) return "El período puede ser de hasta 366 días.";
  return null;
}

/** Variación contra el período anterior, redondeada a entero; null si el anterior es 0. */
export function variation(current:number, previous:number):number | null {
  if (!previous) return null;
  return Math.round((current - previous) / Math.abs(previous) * 100);
}

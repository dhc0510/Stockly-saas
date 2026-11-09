/** STK-016: vista previa de «A pedir». El servidor hace el cálculo oficial; esta función solo lo anticipa en la ventana de configuración. */
export type Suggestion = { toOrder:number|null; basis:"TARGET"|"RECOVERY"|"NONE" };

// Milésimas: el saldo de inventario admite 3 decimales (STK-008); la sugerencia es exacta, sin redondear.
const cents = (value:number) => Math.round(value * 1000);

export function suggestOrder(available:number, minimum:number|null, recoveryMargin:number, target:number|null): Suggestion {
  let result:number|null; let basis:Suggestion["basis"];
  if (target != null) { result = cents(target) - cents(available); basis = "TARGET"; }
  else if (minimum != null) { result = cents(minimum) + cents(recoveryMargin) - cents(available); basis = "RECOVERY"; }
  else { result = null; basis = "NONE"; }
  return { toOrder: result == null ? null : Math.max(0, result) / 1000, basis };
}

/** Mismo criterio que el servidor: opcional, mayor que cero, mayor que el mínimo y con hasta 2 decimales. */
export function targetError(raw:string, minimum:number|null, formatQuantity:(value:number)=>string): string {
  const text = raw.trim();
  if (!text) return "";
  const value = Number(text);
  if (!Number.isFinite(value) || value <= 0 || !/^\d+(\.\d{1,2})?$/.test(text) || value > 9999999999999.99)
    return "El nivel deseado debe ser una cantidad mayor que cero, con hasta 2 decimales.";
  if (minimum != null && value <= minimum) return `El nivel deseado debe ser mayor que el mínimo (${formatQuantity(minimum)}).`;
  return "";
}

/** Avisa al contador del menú «Por reponer» que cambió la configuración de reposición. */
export const restockChanged = () => window.dispatchEvent(new Event("stockly:restock-changed"));

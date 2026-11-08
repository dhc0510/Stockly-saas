// STK-012: Stockly solo registra cobros y devoluciones; los montos se calculan en céntimos para evitar redondeos.
export type Method = "CASH" | "CARD" | "SINPE" | "TRANSFER";
export const METHODS: Method[] = ["CASH", "CARD", "SINPE", "TRANSFER"];
export const methodLabels: Record<Method, string> = { CASH: "Efectivo", CARD: "Tarjeta", SINPE: "SINPE Móvil", TRANSFER: "Transferencia" };
export const methodHints: Record<Method, string> = {
  CASH: "Escribe el monto recibido; Stockly calcula el vuelto.",
  CARD: "Cobrada en un datáfono externo. Stockly no guarda datos de la tarjeta.",
  SINPE: "Pago de celular a celular.",
  TRANSFER: "Transferencia bancaria.",
};
export type MethodState = { method: Method; label: string; active: boolean };
export type PaymentDraft = { method: Method; amount: string; reference: string };
export type Payment = { method: Method; amount: number; received: number | null; reference: string | null };
export type Refund = { method: Method; amount: number; reference: string | null };
export type PaymentState = "paid" | "voided" | "none";
export const paymentStateLabels: Record<PaymentState, string> = { paid: "Cobrada", voided: "Cobro anulado", none: "Sin cobro registrado" };

/** Convierte "1.234,5" o "1234.50" en céntimos; null si no es un monto positivo con hasta dos decimales. */
export function cents(value: string): bigint | null {
  const clean = value.trim().replace(/\s/g, "");
  // En Costa Rica la coma es decimal: si aparece, los puntos se toman como separadores de miles.
  const normalized = clean.includes(",") ? clean.replace(/\./g, "").replace(",", ".") : clean;
  if (!/^\d+(\.\d{1,2})?$/.test(normalized)) return null;
  const [whole, decimal = ""] = normalized.split(".");
  const result = BigInt(whole) * 100n + BigInt(decimal.padEnd(2, "0"));
  return result > 0n ? result : null;
}

export const toAmount = (value: bigint) => (Number(value) / 100).toFixed(2);
export const toInput = (value: bigint) => toAmount(value).replace(".", ",").replace(/,00$/, "");

export type Balance = { paid: bigint; due: bigint; change: bigint; other: bigint };
/** Total pagado, pendiente y vuelto; los pagos con monto inválido no cuentan. */
export function balance(total: bigint, payments: PaymentDraft[]): Balance {
  let paid = 0n, other = 0n;
  for (const payment of payments) {
    const value = cents(payment.amount) ?? 0n;
    paid += value;
    if (payment.method !== "CASH") other += value;
  }
  return { paid, other, due: paid >= total ? 0n : total - paid, change: paid > total ? paid - total : 0n };
}

/** Mensaje para un pago nuevo, o vacío si se puede agregar. */
export function paymentProblem(total: bigint, payments: PaymentDraft[], next: PaymentDraft): string {
  const value = cents(next.amount);
  if (value === null) return "Ingresa un monto positivo de hasta dos decimales.";
  const { due } = balance(total, payments);
  if (due === 0n) return "La venta ya está cubierta. Quita un pago para cambiarlo.";
  if (next.method !== "CASH" && value > due)
    return methodLabels[next.method] + " no puede superar el pendiente. Solo el efectivo da vuelto.";
  return "";
}

/** Igual que el servidor: el vuelto se descuenta del efectivo empezando por el último pago en efectivo. */
export function applied(total: bigint, payments: PaymentDraft[]): bigint[] {
  let change = balance(total, payments).change;
  const result = payments.map(payment => cents(payment.amount) ?? 0n);
  for (let index = payments.length - 1; index >= 0; index--) {
    if (payments[index].method !== "CASH") continue;
    const discount = change < result[index] ? change : result[index];
    result[index] -= discount;
    change -= discount;
  }
  return result;
}

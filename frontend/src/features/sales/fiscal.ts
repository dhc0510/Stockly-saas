// STK-013 · Comprobantes electrónicos de la venta (Anexos v4.4 de Hacienda).
export type FiscalStatus = "PENDING" | "PROCESSING" | "ACCEPTED" | "REJECTED" | "CANCELLED";
export type FiscalType = "01" | "03" | "04";
export type FiscalDocument = {
  saleReturnId?: string | null;
  id: string; docType: FiscalType; environment: "TEST" | "PROD"; consecutive: string; key: string; issuedAt: string;
  status: FiscalStatus; reason: string | null; attempts: number; sentAt: string | null; resolvedAt: string | null;
  referenceId: string | null; replacesId: string | null; mailStatus: "NONE" | "PENDING" | "SENT" | "FAILED"; branchCode: string;
  /** Situación de la clave: "1" normal, "3" sin internet (SPEC §13). */
  situation?: "1" | "2" | "3"; lateSubmission?: boolean;
};

export const fiscalTypeLabels: Record<FiscalType, string> = { "01": "Factura electrónica", "03": "Nota de crédito", "04": "Tiquete electrónico" };
export const fiscalShortLabels: Record<FiscalType, string> = { "01": "Factura", "03": "Nota de crédito", "04": "Tiquete" };
export const fiscalStatusLabels: Record<FiscalStatus | "NONE", string> = {
  PENDING: "Pendiente de envío", PROCESSING: "Enviado, en revisión", ACCEPTED: "Aceptado", REJECTED: "Rechazado",
  CANCELLED: "Cancelado", NONE: "Sin comprobante",
};
export const fiscalTone: Record<FiscalStatus | "NONE", string> = {
  PENDING: "wait", PROCESSING: "wait", ACCEPTED: "ok", REJECTED: "error", CANCELLED: "muted", NONE: "muted",
};

/** Nota 4: tipos de identificación del receptor que usa Stockly y sus dígitos. */
export const ID_TYPES: { code: string; label: string; digits: number[] }[] = [
  { code: "01", label: "Cédula física", digits: [9] },
  { code: "02", label: "Cédula jurídica", digits: [10] },
  { code: "03", label: "DIMEX", digits: [11, 12] },
  { code: "04", label: "NITE", digits: [10] },
];
export const idTypeLabel = (code: string | null | undefined) => ID_TYPES.find(type => type.code === code)?.label ?? "";

/** Mensaje si el número no corresponde al tipo; vacío si es válido. */
export function idProblem(type: string, number: string) {
  const definition = ID_TYPES.find(item => item.code === type);
  if (!definition) return "";
  const digits = number.replace(/\D/g, "");
  return definition.digits.includes(digits.length) ? "" : definition.label + ": el número lleva " + definition.digits.join(" u ") + " dígitos.";
}

/** Comprobante vigente: el último que no es nota de crédito. */
export const currentDocument = (documents: FiscalDocument[]) => [...documents].reverse().find(doc => doc.docType !== "03");
export const creditNoteOf = (documents: FiscalDocument[], original?: FiscalDocument) =>
  original ? [...documents].reverse().find(doc => doc.referenceId === original.id) : undefined;

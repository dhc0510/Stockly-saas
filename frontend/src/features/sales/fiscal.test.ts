import { describe, expect, it } from "vitest";
import { creditNoteOf, currentDocument, idProblem, type FiscalDocument } from "./fiscal";

const doc = (id: string, docType: FiscalDocument["docType"], referenceId: string | null = null) =>
  ({ id, docType, referenceId } as FiscalDocument);

describe("comprobantes electrónicos", () => {
  it("valida los dígitos de cada tipo de identificación (nota 4)", () => {
    expect(idProblem("01", "1-0987-0654")).toBe("");
    expect(idProblem("01", "12345")).toContain("9 dígitos");
    expect(idProblem("02", "3-101-654321")).toBe("");
    expect(idProblem("03", "123456789012")).toBe("");
    expect(idProblem("03", "1234567890")).toContain("11 u 12");
    expect(idProblem("", "")).toBe("");
  });

  it("toma el último comprobante vigente y su nota de crédito", () => {
    const docs = [doc("a", "01"), doc("b", "01"), doc("c", "03", "b")];
    expect(currentDocument(docs)?.id).toBe("b");
    expect(creditNoteOf(docs, currentDocument(docs))?.id).toBe("c");
    expect(currentDocument([])).toBeUndefined();
  });
});

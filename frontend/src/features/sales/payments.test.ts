import { describe, expect, it } from "vitest";
import { applied, balance, cents, paymentProblem, toInput, type PaymentDraft } from "./payments";

const pay = (method: PaymentDraft["method"], amount: string): PaymentDraft => ({ method, amount, reference: "" });

describe("cobros", () => {
  it("lee montos con coma o punto decimal y rechaza los inválidos", () => {
    expect(cents("13251,75")).toBe(1325175n);
    expect(cents("13.251,75")).toBe(1325175n);
    expect(cents("13251.75")).toBe(1325175n);
    expect(cents(" 5000 ")).toBe(500000n);
    expect(cents("0")).toBeNull();
    expect(cents("10,001")).toBeNull();
    expect(cents("-5")).toBeNull();
    expect(toInput(1325175n)).toBe("13251,75");
  });

  it("calcula pendiente, vuelto y monto aplicado en un pago mixto", () => {
    const total = 1325175n;
    const payments = [pay("CARD", "5000"), pay("CASH", "10000")];
    expect(balance(total, payments)).toMatchObject({ paid: 1500000n, due: 0n, change: 174825n });
    expect(applied(total, payments)).toEqual([500000n, 825175n]);
    expect(balance(total, [pay("CARD", "5000")]).due).toBe(825175n);
  });

  it("solo el efectivo da vuelto", () => {
    const total = 1325175n;
    expect(paymentProblem(total, [], pay("CARD", "20000"))).toContain("no puede superar el pendiente");
    expect(paymentProblem(total, [], pay("CASH", "20000"))).toBe("");
    expect(paymentProblem(total, [pay("CASH", "20000")], pay("SINPE", "1"))).toContain("ya está cubierta");
    expect(paymentProblem(total, [], pay("SINPE", "abc"))).toContain("monto positivo");
  });
});

import { describe, expect, it } from "vitest";
import { cobroSchema } from "./cobro";

describe("solicitud de cobro", () => {
  const schema = cobroSchema(41.5, [1, 2]);

  it.each(["", "0", "0,00", "-1", "1.001", "1e1", "100000000", "41,51"])(
    "rechaza %s",
    (monto) => {
      expect(schema.safeParse({ monto, metodoPagoId: "1" }).success).toBe(
        false,
      );
    },
  );

  it("admite cobros parciales y el saldo completo con precisión decimal", () => {
    expect(schema.parse({ monto: "0,01", metodoPagoId: "2" })).toEqual({
      monto: 0.01,
      metodoPagoId: 2,
    });
    expect(schema.parse({ monto: "41.50", metodoPagoId: "1" })).toEqual({
      monto: 41.5,
      metodoPagoId: 1,
    });
    expect(
      cobroSchema(99999999.99, [1]).parse({
        monto: "99999999,99",
        metodoPagoId: "1",
      }).monto,
    ).toBe(99999999.99);
  });

  it("rechaza métodos ausentes del catálogo", () => {
    expect(schema.safeParse({ monto: "1", metodoPagoId: "9" }).success).toBe(
      false,
    );
  });
});

import { describe, expect, it } from "vitest";
import { servicioSchema } from "./servicio";

const base = { nombre: "Lavado", precio: "9,95", modalidadCobro: "KILO" };
describe("ServicioRequest", () => {
  it("envía solamente los campos del DTO y un precio numérico", () => {
    expect(servicioSchema.parse({ ...base, activo: false })).toEqual({
      nombre: "Lavado",
      precio: 9.95,
      modalidadCobro: "KILO",
    });
  });

  it.each(["", "0", "-1", "1.001", "100000000", "1e2", "1,2.3"])(
    "rechaza el precio %j",
    (precio) => {
      expect(servicioSchema.safeParse({ ...base, precio }).success).toBe(false);
    },
  );

  it("acepta el máximo y valida nombre y modalidad", () => {
    expect(
      servicioSchema.parse({
        ...base,
        nombre: "x".repeat(100),
        precio: "99999999.99",
      }).precio,
    ).toBe(99999999.99);
    expect(
      servicioSchema.safeParse({ ...base, nombre: "x".repeat(101) }).success,
    ).toBe(false);
    expect(
      servicioSchema.safeParse({ ...base, modalidadCobro: "POR_HORA" }).success,
    ).toBe(false);
    expect(servicioSchema.safeParse({ nombre: base.nombre }).success).toBe(
      false,
    );
  });
});

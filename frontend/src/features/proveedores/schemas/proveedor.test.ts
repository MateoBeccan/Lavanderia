import { describe, expect, it } from "vitest";
import { cuitSchema, proveedorSchema, reactivacionSchema } from "./proveedor";

describe("validación de proveedores", () => {
  it.each(["30123456781", "30-12345678-1"])("acepta el CUIT %s", (cuit) => {
    expect(cuitSchema.parse(cuit)).toBe(cuit);
  });

  it.each([
    "30-1234-5678-1",
    " 30123456781",
    "30123456781\n",
    "30123456789",
    "3012345678",
  ])("rechaza el CUIT %j", (cuit) =>
    expect(cuitSchema.safeParse(cuit).success).toBe(false),
  );

  const proveedor = {
    nombre: "  Distribuidora  ",
    cuit: "30-12345678-1",
    telefono: "  011-5432  ",
    rubro: "  ",
  };

  it("recorta datos y convierte el rubro en blanco en null", () => {
    expect(proveedorSchema.parse(proveedor)).toEqual({
      nombre: "Distribuidora",
      cuit: proveedor.cuit,
      telefono: "011-5432",
      rubro: null,
    });
  });

  it("respeta obligatoriedad y límites del DTO", () => {
    const parse = (valores: Partial<typeof proveedor>) =>
      proveedorSchema.safeParse({ ...proveedor, ...valores }).success;
    expect(
      parse({
        nombre: "x".repeat(150),
        telefono: "x".repeat(20),
        rubro: "x".repeat(50),
      }),
    ).toBe(true);
    for (const valores of [
      { nombre: " " },
      { nombre: "x".repeat(151) },
      { telefono: " " },
      { telefono: "x".repeat(21) },
      { rubro: "x".repeat(51) },
    ])
      expect(parse(valores)).toBe(false);
  });

  it("acepta un ID positivo representable como Long sin perder precisión", () => {
    expect(reactivacionSchema.parse({ id: " 9223372036854775807 " })).toEqual({
      id: "9223372036854775807",
    });
    for (const id of ["9223372036854775808", "0", "-1", "1.5", "1e3", "01"]) {
      expect(reactivacionSchema.safeParse({ id }).success).toBe(false);
    }
  });
});

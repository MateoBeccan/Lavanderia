import { describe, expect, it } from "vitest";
import { compraSchema, solicitudCompra, subtotalEstimado } from "./compra";

const fila = { insumoId: 3, cantidad: " 1,25 ", precioCompra: "12,34" };
const valores = {
  proveedorId: 2,
  numeroComprobante: " FAC-3 ",
  detalles: [fila],
};

describe("contrato de compra", () => {
  it("conserva el detalle y serializa los decimales sin hacer aritmética binaria", () => {
    const formulario = { ...valores, detalles: [{ ...fila, detalleId: 8 }] };
    expect(solicitudCompra(compraSchema.parse(formulario))).toEqual({
      proveedorId: 2,
      numeroComprobante: "FAC-3",
      detalles: [
        { insumoId: 3, detalleId: 8, cantidad: "1.25", precioCompra: "12.34" },
      ],
    });
    expect(subtotalEstimado("1,25", "12,34")).toBe(1543n);
    expect(subtotalEstimado("99999999,99", "99999999,99")).toBe(
      999999999800000000n,
    );
  });

  it.each(["", "0", "0,00", "-1", "1e3", "0,001", "100000000", "Infinity"])(
    "rechaza el decimal %s en cantidad y precio",
    (decimal) => {
      for (const campo of ["cantidad", "precioCompra"]) {
        expect(
          compraSchema.safeParse({
            ...valores,
            detalles: [{ ...fila, [campo]: decimal }],
          }).success,
        ).toBe(false);
      }
    },
  );

  it("rechaza un proveedor o insumo ausente, lista vacía, comprobante largo y duplicados", () => {
    for (const formulario of [
      { ...valores, proveedorId: null },
      { ...valores, detalles: [] },
      { ...valores, detalles: [{ ...fila, insumoId: null }] },
      { ...valores, detalles: [fila, fila] },
      { ...valores, numeroComprobante: "x".repeat(51) },
    ])
      expect(compraSchema.safeParse(formulario).success).toBe(false);
  });

  it("valida el rango del subtotal redondeado y del total", () => {
    expect(
      compraSchema.safeParse({
        ...valores,
        detalles: [{ ...fila, cantidad: "0,01", precioCompra: "0,49" }],
      }).success,
    ).toBe(false);
    const maximo = { ...fila, cantidad: "1", precioCompra: "99999999,99" };
    expect(
      compraSchema.safeParse({ ...valores, detalles: [maximo] }).success,
    ).toBe(true);
    expect(
      compraSchema.safeParse({
        ...valores,
        detalles: [{ ...maximo, cantidad: "2" }],
      }).success,
    ).toBe(false);
    expect(
      compraSchema.safeParse({
        ...valores,
        detalles: [maximo, { ...fila, insumoId: 4 }],
      }).error?.issues,
    ).toContainEqual(expect.objectContaining({ path: ["detalles", "root"] }));
  });
});

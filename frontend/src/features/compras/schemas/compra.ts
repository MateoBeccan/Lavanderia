import { z } from "zod";
import type { CompraRequest } from "../types/compras";

const MAXIMO = 9_999_999_999n;
const decimal = /^\d{1,8}(?:[.,]\d{1,2})?$/;
const id = z.number().int().positive();

export function centesimos(valor: string) {
  if (!decimal.test(valor)) return 0n;
  const [entero, fraccion = ""] = valor.replace(",", ".").split(".");
  return BigInt(entero) * 100n + BigInt(fraccion.padEnd(2, "0"));
}

export function subtotalEstimado(cantidad: string, precio: string) {
  return (centesimos(cantidad) * centesimos(precio) + 50n) / 100n;
}

const decimalPositivo = z
  .string()
  .trim()
  .refine(
    (valor) => centesimos(valor) > 0n,
    "Ingresá un valor positivo con hasta 8 enteros y 2 decimales.",
  );

export const compraSchema = z
  .object({
    proveedorId: id
      .nullable()
      .refine((valor) => valor !== null, "Seleccioná un proveedor activo."),
    numeroComprobante: z.string().trim().max(50, "Usá hasta 50 caracteres."),
    detalles: z
      .array(
        z.object({
          detalleId: id.optional(),
          insumoId: id
            .nullable()
            .refine((valor) => valor !== null, "Seleccioná un insumo."),
          cantidad: decimalPositivo,
          precioCompra: decimalPositivo,
        }),
      )
      .min(1, "Agregá al menos un renglón."),
  })
  .superRefine(({ detalles }, ctx) => {
    const elegidos = new Set<number>();
    let total = 0n;
    detalles.forEach((fila, index) => {
      if (fila.insumoId !== null) {
        if (elegidos.has(fila.insumoId)) {
          ctx.addIssue({
            code: "custom",
            path: ["detalles", index, "insumoId"],
            message: "Este insumo ya está en la compra.",
          });
        }
        elegidos.add(fila.insumoId);
      }
      if (
        centesimos(fila.cantidad) === 0n ||
        centesimos(fila.precioCompra) === 0n
      )
        return;
      const subtotal = subtotalEstimado(fila.cantidad, fila.precioCompra);
      total += subtotal;
      if (subtotal === 0n || subtotal > MAXIMO) {
        ctx.addIssue({
          code: "custom",
          path: ["detalles", index, "precioCompra"],
          message: "El subtotal debe quedar entre 0,01 y 99.999.999,99.",
        });
      }
    });
    if (total > MAXIMO) {
      ctx.addIssue({
        code: "custom",
        path: ["detalles", "root"],
        message: "El total supera 99.999.999,99.",
      });
    }
  });

export type CompraFormValues = z.input<typeof compraSchema>;
export type CompraValidada = z.output<typeof compraSchema>;
export const filaVacia = { insumoId: null, cantidad: "", precioCompra: "" };

export function solicitudCompra(values: CompraValidada): CompraRequest {
  return {
    proveedorId: values.proveedorId,
    numeroComprobante: values.numeroComprobante || null,
    detalles: values.detalles.map(({ cantidad, precioCompra, ...fila }) => ({
      ...fila,
      cantidad: cantidad.replace(",", "."),
      precioCompra: precioCompra.replace(",", "."),
    })),
  };
}

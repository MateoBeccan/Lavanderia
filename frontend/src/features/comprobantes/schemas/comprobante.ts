import { z } from "zod";
import { hoyPedidos } from "@/features/pedidos/rules";

export const comprobanteSchema = z.object({
  fechaEmision: z.iso
    .date("Ingresá una fecha válida.")
    .refine((fecha) => !fecha.startsWith("0000"), "Ingresá una fecha válida.")
    .refine(
      (fecha) => fecha <= hoyPedidos(),
      "La fecha de emisión no puede ser futura.",
    ),
  observaciones: z
    .string()
    .transform((valor) => valor.trim() || null)
    .refine(
      (valor) => valor === null || Array.from(valor).length <= 200,
      "Usá hasta 200 caracteres.",
    ),
});

export type CamposComprobante = z.input<typeof comprobanteSchema>;

const id = z.int().positive();
const resumen = z.object({
  id,
  tipoOperacion: z.enum(["COBRO", "PAGO_PROVEEDOR"]),
  fechaEmision: z.iso.date(),
  observaciones: z.string().nullable(),
  montoOperacion: z.number(),
});

export const paginaComprobantesSchema = z.object({
  content: z.array(resumen),
  page: z.object({
    size: z.int().positive(),
    number: z.int().nonnegative(),
    totalElements: z.int().nonnegative(),
    totalPages: z.int().nonnegative(),
  }),
});

const camposDetalle = {
  ...resumen.shape,
  metodoPagoCodigo: z.string(),
  metodoPagoNombre: z.string(),
  movimientoCajaId: id,
};

export const detalleComprobanteSchema = z.discriminatedUnion("tipoOperacion", [
  z.object({
    ...camposDetalle,
    tipoOperacion: z.literal("COBRO"),
    cobroId: id,
    pedidoId: id,
    pagoProveedorId: z.null(),
    compraId: z.null(),
  }),
  z.object({
    ...camposDetalle,
    tipoOperacion: z.literal("PAGO_PROVEEDOR"),
    cobroId: z.null(),
    pedidoId: z.null(),
    pagoProveedorId: id,
    compraId: id,
  }),
]);

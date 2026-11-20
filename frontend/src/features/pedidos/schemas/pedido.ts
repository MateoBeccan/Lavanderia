import { z } from "zod";
import { modalidadesCobro } from "@/features/servicios/types/servicios";
import { hoyPedidos } from "../rules";
import type { Pedido } from "../types/pedidos";

const id = z.int().positive("Seleccioná una opción.");
const selector = z
  .union([id, z.null()])
  .refine((valor) => valor !== null, "Seleccioná una opción.");
const detalle = z
  .object({
    detalleId: id.optional(),
    prendaId: selector,
    servicioId: selector,
    modalidadCobro: z.enum(modalidadesCobro).optional(),
    prendaNombre: z.string().optional(),
    servicioNombre: z.string().optional(),
    cantidad: z
      .string()
      .trim()
      .regex(
        /^\d{1,8}([.,]\d{1,2})?$/,
        "Ingresá hasta 8 enteros y 2 decimales.",
      )
      .refine(
        (valor) => /[1-9]/.test(valor),
        "La cantidad debe ser mayor que cero.",
      ),
    estadoPrenda: z.string().max(50, "Usá hasta 50 caracteres."),
    observaciones: z.string().max(250, "Usá hasta 250 caracteres."),
  })
  .superRefine((linea, ctx) => {
    if (
      linea.modalidadCobro &&
      linea.modalidadCobro !== "KILO" &&
      /[.,]\d*[1-9]/.test(linea.cantidad)
    ) {
      ctx.addIssue({
        code: "custom",
        path: ["cantidad"],
        message: "La cantidad debe ser entera para este servicio.",
      });
    }
  });
export const lineaVacia = {
  prendaId: null,
  servicioId: null,
  cantidad: "1",
  estadoPrenda: "",
  observaciones: "",
};
export const pedidoSchema = (editar: boolean) =>
  z
    .object({
      clienteId: selector,
      clienteNombre: z.string(),
      fechaEntregaEstimada: z.iso
        .date("Ingresá una fecha válida.")
        .refine(
          (fecha) => fecha >= hoyPedidos(),
          "La entrega debe ser hoy o posterior.",
        ),
      detalles: z.array(detalle).min(1, "Agregá al menos un renglón."),
    })
    .transform(
      ({ clienteId, clienteNombre, detalles, fechaEntregaEstimada }) => ({
        clienteId: editar ? undefined : clienteId,
        clienteNombre,
        fechaEntregaEstimada,
        detalles: detalles.map(
          ({
            cantidad,
            detalleId,
            prendaId,
            servicioId,
            estadoPrenda,
            observaciones,
            prendaNombre,
            servicioNombre,
            modalidadCobro,
          }) => ({
            detalleId,
            prendaId,
            servicioId,
            cantidad: Number(cantidad.replace(",", ".")),
            estadoPrenda: estadoPrenda || null,
            observaciones: observaciones || null,
            prendaNombre,
            servicioNombre,
            modalidadCobro,
          }),
        ),
      }),
    );
export type PedidoValores = z.input<ReturnType<typeof pedidoSchema>>;
export type PedidoValidado = z.output<ReturnType<typeof pedidoSchema>>;
type DetalleRequest = Omit<
  PedidoValidado["detalles"][number],
  "prendaNombre" | "servicioNombre" | "modalidadCobro"
>;
export type PedidoRequest = Omit<
  PedidoValidado,
  "clienteId" | "clienteNombre" | "detalles"
> & { clienteId?: number; detalles: DetalleRequest[] };

export function prepararPedido({
  clienteId,
  fechaEntregaEstimada,
  detalles,
}: PedidoValidado): PedidoRequest {
  return {
    ...(clienteId !== undefined ? { clienteId } : {}),
    fechaEntregaEstimada,
    detalles: detalles.map(
      ({
        detalleId,
        prendaId,
        servicioId,
        cantidad,
        estadoPrenda,
        observaciones,
      }) => ({
        detalleId,
        prendaId,
        servicioId,
        cantidad,
        estadoPrenda,
        observaciones,
      }),
    ),
  };
}

export function valoresPedido(pedido?: Pedido): PedidoValores {
  return {
    clienteId: pedido?.clienteId ?? null,
    clienteNombre: pedido
      ? `${pedido.clienteNombre} ${pedido.clienteApellido}`
      : "",
    fechaEntregaEstimada: pedido?.fechaEntregaEstimada ?? "",
    detalles: pedido?.detalles.map((d) => ({
      detalleId: d.id,
      prendaId: d.prendaId,
      servicioId: d.servicioId,
      prendaNombre: d.prendaNombre,
      servicioNombre: d.servicioNombre,
      modalidadCobro: d.modalidadCobro,
      cantidad: String(d.cantidad),
      estadoPrenda: d.estadoPrenda ?? "",
      observaciones: d.observaciones ?? "",
    })) ?? [{ ...lineaVacia }],
  };
}

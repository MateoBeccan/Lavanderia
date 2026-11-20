import { z } from "zod";

const importe = (mensaje: string) =>
  z
    .string()
    .trim()
    .regex(/^[0-9]{1,8}([,.][0-9]{1,2})?$/, mensaje)
    .transform((value) => Number(value.replace(",", ".")));

const noNegativo = importe(
  "Ingresá un importe entre 0 y 99.999.999,99, con hasta dos decimales.",
);

export const cierreSchema = z.object({ efectivoContado: noNegativo });
export const aperturaSchema = z.object({ montoInicial: noNegativo });
export const movimientoManualSchema = z.object({
  monto: importe(
    "Ingresá un importe de hasta 99.999.999,99, con hasta dos decimales.",
  ).pipe(z.number().positive("Ingresá un importe mayor a cero.")),
  metodoPagoId: z
    .string()
    .regex(/^[1-9][0-9]*$/, "Elegí un método de pago activo.")
    .transform(Number)
    .pipe(z.int().positive()),
  descripcion: z
    .string()
    .trim()
    .min(1, "Ingresá un concepto.")
    .max(250, "El concepto admite hasta 250 caracteres."),
});

export function validarEgreso(monto: number, codigo: string, efectivo: number) {
  if (codigo === "EFECTIVO" && monto > efectivo)
    return "El egreso supera el efectivo disponible de la caja.";
}

export const filtrosSchema = z
  .object({
    tipo: z.string(),
    metodoPagoId: z.string().regex(/^([1-9][0-9]*)?$/),
    desde: z.union([z.literal(""), z.iso.date()]),
    hasta: z.union([z.literal(""), z.iso.date()]),
  })
  .refine(({ desde, hasta }) => !desde || !hasta || desde <= hasta, {
    path: ["hasta"],
    message: "Hasta debe ser igual o posterior a Desde.",
  });

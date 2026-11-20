import { z } from "zod";
import { nombreCatalogo } from "@/shared/catalogos/schema";
import { modalidadesCobro } from "../types/servicios";

export const servicioSchema = z.object({
  nombre: nombreCatalogo(100),
  precio: z
    .string()
    .optional()
    .transform((value) => value?.trim() ?? "")
    .pipe(
      z
        .string()
        .regex(
          /^\d{1,8}(?:[.,]\d{1,2})?$/,
          "Ingresá un precio de hasta 8 enteros y 2 decimales.",
        ),
    )
    .transform((value) => Number(value.replace(",", ".")))
    .pipe(z.number().positive("El precio debe ser mayor que cero.")),
  modalidadCobro: z
    .string()
    .optional()
    .transform((value) => value ?? "")
    .pipe(
      z.enum(modalidadesCobro, { error: "Seleccioná una modalidad de cobro." }),
    ),
});

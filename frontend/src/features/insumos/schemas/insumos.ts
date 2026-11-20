import { z } from "zod";
import { nombreCatalogo, stripJava } from "@/shared/catalogos/schema";

export const categoriaInsumoSchema = z.object({
  nombre: nombreCatalogo(50).transform(stripJava),
});

export const insumoSchema = z.object({
  nombre: nombreCatalogo(100).transform(stripJava),
  categoriaId: z
    .number()
    .nullable()
    .transform((id, ctx) => {
      if (id === null || !Number.isSafeInteger(id) || id <= 0) {
        ctx.addIssue({
          code: "custom",
          message: "Seleccioná una categoría activa.",
        });
        return z.NEVER;
      }
      return id;
    }),
});

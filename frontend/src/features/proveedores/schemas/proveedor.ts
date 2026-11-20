import { z } from "zod";
import { idCatalogoValido, stripJava } from "@/shared/catalogos/schema";

const formatoCuit = /^(?:[0-9]{11}|[0-9]{2}-[0-9]{8}-[0-9])$/u;
const pesos = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];

export const cuitSchema = z
  .string()
  .regex(formatoCuit, "Ingresá 11 dígitos o usá el formato XX-XXXXXXXX-X.")
  .refine((valor) => {
    if (!formatoCuit.test(valor)) return true;
    const digitos = valor.replaceAll("-", "");
    const suma = pesos.reduce(
      (total, peso, i) => total + Number(digitos[i]) * peso,
      0,
    );
    return (11 - (suma % 11)) % 11 === Number(digitos[10]);
  }, "El dígito verificador del CUIT es inválido.");

const texto = z.string().transform(stripJava);

export const proveedorSchema = z.object({
  nombre: texto.pipe(
    z
      .string()
      .min(1, "Ingresá un nombre.")
      .max(150, "Usá hasta 150 caracteres."),
  ),
  cuit: cuitSchema,
  telefono: texto.pipe(
    z
      .string()
      .min(1, "Ingresá un teléfono.")
      .max(20, "Usá hasta 20 caracteres."),
  ),
  rubro: texto
    .pipe(z.string().max(50, "Usá hasta 50 caracteres."))
    .transform((valor) => valor || null),
});

export const reactivacionSchema = z.object({
  id: z
    .string()
    .trim()
    .refine(idCatalogoValido, "Ingresá un ID de proveedor válido."),
});

export type ProveedorFormValues = z.input<typeof proveedorSchema>;

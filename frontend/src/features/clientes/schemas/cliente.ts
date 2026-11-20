import { z } from "zod";

export const clienteSchema = z.object({
  nombre: z
    .string()
    .max(100, "Ingresá hasta 100 caracteres.")
    .trim()
    .min(1, "Ingresá el nombre."),
  apellido: z
    .string()
    .max(100, "Ingresá hasta 100 caracteres.")
    .trim()
    .min(1, "Ingresá el apellido."),
  dni: z
    .string()
    .regex(/^[0-9]{7,8}$/, "Ingresá 7 u 8 dígitos sin separadores."),
  telefono: z
    .string()
    .max(30, "Ingresá hasta 30 caracteres.")
    .trim()
    .transform((value) => value || null),
  email: z
    .string()
    .max(254, "Ingresá hasta 254 caracteres.")
    .refine(
      (value) =>
        value === "" ||
        z.email({ pattern: z.regexes.html5Email }).safeParse(value).success,
      "Ingresá un correo electrónico válido.",
    )
    .transform((value) => value || null),
});

export type ClienteFormValues = z.input<typeof clienteSchema>;

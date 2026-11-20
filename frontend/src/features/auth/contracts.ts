import { z } from "zod";

export const loginSchema = z.object({
  username: z
    .string()
    .trim()
    .min(1, "Ingresá tu usuario.")
    .max(50, "Usá hasta 50 caracteres."),
  password: z
    .string()
    .min(1, "Ingresá tu contraseña.")
    .max(128, "Usá hasta 128 caracteres.")
    .refine((password) => password.trim().length > 0, "Ingresá tu contraseña."),
});
export type LoginRequest = z.infer<typeof loginSchema>;

export const loginResponse = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal("Bearer"),
  expiresIn: z.number().int().positive(),
});

export const userResponse = z.object({
  id: z.number().int(),
  username: z.string(),
  nombre: z.string(),
  email: z.string(),
  rol: z.enum(["ADMINISTRADOR", "EMPLEADO"]),
});
export type User = z.infer<typeof userResponse>;
export type Role = User["rol"];

export const roleLabels: Record<Role, string> = {
  ADMINISTRADOR: "Administrador",
  EMPLEADO: "Empleado",
};

import { z } from "zod";

const monto = z
  .string()
  .trim()
  .regex(
    /^\d{1,8}(?:[.,]\d{1,2})?$/,
    "Ingresá un importe de hasta 8 enteros y 2 decimales.",
  );

export const centavos = (decimal: string) => {
  const negativo = decimal.startsWith("-");
  const [entero, fraccion = ""] = decimal
    .replace(/^-/, "")
    .replace(",", ".")
    .split(".");
  const valor = BigInt(entero) * 100n + BigInt(fraccion.padEnd(2, "0"));
  return negativo ? -valor : valor;
};

export function cobroSchema(saldo: number, metodos: readonly number[]) {
  return z.object({
    monto: monto
      .superRefine((valor, ctx) => {
        if (!monto.safeParse(valor).success) return;
        const importe = centavos(valor);
        if (importe === 0n || importe > centavos(String(saldo))) {
          ctx.addIssue({
            code: "custom",
            message:
              importe === 0n
                ? "El importe debe ser mayor a cero."
                : "El importe supera el saldo pendiente.",
          });
        }
      })
      .transform((valor) => Number(valor.replace(",", "."))),
    metodoPagoId: z
      .string()
      .transform(Number)
      .refine(
        (id) => metodos.includes(id),
        "Seleccioná un método de pago activo.",
      ),
  });
}

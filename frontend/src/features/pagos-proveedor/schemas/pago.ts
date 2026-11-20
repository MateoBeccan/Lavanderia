import { z } from "zod";
import type { Role } from "@/features/auth/contracts";
import type { OpcionCaja, SaldoCaja } from "@/features/caja/types/caja";
import { centavos, cobroSchema } from "@/features/cobros/schemas/cobro";
import type { SaldoCompra } from "@/features/compras/types/compras";

export function puedeGestionarPagos(rol?: Role) {
  return rol === "ADMINISTRADOR" || rol === "EMPLEADO";
}

export function tieneSaldoPendiente(saldo: SaldoCompra) {
  return (
    (saldo.estadoPagoCodigo === "PENDIENTE" ||
      saldo.estadoPagoCodigo === "PARCIAL") &&
    saldo.saldoPendiente > 0
  );
}

export function pagoProveedorSchema(
  saldo: number,
  metodos: readonly OpcionCaja[],
  caja: Pick<SaldoCaja, "saldoGeneral" | "efectivoDisponible">,
) {
  return cobroSchema(
    saldo,
    metodos.map(({ id }) => id),
  ).pipe(
    z
      .object({
        monto: z.number(),
        metodoPagoId: z.number(),
      })
      .superRefine((datos, ctx) => {
        const metodo = metodos.find(({ id }) => id === datos.metodoPagoId);
        const importe = centavos(String(datos.monto));
        const efectivo = centavos(String(caja.efectivoDisponible));
        const general = centavos(String(caja.saldoGeneral)) - importe;
        const maximo = 9_999_999_999n;
        const efectivoResultante =
          metodo?.codigo === "EFECTIVO" ? efectivo - importe : efectivo;
        if (metodo?.codigo === "EFECTIVO" && importe > efectivo) {
          ctx.addIssue({
            code: "custom",
            path: ["monto"],
            message: "El pago supera el efectivo disponible de la caja.",
          });
        } else if (
          general < -maximo ||
          general > maximo ||
          efectivoResultante < 0n ||
          efectivoResultante > maximo
        ) {
          ctx.addIssue({
            code: "custom",
            path: ["monto"],
            message:
              "La operación deja los saldos de caja fuera del rango admitido.",
          });
        }
      }),
  );
}

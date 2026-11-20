import { useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { cajaKeys } from "@/features/caja/hooks/queries";
import { comprasKeys } from "@/features/compras/hooks/queries";
import { errorOperacionFinanciera } from "@/shared/api/errorOperacionFinanciera";
import {
  congelarEnvio,
  crearRegistroFinanciero,
} from "@/shared/api/operacionFinanciera";
import { idCatalogoValido } from "@/shared/catalogos/schema";
import { pagosApi } from "../api/pagos";
import { puedeGestionarPagos } from "../schemas/pago";
import type {
  IntentoPago,
  OperacionPago,
  SolicitudPagoProveedor,
} from "../types/pagos";

const lista: OperacionPago = { estado: "LISTA" };

export function compraPendiente(operacion: OperacionPago) {
  if ("intento" in operacion) return operacion.intento.compraId;
  if (operacion.estado === "VERIFICAR") return operacion.compraId;
}

const registro = crearRegistroFinanciero<OperacionPago>(
  "pago-proveedor",
  lista,
  (compraId) =>
    idCatalogoValido(compraId)
      ? {
          estado: "VERIFICAR",
          compraId,
          mensaje:
            "Hay un envío pendiente de conciliar. Actualizá el saldo y revisá el historial de la compra.",
        }
      : undefined,
  compraPendiente,
);

export function useOperacionPago() {
  const { api, user } = useSession();
  const cache = useQueryClient();
  const usuario = user?.id ?? 0;
  const permitido = puedeGestionarPagos(user?.rol);
  const operacion = useSyncExternalStore(registro.subscribe, () =>
    registro.leer(usuario),
  );

  async function enviar(intento: IntentoPago, reintento: boolean) {
    registro.actualizar(usuario, { estado: "ENVIANDO", intento });
    try {
      const pago = await pagosApi.registrar(api, intento);
      registro.actualizar(usuario, { estado: "CONFIRMADA", pago });
    } catch (error) {
      const fallo = errorOperacionFinanciera(error, reintento);
      registro.actualizar(
        usuario,
        fallo.estado === "PENDIENTE"
          ? { ...fallo, intento }
          : { ...fallo, compraId: intento.compraId },
      );
    }
    void Promise.all([
      cache.invalidateQueries({
        queryKey: comprasKeys.ficha(intento.compraId),
      }),
      cache.invalidateQueries({ queryKey: comprasKeys.listas }),
      cache.invalidateQueries({ queryKey: cajaKeys.all }),
    ]);
  }

  return {
    operacion,
    iniciar(compraId: string, cajaId: number, body: SolicitudPagoProveedor) {
      const actual = registro.leer(usuario);
      if (
        !permitido ||
        compraPendiente(actual) ||
        (actual.estado === "CONFIRMADA" &&
          String(actual.pago.compraId) === compraId)
      )
        return;
      try {
        const intento = Object.freeze({
          compraId,
          cajaId,
          ...congelarEnvio(body),
        });
        void enviar(intento, false);
      } catch {
        registro.actualizar(usuario, {
          estado: "ERROR",
          compraId,
          mensaje:
            "Abrí el sistema desde una conexión segura para registrar pagos.",
        });
      }
    },
    reintentar() {
      const actual = registro.leer(usuario);
      if (
        permitido &&
        actual.estado === "PENDIENTE" &&
        Date.now() >= actual.retryAt
      ) {
        void enviar(actual.intento, true);
      }
    },
    cerrar(compraId: string) {
      const actual = registro.leer(usuario);
      if (!permitido) return;
      if (
        (actual.estado === "VERIFICAR" && actual.compraId === compraId) ||
        (actual.estado === "CONFIRMADA" &&
          String(actual.pago.compraId) === compraId)
      ) {
        registro.actualizar(usuario, lista);
      }
    },
  };
}

import { useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import {
  congelarEnvio,
  crearRegistroFinanciero,
} from "@/shared/api/operacionFinanciera";
import { useSession } from "@/features/auth/session";
import { cajaKeys } from "@/features/caja/hooks/queries";
import { idPedidoValido } from "@/features/pedidos/rules";
import { cobrosApi } from "../api/cobros";
import type { IntentoCobro, SolicitudCobro } from "../types/cobros";
import { cobrosKeys } from "./queries";
import { resolverError } from "./operacion";
import type { OperacionCobro } from "./operacion";

const borrador: OperacionCobro = { estado: "BORRADOR" };
const { subscribe, leer, actualizar } = crearRegistroFinanciero<OperacionCobro>(
  "cobro",
  borrador,
  (pedidoId) =>
    idPedidoValido(pedidoId) ? { estado: "VERIFICAR", pedidoId } : undefined,
  pedidoPendiente,
);
export function pedidoPendiente(op: OperacionCobro) {
  if ("intento" in op) return op.intento.pedidoId;
  if (op.estado === "VERIFICAR") return op.pedidoId;
}

export function useOperacionCobro() {
  const { api, user } = useSession();
  const cache = useQueryClient();
  const usuario = user?.id ?? 0;
  const operacion = useSyncExternalStore(subscribe, () => leer(usuario));

  async function enviar(intento: IntentoCobro, reintento: boolean) {
    actualizar(usuario, { estado: "ENVIANDO", intento });
    let cobro;
    try {
      cobro = await cobrosApi.registrar(api, intento);
    } catch (error) {
      actualizar(usuario, resolverError(error, intento, reintento));
      void cache.invalidateQueries({ queryKey: ["pedidos"] });
      void cache.invalidateQueries({
        queryKey: cobrosKeys.listas(intento.pedidoId),
      });
      void cache.invalidateQueries({ queryKey: cajaKeys.abierta });
      return;
    }
    actualizar(usuario, { estado: "CONFIRMADO", cobro });
    void Promise.all([
      cache.invalidateQueries({ queryKey: ["pedidos"] }),
      cache.invalidateQueries({
        queryKey: cobrosKeys.listas(intento.pedidoId),
      }),
      cache.invalidateQueries({ queryKey: cajaKeys.all }),
    ]);
  }

  return {
    operacion,
    iniciar(pedidoId: string, body: SolicitudCobro) {
      const actual = leer(usuario);
      if (
        !user ||
        pedidoPendiente(actual) ||
        (actual.estado === "CONFIRMADO" &&
          String(actual.cobro.pedidoId) === pedidoId)
      )
        return;
      try {
        const intento = Object.freeze({
          pedidoId,
          ...congelarEnvio(body),
        });
        void enviar(intento, false);
      } catch {
        actualizar(usuario, {
          estado: "ERROR_VALIDADO",
          pedidoId,
          mensaje:
            "Abrí el sistema desde una conexión segura para registrar cobros.",
        });
      }
    },
    reintentar() {
      const actual = leer(usuario);
      if (!user) return;
      if (
        actual.estado === "RESULTADO_INCIERTO" ||
        (actual.estado === "CONFLICTO" &&
          actual.retryAt !== null &&
          Date.now() >= actual.retryAt)
      ) {
        void enviar(actual.intento, true);
      }
    },
    cerrar(pedidoId: string) {
      const actual = leer(usuario);
      if (!user) return;
      if (
        (actual.estado === "VERIFICAR" && actual.pedidoId === pedidoId) ||
        (actual.estado === "CONFLICTO" &&
          actual.retryAt === null &&
          actual.intento.pedidoId === pedidoId) ||
        actual.estado === "CONFIRMADO"
      ) {
        actualizar(usuario, borrador);
      }
    },
  };
}

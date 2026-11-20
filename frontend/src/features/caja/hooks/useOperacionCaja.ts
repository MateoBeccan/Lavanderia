import { useSyncExternalStore } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useSession } from "@/features/auth/session";
import { registrarCaja } from "../api/escrituras";
import type { IntentoCaja, SolicitudCaja } from "../api/escrituras";
import { errorOperacionCaja } from "./errorOperacionCaja";
import { cajaKeys } from "./queries";

type Estado =
  | { estado: "LISTA" }
  | { estado: "ENVIANDO"; intento: IntentoCaja }
  | {
      estado: "PENDIENTE";
      intento: IntentoCaja;
      retryAt: number;
      mensaje: string;
    }
  | { estado: "VERIFICAR"; cajaId?: number; mensaje: string }
  | { estado: "ERROR"; mensaje: string }
  | {
      estado: "CONFIRMADA";
      resultado: Awaited<ReturnType<typeof registrarCaja>>;
    };

const lista: Estado = { estado: "LISTA" };
const operaciones = new Map<number, Estado>();
const listeners = new Set<() => void>();
const marcador = (usuario: number) => `lavanderia.caja.pendiente.v1.${usuario}`;

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function leer(usuario: number): Estado {
  const actual = operaciones.get(usuario);
  if (actual) return actual;
  try {
    const pendiente = sessionStorage.getItem(marcador(usuario));
    if (pendiente) {
      const recuperada: Estado = {
        estado: "VERIFICAR",
        cajaId: /^[1-9][0-9]*$/.test(pendiente) ? Number(pendiente) : undefined,
        mensaje:
          "Hay un envío pendiente de conciliar. Revisá la caja y sus movimientos antes de registrar otra operación.",
      };
      operaciones.set(usuario, recuperada);
      return recuperada;
    }
  } catch {
    // El navegador puede bloquear el acceso a sessionStorage.
  }
  return lista;
}

function actualizar(usuario: number, siguiente: Estado) {
  operaciones.set(usuario, siguiente);
  try {
    if ("intento" in siguiente) {
      sessionStorage.setItem(
        marcador(usuario),
        "cajaId" in siguiente.intento
          ? String(siguiente.intento.cajaId)
          : "apertura",
      );
    } else if (siguiente.estado !== "VERIFICAR") {
      sessionStorage.removeItem(marcador(usuario));
    }
  } catch {
    // El intento sigue disponible en memoria.
  }
  for (const listener of listeners) listener();
}

export function useOperacionCaja() {
  const { api, user } = useSession();
  const cache = useQueryClient();
  const usuario = user?.id ?? 0;
  const operacion = useSyncExternalStore(subscribe, () => leer(usuario));
  const permitido = user?.rol === "ADMINISTRADOR" || user?.rol === "EMPLEADO";
  const refrescar = () => cache.invalidateQueries({ queryKey: cajaKeys.all });

  async function enviar(intento: IntentoCaja, reintento: boolean) {
    actualizar(usuario, { estado: "ENVIANDO", intento });
    try {
      const resultado = await registrarCaja(api, intento);
      actualizar(usuario, { estado: "CONFIRMADA", resultado });
    } catch (error) {
      const fallo = errorOperacionCaja(error, reintento);
      if (fallo.estado === "PENDIENTE") {
        actualizar(usuario, { ...fallo, intento });
      } else if (fallo.estado === "VERIFICAR") {
        actualizar(usuario, {
          ...fallo,
          cajaId: "cajaId" in intento ? intento.cajaId : undefined,
        });
      } else {
        actualizar(usuario, fallo);
      }
    }
    void refrescar();
  }

  return {
    operacion,
    permitido,
    refrescar,
    iniciar(solicitud: SolicitudCaja) {
      if (!permitido || !["LISTA", "ERROR"].includes(leer(usuario).estado))
        return;
      try {
        const intento = Object.freeze({
          ...solicitud,
          clave: crypto.randomUUID(),
          body: Object.freeze({ ...solicitud.body }),
        }) as IntentoCaja;
        void enviar(intento, false);
      } catch {
        actualizar(usuario, {
          estado: "ERROR",
          mensaje:
            "Abrí el sistema desde una conexión segura para registrar operaciones de caja.",
        });
      }
    },
    reintentar() {
      const actual = leer(usuario);
      if (
        permitido &&
        actual.estado === "PENDIENTE" &&
        Date.now() >= actual.retryAt
      )
        void enviar(actual.intento, true);
    },
    cerrar() {
      const actual = leer(usuario);
      if (
        permitido &&
        (actual.estado === "VERIFICAR" || actual.estado === "CONFIRMADA")
      )
        actualizar(usuario, lista);
    },
  };
}

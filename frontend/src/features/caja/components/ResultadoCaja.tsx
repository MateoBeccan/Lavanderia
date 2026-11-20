import { useEffect, useState } from "react";
import { Link } from "react-router";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import type { useOperacionCaja } from "../hooks/useOperacionCaja";
import { importeCaja } from "./formato";

function Reintentar({
  retryAt,
  reintentar,
  cambioCaja,
}: {
  retryAt: number;
  reintentar: () => void;
  cambioCaja: boolean;
}) {
  const [listo, setListo] = useState(() => Date.now() >= retryAt);
  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    function esperar() {
      const espera = retryAt - Date.now();
      if (espera <= 0) setListo(true);
      else timer = setTimeout(esperar, Math.min(espera, 2_147_483_647));
    }
    timer = setTimeout(esperar, 0);
    return () => clearTimeout(timer);
  }, [retryAt]);
  const etiqueta = "Reintentar el mismo envío";
  return (
    <div className="space-y-2">
      {cambioCaja && (
        <p>
          El reintento conserva la caja original. Si la jornada cambió, solo
          podrá recuperar un registro ya confirmado.
        </p>
      )}
      <Button disabled={!listo} onClick={reintentar}>
        {etiqueta}
      </Button>
    </div>
  );
}

export function ResultadoCaja({
  flujo,
  cajaActual,
}: {
  flujo: ReturnType<typeof useOperacionCaja>;
  cajaActual?: number;
}) {
  const { operacion, cerrar, reintentar, refrescar, permitido } = flujo;
  if (operacion.estado === "LISTA") return null;
  if (operacion.estado === "ERROR")
    return <ErrorState message={operacion.mensaje} />;
  if (operacion.estado === "ENVIANDO")
    return <LoadingState label="Registrando operación de caja…" />;

  return (
    <section
      className="surface space-y-3 p-5"
      aria-label="Resultado de la operación"
    >
      {operacion.estado === "CONFIRMADA" ? (
        <>
          <div role="status">
            {operacion.resultado.tipo === "APERTURA" ? (
              <p>
                Caja #{operacion.resultado.caja.id} abierta con{" "}
                {importeCaja(operacion.resultado.caja.montoInicial)} de efectivo
                inicial.
              </p>
            ) : (
              <p>
                Movimiento #{operacion.resultado.movimiento.id} registrado en
                Caja #{operacion.resultado.movimiento.cajaId} por{" "}
                {importeCaja(operacion.resultado.movimiento.monto)} ·{" "}
                {operacion.resultado.movimiento.metodoPagoNombre} ·{" "}
                {operacion.resultado.movimiento.descripcion}.
              </p>
            )}
          </div>
          {operacion.resultado.tipo !== "APERTURA" && (
            <Link
              className="block underline"
              to={`/caja/${operacion.resultado.movimiento.cajaId}/movimientos/${operacion.resultado.movimiento.id}`}
            >
              Consultar movimiento confirmado
            </Link>
          )}
          <Button variant="outline" onClick={cerrar}>
            Continuar
          </Button>
        </>
      ) : (
        <>
          <p role="alert">{operacion.mensaje}</p>
          {operacion.estado === "VERIFICAR" && operacion.cajaId && (
            <Link className="block underline" to={`/caja/${operacion.cajaId}`}>
              Revisar movimientos de Caja #{operacion.cajaId}
            </Link>
          )}
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" onClick={() => void refrescar()}>
              Actualizar caja y movimientos
            </Button>
            {permitido && operacion.estado === "PENDIENTE" && (
              <Reintentar
                key={operacion.retryAt}
                retryAt={operacion.retryAt}
                reintentar={reintentar}
                cambioCaja={
                  "cajaId" in operacion.intento &&
                  operacion.intento.cajaId !== cajaActual
                }
              />
            )}
            {permitido && operacion.estado === "VERIFICAR" && (
              <ConfirmDialog
                trigger={<Button>Ya concilié el envío</Button>}
                title="Habilitar nuevas operaciones"
                description="Confirmá solo después de identificar el resultado del envío anterior en el historial o con el administrador."
                confirmLabel="Confirmar conciliación"
                onConfirm={cerrar}
              />
            )}
          </div>
        </>
      )}
    </section>
  );
}

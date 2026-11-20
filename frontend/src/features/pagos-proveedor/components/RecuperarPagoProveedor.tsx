import { useEffect, useState } from "react";
import {
  usePagosAsociados,
  useSaldoCompra,
} from "@/features/compras/hooks/queries";
import { PagosAsociados } from "@/features/compras/components/PagosAsociados";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import type { OperacionPago } from "../types/pagos";
import { SaldoCompra } from "./SaldoCompra";

function Reintento({
  retryAt,
  reintentar,
}: {
  retryAt: number;
  reintentar: () => void;
}) {
  const [listo, setListo] = useState(() => retryAt <= Date.now());
  useEffect(() => {
    if (listo) return;
    const timer = setTimeout(
      () => setListo(true),
      Math.min(2_147_483_647, Math.max(0, retryAt - Date.now())),
    );
    return () => clearTimeout(timer);
  }, [listo, retryAt]);
  return (
    <Button disabled={!listo} onClick={reintentar}>
      Reintentar el mismo envío
    </Button>
  );
}

function Conciliar({ id, cerrar }: { id: string; cerrar: () => void }) {
  const saldo = useSaldoCompra(id);
  const historial = usePagosAsociados(id, 0);
  const [revision, setRevision] = useState<
    "inicial" | "cargando" | "lista" | "error"
  >("inicial");
  async function verificar() {
    setRevision("cargando");
    const consultas = await Promise.all([saldo.refetch(), historial.refetch()]);
    setRevision(
      consultas.every(({ isSuccess }) => isSuccess) ? "lista" : "error",
    );
  }
  return (
    <>
      <p>
        Revisá el saldo y todas las páginas del historial. Si todavía hay dudas
        sobre el envío anterior, esperá y volvé a consultar.
      </p>
      {saldo.isSuccess && <SaldoCompra saldo={saldo.data} />}
      <PagosAsociados id={id} />
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          disabled={revision === "cargando"}
          onClick={() => void verificar()}
        >
          Actualizar saldo e historial
        </Button>
        {revision === "lista" && saldo.isSuccess && historial.isSuccess && (
          <Button onClick={cerrar}>Ya concilié el envío</Button>
        )}
      </div>
      {revision === "error" && (
        <ErrorState message="No se pudo verificar el saldo y el historial." />
      )}
    </>
  );
}

export function RecuperarPagoProveedor({
  id,
  operacion,
  reintentar,
  cerrar,
}: {
  id: string;
  operacion: Extract<
    OperacionPago,
    { estado: "ENVIANDO" | "PENDIENTE" | "VERIFICAR" }
  >;
  reintentar: () => void;
  cerrar: () => void;
}) {
  if (operacion.estado === "ENVIANDO")
    return <LoadingState label="Registrando pago…" />;
  return (
    <section
      className="surface grid gap-4 p-4 sm:p-6"
      aria-label="Envío pendiente"
    >
      <p role="alert">{operacion.mensaje}</p>
      {operacion.estado === "VERIFICAR" ? (
        <Conciliar id={id} cerrar={cerrar} />
      ) : (
        <>
          <p>
            Si Spring ya registró el pago, vas a recuperar su resultado
            original. Si el primer intento se revirtió, el reintento puede
            aplicarse a la caja abierta en ese momento. Caja del intento
            original: #{operacion.intento.cajaId}.
          </p>
          <div>
            <Reintento
              key={operacion.retryAt}
              retryAt={operacion.retryAt}
              reintentar={reintentar}
            />
          </div>
          <PagosAsociados id={id} />
        </>
      )}
    </section>
  );
}

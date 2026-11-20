import { useEffect, useState } from "react";
import { Link } from "react-router";
import { useSaldoPedido } from "@/features/pedidos/hooks/queries";
import { Button } from "@/shared/components/ui/button";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import type { OperacionCobro } from "../hooks/operacion";
import { useCobros } from "../hooks/queries";
import { HistorialCobros } from "./HistorialCobros";
import { SaldoCobro } from "./SaldoCobro";

function Reintento({
  retryAt,
  reintentar,
}: {
  retryAt: number;
  reintentar: () => void;
}) {
  const [listo, setListo] = useState(retryAt === 0);
  useEffect(() => {
    if (retryAt === 0) return;
    const id = setTimeout(
      () => setListo(true),
      Math.max(0, retryAt - Date.now()),
    );
    return () => clearTimeout(id);
  }, [retryAt]);
  return (
    <Button disabled={!listo} onClick={reintentar}>
      Reintentar el mismo envío
    </Button>
  );
}

function VerificarHistorial({
  id,
  cerrar,
}: {
  id: string;
  cerrar: () => void;
}) {
  const saldo = useSaldoPedido(id);
  const historial = useCobros(id, 0);
  const [revision, setRevision] = useState<
    "inicial" | "cargando" | "error" | "lista"
  >("inicial");

  async function verificar() {
    setRevision("cargando");
    const consultas = await Promise.all([saldo.refetch(), historial.refetch()]);
    setRevision(
      consultas.every((consulta) => consulta.isSuccess) ? "lista" : "error",
    );
  }

  return (
    <div className="grid gap-3">
      <p>
        Actualizá el saldo y revisá todas las páginas del historial antes de
        habilitar otro cobro.
      </p>
      {saldo.isSuccess && <SaldoCobro saldo={saldo.data} />}
      <HistorialCobros id={id} />
      <div className="flex flex-wrap gap-2">
        <Button
          variant="outline"
          onClick={() => void verificar()}
          disabled={revision === "cargando"}
        >
          Actualizar saldo e historial
        </Button>
        {revision === "lista" && (
          <Button onClick={cerrar}>Ya verifiqué el historial</Button>
        )}
      </div>
      {revision === "error" && (
        <ErrorState message="No se pudo verificar el saldo y el historial. Intentá actualizar nuevamente." />
      )}
      {revision === "lista" && (
        <p className="text-sm text-muted-foreground">
          Si el envío anterior todavía no aparece y hay dudas sobre el
          resultado, esperá y volvé a consultar antes de registrar el importe.
        </p>
      )}
    </div>
  );
}

export function RecuperarCobro({
  id,
  operacion,
  reintentar,
  cerrar,
}: {
  id: string;
  operacion: Extract<
    OperacionCobro,
    { estado: "ENVIANDO" | "RESULTADO_INCIERTO" | "CONFLICTO" | "VERIFICAR" }
  >;
  reintentar: () => void;
  cerrar: () => void;
}) {
  if (operacion.estado === "ENVIANDO")
    return <LoadingState label="Registrando cobro…" />;
  const retryAt = operacion.estado === "CONFLICTO" ? operacion.retryAt : 0;
  const verificacion = operacion.estado === "VERIFICAR" || retryAt === null;
  return (
    <section
      className="surface grid min-w-0 gap-4 p-4 sm:p-6"
      aria-label="Envío pendiente"
    >
      <p role="alert">
        {operacion.estado === "VERIFICAR"
          ? "Hay un envío previo para verificar. El reintento idéntico ya no está disponible en esta sesión."
          : operacion.mensaje}
      </p>
      <Link className="underline" to={`/pedidos/${id}`}>
        Consultar pedido #{id}
      </Link>
      {verificacion ? (
        <VerificarHistorial id={id} cerrar={cerrar} />
      ) : (
        <div>
          <Reintento key={retryAt} retryAt={retryAt} reintentar={reintentar} />
        </div>
      )}
    </section>
  );
}

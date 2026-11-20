import { Link } from "react-router";
import { fechaPedido } from "@/features/pedidos/rules";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { useComprobanteDeOperacion } from "../hooks/queries";
import type { ReferenciaComprobante } from "../types/comprobantes";
import { RegistrarComprobanteForm } from "./RegistrarComprobanteForm";

export function ComprobanteOperacion({
  operacion,
}: {
  operacion: ReferenciaComprobante;
}) {
  const consulta = useComprobanteDeOperacion(operacion);
  const datos = consulta.data;
  return (
    <section
      aria-label="Comprobante interno"
      className="grid gap-3 border-t pt-4"
    >
      <h2 className="text-lg font-semibold">Comprobante interno</h2>
      {consulta.isPending && <LoadingState label="Consultando comprobante…" />}
      {consulta.isError && (
        <ErrorState
          message={consulta.error.message}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {consulta.isSuccess && datos && (
        <>
          <p>
            Comprobante #{datos.id} · Emisión{" "}
            <time dateTime={datos.fechaEmision}>
              {fechaPedido(datos.fechaEmision)}
            </time>
          </p>
          {datos.observaciones && (
            <p className="whitespace-pre-wrap wrap-anywhere">
              {datos.observaciones}
            </p>
          )}
          <Button asChild variant="outline" className="justify-self-start">
            <Link to={`/comprobantes/${datos.id}`}>
              Ver comprobante #{datos.id}
            </Link>
          </Button>
        </>
      )}
      {datos === null && (
        <>
          {consulta.isSuccess && (
            <p className="text-muted-foreground">
              La operación todavía no tiene comprobante.
            </p>
          )}
          <RegistrarComprobanteForm
            operacion={operacion}
            consultando={consulta.isFetching || consulta.isError}
            volverAConsultarSinRegistro={async () => {
              const resultado = await consulta.refetch();
              return resultado.isSuccess && resultado.data === null;
            }}
          />
        </>
      )}
    </section>
  );
}

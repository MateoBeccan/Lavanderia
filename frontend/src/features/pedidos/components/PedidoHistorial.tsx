import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { useHistorialPedido } from "../hooks/queries";
import { errorPedido, fechaPedido } from "../rules";
import { PedidoEstadoBadge } from "./PedidoEstadoBadge";

export function PedidoHistorial({ id }: { id: string }) {
  const historial = useHistorialPedido(id);
  return (
    <section className="surface p-4 sm:p-6" aria-labelledby="historial-pedido">
      <h2 className="mb-4 text-lg font-semibold" id="historial-pedido">
        Historial de estados
      </h2>
      {historial.isPending && <LoadingState label="Cargando historial…" />}
      {historial.isError && (
        <ErrorState
          message={errorPedido(historial.error)}
          onRetry={() => void historial.refetch()}
        />
      )}
      {historial.isSuccess &&
        (historial.data.length ? (
          <ol className="divide-y">
            {historial.data.map((evento) => (
              <li
                key={evento.id}
                className="grid gap-2 py-3 first:pt-0 last:pb-0"
              >
                <div className="flex flex-wrap items-center gap-2">
                  {evento.estadoAnteriorCodigo && (
                    <>
                      <PedidoEstadoBadge
                        codigo={evento.estadoAnteriorCodigo}
                        nombre={evento.estadoAnteriorNombre ?? undefined}
                      />
                      <span aria-label="Cambió a">→</span>
                    </>
                  )}
                  <PedidoEstadoBadge
                    codigo={evento.estadoNuevoCodigo}
                    nombre={evento.estadoNuevoNombre}
                  />
                </div>
                <div className="text-sm text-muted-foreground">
                  <time dateTime={evento.fechaCambio}>
                    {fechaPedido(evento.fechaCambio, true)}
                  </time>{" "}
                  · {evento.usuarioNombre}
                </div>
                {evento.observacion && (
                  <p className="wrap-anywhere">{evento.observacion}</p>
                )}
              </li>
            ))}
          </ol>
        ) : (
          <p className="text-sm text-muted-foreground">
            Todavía no hay cambios registrados.
          </p>
        ))}
    </section>
  );
}

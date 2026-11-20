import { useState } from "react";
import { Link } from "react-router";
import { importeCaja } from "@/features/caja/components/formato";
import { fechaPedido } from "@/features/pedidos/rules";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { useComprobantes } from "../hooks/queries";
import type { PadreComprobantes } from "../types/comprobantes";
import { nombreTipo } from "./presentacion";

export function ComprobantesAsociados({
  padre,
  id,
}: {
  padre: PadreComprobantes;
  id: string;
}) {
  const [pagina, cambiarPagina] = useState(0);
  const consulta = useComprobantes(padre, id, pagina);
  const datos = consulta.data;
  return (
    <section
      id="comprobantes-asociados"
      aria-label="Comprobantes asociados"
      className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-3"
    >
      <h2 className="text-lg font-semibold">Comprobantes asociados</h2>
      {consulta.isPending && <LoadingState label="Cargando comprobantes…" />}
      {consulta.isError && (
        <ErrorState
          message={consulta.error.message}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {consulta.isSuccess && datos && datos.content.length === 0 && (
        <div className="surface flex flex-wrap items-center gap-3 p-4">
          <p>
            {pagina === 0
              ? "Todavía no hay comprobantes registrados."
              : "No hay comprobantes en esta página."}
          </p>
          {pagina > 0 && (
            <Button variant="outline" onClick={() => cambiarPagina(0)}>
              Ir a la primera página
            </Button>
          )}
        </div>
      )}
      {consulta.isSuccess && datos && datos.content.length > 0 && (
        <DataTable
          caption={
            padre === "pedidos"
              ? "Comprobantes del pedido"
              : "Comprobantes de la compra"
          }
          data={datos.content}
          getRowId={(fila) => String(fila.id)}
          pagination={{
            pageIndex: datos.page.number,
            totalPages: datos.page.totalPages,
            onPageChange: cambiarPagina,
          }}
          columns={[
            {
              id: "id",
              header: "ID",
              cell: ({ row }) => (
                <Link
                  className="underline"
                  to={`/comprobantes/${row.original.id}`}
                >
                  Comprobante #{row.original.id}
                </Link>
              ),
            },
            {
              id: "tipo",
              header: "Tipo",
              cell: ({ row }) => nombreTipo(row.original.tipoOperacion),
            },
            {
              id: "fecha",
              header: "Emisión",
              cell: ({ row }) => (
                <time dateTime={row.original.fechaEmision}>
                  {fechaPedido(row.original.fechaEmision)}
                </time>
              ),
            },
            {
              id: "importe",
              header: "Importe",
              cell: ({ row }) => importeCaja(row.original.montoOperacion),
            },
          ]}
        />
      )}
    </section>
  );
}

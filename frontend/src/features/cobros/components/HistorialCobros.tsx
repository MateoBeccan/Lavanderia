import { useState } from "react";
import { Link } from "react-router";
import { DataTable } from "@/shared/components/tables/DataTable";
import { CajaFeedback } from "@/features/caja/components/CajaFeedback";
import { fechaCaja, importeCaja } from "@/features/caja/components/formato";
import { useCobros } from "../hooks/queries";

export function HistorialCobros({ id }: { id: string }) {
  const [page, setPage] = useState(0);
  const consulta = useCobros(id, page);
  return (
    <section className="grid min-w-0 gap-3" aria-label="Historial de cobros">
      <h2 className="text-lg font-semibold">Historial de cobros</h2>
      <CajaFeedback query={consulta} />
      {consulta.isSuccess && (
        <DataTable
          caption={`Cobros del pedido #${id}`}
          data={consulta.data.content}
          getRowId={(cobro) => String(cobro.id)}
          pagination={{
            pageIndex: consulta.data.page.number,
            totalPages: consulta.data.page.totalPages,
            onPageChange: setPage,
          }}
          columns={[
            {
              header: "Cobro",
              id: "cobro",
              cell: ({ row }) => (
                <Link className="underline" to={`/cobros/${row.original.id}`}>
                  Cobro #{row.original.id}
                </Link>
              ),
            },
            {
              header: "Pedido",
              id: "pedido",
              cell: ({ row }) => (
                <Link
                  className="underline"
                  to={`/pedidos/${row.original.pedidoId}`}
                >
                  Pedido #{row.original.pedidoId}
                </Link>
              ),
            },
            {
              header: "Fecha",
              id: "fecha",
              cell: ({ row }) => (
                <time dateTime={row.original.fechaCobro}>
                  {fechaCaja(row.original.fechaCobro)}
                </time>
              ),
            },
            {
              header: "Importe",
              id: "monto",
              cell: ({ row }) => importeCaja(row.original.montoCobrado),
            },
            { header: "Método de pago", accessorKey: "metodoPagoNombre" },
            { header: "Registrado por", accessorKey: "usuarioNombre" },
          ]}
        />
      )}
    </section>
  );
}

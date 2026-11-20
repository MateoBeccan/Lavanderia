import { useState } from "react";
import { Link } from "react-router";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { DataTable } from "@/shared/components/tables/DataTable";
import { usePagosAsociados } from "../hooks/queries";
import { fechaCompra, importeCompra } from "./presentacion";

export function PagosAsociados({ id }: { id: string }) {
  const [page, setPage] = useState(0);
  const pagos = usePagosAsociados(id, page);
  return (
    <section aria-labelledby="pagos-compra" className="space-y-3">
      <h2 id="pagos-compra" className="text-lg font-semibold">
        Pagos asociados
      </h2>
      {pagos.isPending && <LoadingState label="Cargando pagos asociados…" />}
      {pagos.isError && (
        <ErrorState
          message={pagos.error.message}
          onRetry={() => void pagos.refetch()}
        />
      )}
      {pagos.data &&
        (pagos.data.page.totalElements === 0 ? (
          <p className="text-muted-foreground">
            Todavía no hay pagos asociados.
          </p>
        ) : (
          <DataTable
            caption="Historial de pagos"
            data={pagos.data.content}
            getRowId={(pago) => String(pago.id)}
            pagination={{
              pageIndex: pagos.data.page.number,
              totalPages: pagos.data.page.totalPages,
              onPageChange: setPage,
            }}
            columns={[
              {
                id: "pago",
                header: "Pago",
                cell: ({ row }) => (
                  <Link
                    className="underline"
                    to={`/pagos-proveedor/${row.original.id}`}
                  >
                    Pago #{row.original.id}
                  </Link>
                ),
              },
              {
                id: "fecha",
                header: "Fecha",
                cell: ({ row }) => fechaCompra(row.original.fechaPago),
              },
              { accessorKey: "metodoPagoNombre", header: "Método" },
              { accessorKey: "usuarioNombre", header: "Registrado por" },
              {
                id: "importe",
                header: "Importe",
                cell: ({ row }) => importeCompra(row.original.montoPagado),
              },
            ]}
          />
        ))}
    </section>
  );
}

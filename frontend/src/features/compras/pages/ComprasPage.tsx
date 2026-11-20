import { useState } from "react";
import { Link } from "react-router";
import { useSession } from "@/features/auth/session";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { PageHeader } from "@/shared/components/PageHeader";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { CompraFiltros } from "../components/CompraFiltros";
import {
  fechaCompra,
  importeCompra,
  puedeGestionarCompras,
} from "../components/presentacion";
import { useCompras, useEstadosPago } from "../hooks/queries";
import type { CompraFiltros as Filtros } from "../types/compras";

export function ComprasPage() {
  const { user } = useSession();
  const [filtros, setFiltros] = useState<Filtros>({});
  const [page, setPage] = useState(0);
  const compras = useCompras(filtros, page);
  const estados = useEstadosPago();
  const pagina = compras.data?.page;
  return (
    <div className="space-y-5">
      <PageHeader
        title="Compras"
        description="Consultá y registrá compras de abastecimiento."
        action={
          puedeGestionarCompras(user?.rol) && (
            <Button asChild>
              <Link to="/compras/nueva">Nueva compra</Link>
            </Button>
          )
        }
      />
      <CompraFiltros
        aplicar={(valores) => {
          setFiltros(valores);
          setPage(0);
        }}
      />
      {compras.isPending && <LoadingState label="Cargando compras…" />}
      {compras.isError && (
        <ErrorState
          message={compras.error.message}
          onRetry={() => void compras.refetch()}
        />
      )}
      {compras.data &&
        pagina &&
        (compras.data.content.length === 0 && page === 0 ? (
          <EmptyState
            title="Sin compras"
            description="No hay compras para los filtros aplicados."
          />
        ) : (
          <DataTable
            caption="Compras por fecha, de más recientes a más antiguas"
            data={compras.data.content}
            getRowId={(fila) => String(fila.id)}
            pagination={{
              pageIndex: pagina.number,
              totalPages: pagina.totalPages,
              onPageChange: setPage,
            }}
            columns={[
              { accessorKey: "id", header: "Compra" },
              { accessorKey: "proveedorNombre", header: "Proveedor" },
              {
                id: "fecha",
                header: "Fecha",
                cell: ({ row }) => fechaCompra(row.original.fechaCompra),
              },
              {
                id: "comprobante",
                header: "Comprobante",
                cell: ({ row }) => row.original.numeroComprobante || "—",
              },
              {
                id: "total",
                header: "Total",
                cell: ({ row }) => importeCompra(row.original.totalCompra),
              },
              {
                id: "estado",
                header: "Estado de pago",
                cell: ({ row }) => {
                  const codigo = row.original.estadoPagoCodigo;
                  return (
                    estados.data?.find((estado) => estado.codigo === codigo)
                      ?.nombre ?? codigo
                  );
                },
              },
              {
                id: "detalle",
                header: "Detalle",
                cell: ({ row }) => (
                  <Button asChild variant="outline" className="px-3 text-sm">
                    <Link
                      to={`/compras/${row.original.id}`}
                      aria-label={`Ver compra ${row.original.id}`}
                    >
                      Ver compra
                    </Link>
                  </Button>
                ),
              },
            ]}
          />
        ))}
      {pagina && page > 0 && compras.data?.content.length === 0 && (
        <Button variant="outline" onClick={() => setPage(0)}>
          Volver a la primera página
        </Button>
      )}
    </div>
  );
}

import { useQuery } from "@tanstack/react-query";
import { useEffect, useId, useRef, useState } from "react";
import type { ComponentProps } from "react";
import { Link } from "react-router";
import { toast } from "sonner";
import { useSession } from "@/features/auth/session";
import { errorCatalogo } from "@/shared/catalogos/feedback";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import {
  opcionesInsumosSuministrados,
  useCambiarInsumoSuministrado,
} from "../hooks/insumosSuministrados";
import type { InsumoSuministrado } from "../types/insumosSuministrados";
import { AsociarInsumo } from "./AsociarInsumo";

export function InsumosSuministrados({
  proveedorId,
  activo,
}: {
  proveedorId: string;
  activo: boolean;
}) {
  const { api, user } = useSession();
  const titulo = useId();
  const panel = useId();
  const trigger = useRef<HTMLButtonElement>(null);
  const [page, setPage] = useState(0);
  const [asociar, setAsociar] = useState(false);
  const consulta = useQuery(
    opcionesInsumosSuministrados(api, proveedorId, { page, size: 10 }),
  );
  const baja = useCambiarInsumoSuministrado(proveedorId);
  const ultima = Math.max(0, (consulta.data?.page.totalPages ?? 1) - 1);
  const fueraDeRango = consulta.isSuccess && page > ultima;
  const admin = user?.rol === "ADMINISTRADOR" && activo;
  useEffect(() => {
    if (fueraDeRango) setPage(ultima);
  }, [fueraDeRango, ultima]);

  const columns: ComponentProps<
    typeof DataTable<InsumoSuministrado>
  >["columns"] = [
    {
      id: "insumo",
      header: "Insumo",
      cell: ({ row: { original } }) => (
        <Link
          className="font-semibold underline underline-offset-4"
          to={`/insumos/${original.insumoId}`}
        >
          {original.insumoNombre}
        </Link>
      ),
    },
    { accessorKey: "categoriaNombre", header: "Categoría" },
  ];
  if (admin)
    columns.push({
      id: "acciones",
      header: "Acciones",
      cell: ({ row: { original } }) => (
        <ConfirmDialog
          trigger={
            <Button
              variant="outline"
              disabled={baja.isPending}
              aria-label={`Desasociar ${original.insumoNombre}`}
            >
              Desasociar
            </Button>
          }
          title="Desasociar insumo"
          description={`«${original.insumoNombre}» se quitará de los insumos suministrados por este proveedor.`}
          confirmLabel="Desasociar insumo"
          onConfirm={() =>
            baja.mutate(
              { tipo: "desasociar", insumoId: original.insumoId },
              {
                onSuccess: () => toast.success("Insumo desasociado."),
              },
            )
          }
        />
      ),
    });

  return (
    <section className="mt-6 space-y-4" aria-labelledby={titulo}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id={titulo} className="text-lg font-semibold">
          Insumos suministrados
        </h2>
        {admin && (
          <Button
            ref={trigger}
            variant="outline"
            aria-expanded={asociar}
            aria-controls={panel}
            onClick={() => setAsociar((abierto) => !abierto)}
          >
            Asociar insumo
          </Button>
        )}
      </div>
      {admin && (
        <div id={panel} hidden={!asociar}>
          {asociar && (
            <AsociarInsumo
              proveedorId={proveedorId}
              onClose={() => {
                setAsociar(false);
                trigger.current?.focus();
              }}
            />
          )}
        </div>
      )}
      {baja.isPending && <LoadingState label="Desasociando…" />}
      {baja.isError && <ErrorState message={errorCatalogo(baja.error)} />}
      {consulta.isError ? (
        <ErrorState
          message={errorCatalogo(consulta.error)}
          onRetry={() => void consulta.refetch()}
        />
      ) : consulta.isPending || fueraDeRango ? (
        <LoadingState label="Cargando insumos suministrados…" />
      ) : consulta.data.content.length ? (
        <DataTable
          caption="Insumos suministrados"
          data={consulta.data.content}
          columns={columns}
          getRowId={({ proveedorId, insumoId }) => `${proveedorId}:${insumoId}`}
          pagination={{
            pageIndex: consulta.data.page.number,
            totalPages: consulta.data.page.totalPages,
            onPageChange: setPage,
          }}
        />
      ) : (
        <EmptyState
          title="Sin insumos suministrados"
          description="Los insumos con una asociación vigente aparecen en esta ficha."
        />
      )}
    </section>
  );
}

import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { PageHeader } from "@/shared/components/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { DataTable } from "@/shared/components/tables/DataTable";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { selectCatalogo } from "@/shared/catalogos/config";
import { PedidoEstadoBadge } from "../components/PedidoEstadoBadge";
import { useEstadosPedido, usePedidos } from "../hooks/queries";
import {
  errorPedido,
  fechaPedido,
  idPedidoValido,
  importePedido,
} from "../rules";

const ordenes = {
  "id,desc": "Número · más alto primero",
  "id,asc": "Número · más bajo primero",
  "fechaIngreso,desc": "Ingreso · más reciente",
  "fechaIngreso,asc": "Ingreso · más antiguo",
  "fechaEntregaEstimada,asc": "Entrega · más próxima",
  "fechaEntregaEstimada,desc": "Entrega · más lejana",
  "total,desc": "Total · mayor primero",
  "total,asc": "Total · menor primero",
};
const nombresFiltros = ["clienteId", "estado", "desde", "hasta"] as const;

export function PedidosPage() {
  const [params, setParams] = useSearchParams();
  const [errorFiltro, setErrorFiltro] = useState("");
  const pageParam = Number(params.get("page") ?? 0);
  const page =
    Number.isSafeInteger(pageParam) && pageParam >= 0 && pageParam <= 2147483647
      ? pageParam
      : 0;
  const orden = params.get("sort") ?? "";
  const sort = Object.hasOwn(ordenes, orden) ? orden : "id,desc";
  const clienteId = params.get("clienteId") ?? "";
  const estado = params.get("estado") ?? "";
  const desde = params.get("desde") ?? "";
  const hasta = params.get("hasta") ?? "";
  const consulta = usePedidos({
    page,
    size: 20,
    sort,
    clienteId: clienteId || undefined,
    estado: estado || undefined,
    desde: desde || undefined,
    hasta: hasta || undefined,
  });
  const estados = useEstadosPedido();
  const filtroKey = JSON.stringify([clienteId, estado, desde, hasta]);
  const search = params.size ? "?" + params.toString() : "";
  return (
    <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-5">
      <PageHeader
        title="Pedidos"
        action={
          <Button asChild>
            <Link to={{ pathname: "/pedidos/nuevo", search }}>
              Nuevo pedido
            </Link>
          </Button>
        }
      />
      <form
        key={filtroKey}
        aria-label="Filtros de pedidos"
        className="surface grid gap-4 p-4 sm:grid-cols-2 xl:grid-cols-4"
        onSubmit={(event) => {
          event.preventDefault();
          const datos = new FormData(event.currentTarget);
          const cliente = String(datos.get("clienteId") ?? "").trim();
          const inicio = String(datos.get("desde") ?? "");
          const fin = String(datos.get("hasta") ?? "");
          if (cliente && !idPedidoValido(cliente)) {
            setErrorFiltro("Ingresá un ID de cliente positivo y válido.");
            return;
          }
          if (inicio && fin && inicio > fin) {
            setErrorFiltro("Desde debe ser anterior o igual a hasta.");
            return;
          }
          setErrorFiltro("");
          const nuevo = new URLSearchParams({ sort });
          for (const campo of nombresFiltros) {
            const valor = String(datos.get(campo) ?? "").trim();
            if (valor) nuevo.set(campo, valor);
          }
          setParams(nuevo);
        }}
      >
        <FormField id="pedido-filtro-cliente" label="ID del cliente">
          <Input
            id="pedido-filtro-cliente"
            name="clienteId"
            inputMode="numeric"
            defaultValue={clienteId}
          />
        </FormField>
        <FormField id="pedido-filtro-estado" label="Estado">
          <select
            id="pedido-filtro-estado"
            name="estado"
            defaultValue={estado}
            className={selectCatalogo}
          >
            <option value="">Todos los estados</option>
            {estado && !estados.data?.some((s) => s.codigo === estado) && (
              <option value={estado}>{estado}</option>
            )}
            {estados.data?.map((s) => (
              <option key={s.id} value={s.codigo}>
                {s.nombre}
              </option>
            ))}
          </select>
        </FormField>
        <FormField id="pedido-filtro-desde" label="Ingreso desde">
          <Input
            id="pedido-filtro-desde"
            type="date"
            name="desde"
            defaultValue={desde}
          />
        </FormField>
        <FormField id="pedido-filtro-hasta" label="Ingreso hasta">
          <Input
            id="pedido-filtro-hasta"
            type="date"
            name="hasta"
            defaultValue={hasta}
          />
        </FormField>
        <div className="flex flex-wrap items-center gap-2 sm:col-span-2 xl:col-span-4">
          <Button type="submit">Aplicar filtros</Button>
          <Button
            type="button"
            variant="outline"
            onClick={(event) => {
              event.currentTarget.form?.reset();
              setErrorFiltro("");
              setParams({ sort });
            }}
          >
            Limpiar filtros
          </Button>
          {errorFiltro && <ErrorState message={errorFiltro} />}
          {estados.isError && (
            <ErrorState
              message={errorPedido(estados.error)}
              onRetry={() => void estados.refetch()}
            />
          )}
        </div>
      </form>
      <div className="flex justify-end">
        <FormField id="pedido-orden" label="Ordenar por">
          <select
            id="pedido-orden"
            className={selectCatalogo}
            value={sort}
            onChange={(e) => {
              const siguiente = new URLSearchParams(params);
              siguiente.delete("page");
              siguiente.set("sort", e.target.value);
              setParams(siguiente);
            }}
          >
            {Object.entries(ordenes).map(([valor, nombre]) => (
              <option key={valor} value={valor}>
                {nombre}
              </option>
            ))}
          </select>
        </FormField>
      </div>
      {consulta.isPending && <LoadingState label="Cargando pedidos…" />}
      {consulta.isError && (
        <ErrorState
          message={errorPedido(consulta.error)}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {consulta.isSuccess &&
        (consulta.data.content.length ? (
          <DataTable
            caption="Listado de pedidos"
            data={consulta.data.content}
            getRowId={(p) => String(p.id)}
            columns={[
              { header: "Pedido", accessorKey: "id" },
              {
                header: "Cliente",
                id: "cliente",
                cell: ({ row }) =>
                  `${row.original.clienteNombre} ${row.original.clienteApellido}`,
              },
              {
                header: "Estado",
                id: "estado",
                cell: ({ row }) => (
                  <PedidoEstadoBadge
                    codigo={row.original.estadoCodigo}
                    nombre={
                      estados.data?.find(
                        (s) => s.codigo === row.original.estadoCodigo,
                      )?.nombre
                    }
                  />
                ),
              },
              {
                header: "Ingreso",
                id: "ingreso",
                cell: ({ row }) => fechaPedido(row.original.fechaIngreso, true),
              },
              {
                header: "Entrega estimada",
                id: "entrega",
                cell: ({ row }) =>
                  fechaPedido(row.original.fechaEntregaEstimada),
              },
              {
                header: "Total",
                id: "total",
                cell: ({ row }) => importePedido(row.original.total),
              },
              {
                header: "Acciones",
                id: "acciones",
                cell: ({ row }) => (
                  <Link
                    className="font-medium text-primary underline"
                    to={{ pathname: `/pedidos/${row.original.id}`, search }}
                  >
                    Ver pedido {row.original.id}
                  </Link>
                ),
              },
            ]}
            pagination={{
              pageIndex: consulta.data.page.number,
              totalPages: consulta.data.page.totalPages,
              onPageChange: (numero) => {
                const siguiente = new URLSearchParams(params);
                siguiente.set("page", String(numero));
                setParams(siguiente);
              },
            }}
          />
        ) : (
          <EmptyState
            title="Sin pedidos en esta página"
            description="Revisá los filtros o registrá un nuevo pedido."
            action={
              page > 0 ? (
                <Button
                  variant="outline"
                  onClick={() => {
                    const inicial = new URLSearchParams(params);
                    inicial.delete("page");
                    setParams(inicial);
                  }}
                >
                  Ir a la primera página
                </Button>
              ) : undefined
            }
          />
        ))}
    </div>
  );
}

import { useState } from "react";
import { selectCatalogo } from "@/shared/catalogos/config";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import { useEstadosPago } from "../hooks/queries";
import type { CompraFiltros as Filtros } from "../types/compras";

export function CompraFiltros({
  aplicar,
  proveedorInicial,
}: {
  proveedorInicial?: string;
  aplicar: (filtros: Filtros) => void;
}) {
  const estados = useEstadosPago();
  const [proveedor, setProveedor] = useState(proveedorInicial ?? "");
  const [error, setError] = useState("");
  return (
    <form
      className="surface grid gap-4 p-4 sm:grid-cols-2 xl:grid-cols-5"
      onSubmit={(event) => {
        event.preventDefault();
        const datos = new FormData(event.currentTarget);
        const leer = (campo: keyof Filtros) =>
          String(datos.get(campo) ?? "").trim() || undefined;
        const fechaDesde = leer("fechaDesde");
        const fechaHasta = leer("fechaHasta");
        const proveedorId = leer("proveedorId");
        if (
          proveedorId &&
          (!/^\d{1,19}$/.test(proveedorId) ||
            BigInt(proveedorId) <= 0n ||
            BigInt(proveedorId) > 9223372036854775807n)
        ) {
          setError("Ingresá un ID de proveedor válido.");
          return;
        }
        if (fechaDesde && fechaHasta && fechaDesde > fechaHasta) {
          setError(
            "La fecha desde debe ser anterior o igual a la fecha hasta.",
          );
          return;
        }
        setError("");
        aplicar({
          proveedorId,
          fechaDesde,
          fechaHasta,
          estadoPagoCodigo: leer("estadoPagoCodigo"),
          numeroComprobante: leer("numeroComprobante"),
        });
      }}
      onReset={() => {
        setError("");
        setProveedor("");
        aplicar({});
      }}
    >
      <FormField id="filtro-proveedor" label="ID del proveedor">
        <Input
          id="filtro-proveedor"
          name="proveedorId"
          value={proveedor}
          onChange={(event) => setProveedor(event.target.value)}
          inputMode="numeric"
        />
      </FormField>
      <FormField id="filtro-desde" label="Fecha desde">
        <Input id="filtro-desde" name="fechaDesde" type="date" />
      </FormField>
      <FormField id="filtro-hasta" label="Fecha hasta">
        <Input id="filtro-hasta" name="fechaHasta" type="date" />
      </FormField>
      <FormField id="filtro-estado" label="Estado de pago">
        <select
          id="filtro-estado"
          name="estadoPagoCodigo"
          className={selectCatalogo}
          disabled={!estados.data}
        >
          <option value="">Todos</option>
          {estados.data?.map((estado) => (
            <option key={estado.id} value={estado.codigo}>
              {estado.nombre}
            </option>
          ))}
        </select>
      </FormField>
      <FormField id="filtro-comprobante" label="Número de comprobante">
        <Input
          id="filtro-comprobante"
          name="numeroComprobante"
          maxLength={50}
        />
      </FormField>
      <div className="col-span-full flex flex-wrap items-center gap-2">
        <Button type="submit">Aplicar filtros</Button>
        <Button type="reset" variant="outline">
          Limpiar filtros
        </Button>
        {estados.isPending && (
          <LoadingState label="Cargando estados de pago…" />
        )}
        {estados.isError && (
          <ErrorState
            message={estados.error.message}
            onRetry={() => void estados.refetch()}
          />
        )}
        {error && <ErrorState message={error} />}
      </div>
    </form>
  );
}

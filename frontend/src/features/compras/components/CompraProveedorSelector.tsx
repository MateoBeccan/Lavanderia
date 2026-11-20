import { useState, type Ref } from "react";
import { useProveedores } from "@/features/proveedores/hooks/queries";
import { SelectorCatalogo } from "@/shared/catalogos/SelectorCatalogo";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { SelectorPagina } from "./SelectorPagina";

export function CompraProveedorSelector({
  id = "compra-proveedor",
  value,
  nombre,
  onChange,
  onBlur,
  inputRef,
  error,
  disabled,
  placeholder = "Seleccioná un proveedor activo",
}: {
  id?: string;
  value: number | null;
  nombre?: string;
  onChange: (id: number | null, nombre?: string) => void;
  onBlur?: () => void;
  inputRef?: Ref<HTMLSelectElement>;
  error?: string;
  disabled?: boolean;
  placeholder?: string;
}) {
  const [page, setPage] = useState(0);
  const consulta = useProveedores({ page, size: 10 });
  const opciones = consulta.data?.content ?? [];
  return (
    <div className="space-y-2">
      <FormField id={id} label="Proveedor" error={error}>
        <SelectorCatalogo
          id={id}
          ref={inputRef}
          onBlur={onBlur}
          opciones={opciones}
          value={value}
          nombreSeleccionado={nombre}
          disabled={disabled || !consulta.data || consulta.isError}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${id}-error` : undefined}
          placeholder={placeholder}
          onChange={(id) =>
            onChange(id, opciones.find((opcion) => opcion.id === id)?.nombre)
          }
        />
      </FormField>
      <SelectorPagina
        nombre="proveedores"
        page={page}
        total={consulta.data?.page.totalPages ?? 0}
        cambiar={setPage}
      />
      {consulta.isPending && <LoadingState label="Cargando proveedores…" />}
      {consulta.isError && (
        <ErrorState
          message={consulta.error.message}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {consulta.data?.page.totalElements === 0 && (
        <p className="text-sm text-muted-foreground">
          No hay proveedores activos.
        </p>
      )}
    </div>
  );
}

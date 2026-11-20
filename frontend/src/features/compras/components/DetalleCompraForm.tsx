import { Controller, type UseFormReturn } from "react-hook-form";
import { SelectorCatalogo } from "@/shared/catalogos/SelectorCatalogo";
import type { RegistroCatalogo } from "@/shared/catalogos/api";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import {
  subtotalEstimado,
  type CompraFormValues,
  type CompraValidada,
} from "../schemas/compra";
import { importeEstimado } from "./presentacion";

export function DetalleCompraForm({
  form,
  index,
  fila,
  opciones,
  nombre,
  guardarNombre,
  quitar,
  bloqueado,
}: {
  form: UseFormReturn<CompraFormValues, unknown, CompraValidada>;
  index: number;
  fila: CompraFormValues["detalles"][number];
  opciones: RegistroCatalogo[];
  nombre?: string;
  guardarNombre: (id: number, nombre: string) => void;
  quitar: () => void;
  bloqueado: boolean;
}) {
  const error = form.formState.errors.detalles?.[index];
  const id = `renglon-${index + 1}`;
  return (
    <fieldset className="grid min-w-0 gap-3 rounded-lg border p-3 sm:grid-cols-2 xl:grid-cols-[2fr_1fr_1fr_auto]">
      <legend className="px-1 text-sm font-semibold">
        Renglón {index + 1}
      </legend>
      <Controller
        name={`detalles.${index}.insumoId`}
        control={form.control}
        render={({ field }) => (
          <FormField
            id={id + "-insumo"}
            label="Insumo"
            error={error?.insumoId?.message}
          >
            <SelectorCatalogo
              id={id + "-insumo"}
              opciones={opciones}
              value={field.value}
              name={field.name}
              ref={field.ref}
              onBlur={field.onBlur}
              nombreSeleccionado={nombre}
              disabled={bloqueado}
              aria-invalid={Boolean(error?.insumoId)}
              aria-describedby={
                error?.insumoId ? id + "-insumo-error" : undefined
              }
              onChange={(nuevo) => {
                const opcion = opciones.find((item) => item.id === nuevo);
                if (opcion) guardarNombre(opcion.id, opcion.nombre);
                if (field.value !== null && field.value !== nuevo) {
                  form.setValue(`detalles.${index}.precioCompra`, "", {
                    shouldDirty: true,
                  });
                }
                field.onChange(nuevo);
              }}
            />
          </FormField>
        )}
      />
      <FormField
        id={id + "-cantidad"}
        label="Cantidad"
        error={error?.cantidad?.message}
      >
        <Input
          id={id + "-cantidad"}
          inputMode="decimal"
          {...form.register(`detalles.${index}.cantidad`)}
          aria-invalid={Boolean(error?.cantidad)}
          aria-describedby={
            error?.cantidad ? id + "-cantidad-error" : undefined
          }
        />
      </FormField>
      <FormField
        id={id + "-precio"}
        label="Precio de compra"
        error={error?.precioCompra?.message}
      >
        <Input
          id={id + "-precio"}
          inputMode="decimal"
          {...form.register(`detalles.${index}.precioCompra`)}
          aria-invalid={Boolean(error?.precioCompra)}
          aria-describedby={
            error?.precioCompra ? id + "-precio-error" : undefined
          }
        />
      </FormField>
      <div className="flex flex-wrap items-center gap-2 xl:flex-col xl:items-end">
        <span className="text-sm tabular-nums">
          Subtotal estimado{" "}
          {importeEstimado(
            subtotalEstimado(fila.cantidad.trim(), fila.precioCompra.trim()),
          )}
        </span>
        <Button
          type="button"
          variant="outline"
          className="px-3 text-sm"
          aria-label={`Quitar renglón ${index + 1}`}
          onClick={quitar}
        >
          Quitar
        </Button>
      </div>
    </fieldset>
  );
}

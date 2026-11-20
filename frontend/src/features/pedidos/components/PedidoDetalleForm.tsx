import { useId } from "react";
import { useController, useFormContext } from "react-hook-form";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";
import type { PedidoValores } from "../schemas/pedido";
import { PedidoPrendaSelector, PedidoServicioSelector } from "./SelectorPedido";

export function PedidoDetalleForm({
  index,
  quitar,
}: {
  index: number;
  quitar?: () => void;
}) {
  const prefijo = useId();
  const base = `detalles.${index}` as const;
  const {
    control,
    register,
    setValue,
    trigger,
    formState: { errors },
  } = useFormContext<PedidoValores>();
  const prenda = useController({ control, name: `${base}.prendaId` });
  const servicio = useController({ control, name: `${base}.servicioId` });
  const nombrePrenda = useController({ control, name: `${base}.prendaNombre` });
  const nombreServicio = useController({
    control,
    name: `${base}.servicioNombre`,
  });
  const modalidad = useController({ control, name: `${base}.modalidadCobro` });
  const error = errors.detalles?.[index];
  const entradas = [
    {
      campo: "cantidad",
      label: modalidad.field.value === "KILO" ? "Cantidad (kg)" : "Cantidad",
      inputMode: "decimal",
    },
    {
      campo: "estadoPrenda",
      label: "Estado de la prenda (opcional)",
      maxLength: 50,
    },
    {
      campo: "observaciones",
      label: "Observaciones (opcional)",
      maxLength: 250,
    },
  ] as const;

  return (
    <fieldset className="min-w-0 rounded-lg border p-4">
      <legend className="px-2 font-semibold">Renglón {index + 1}</legend>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        <FormField
          id={prefijo + "-prenda"}
          label="Prenda"
          error={error?.prendaId?.message}
        >
          <PedidoPrendaSelector
            id={prefijo + "-prenda"}
            ref={prenda.field.ref}
            onBlur={prenda.field.onBlur}
            value={prenda.field.value}
            nombreSeleccionado={nombrePrenda.field.value}
            aria-invalid={!!error?.prendaId}
            aria-describedby={
              error?.prendaId ? prefijo + "-prenda-error" : undefined
            }
            onSelect={(opcion) => {
              nombrePrenda.field.onChange(opcion?.nombre ?? "");
              prenda.field.onChange(opcion?.id ?? null);
            }}
          />
        </FormField>
        <FormField
          id={prefijo + "-servicio"}
          label="Servicio"
          error={error?.servicioId?.message}
        >
          <PedidoServicioSelector
            id={prefijo + "-servicio"}
            ref={servicio.field.ref}
            onBlur={servicio.field.onBlur}
            value={servicio.field.value}
            nombreSeleccionado={nombreServicio.field.value}
            aria-invalid={!!error?.servicioId}
            aria-describedby={
              error?.servicioId ? prefijo + "-servicio-error" : undefined
            }
            onSelect={(opcion) => {
              nombreServicio.field.onChange(opcion?.nombre ?? "");
              setValue(`${base}.modalidadCobro`, opcion?.modalidadCobro);
              servicio.field.onChange(opcion?.id ?? null);
              if (error?.cantidad) void trigger(`${base}.cantidad`);
            }}
          />
        </FormField>
        {entradas.map(({ campo, label, ...props }) => {
          const id = prefijo + "-" + campo;
          const mensaje = error?.[campo]?.message;
          return (
            <FormField key={campo} id={id} label={label} error={mensaje}>
              <Input
                {...props}
                id={id}
                {...register(`${base}.${campo}`)}
                aria-invalid={!!mensaje}
                aria-describedby={mensaje ? id + "-error" : undefined}
              />
            </FormField>
          );
        })}
      </div>
      {quitar && (
        <Button
          className="mt-4"
          type="button"
          variant="outline"
          onClick={quitar}
        >
          Quitar renglón {index + 1}
        </Button>
      )}
    </fieldset>
  );
}

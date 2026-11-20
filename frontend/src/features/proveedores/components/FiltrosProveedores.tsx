import { useState } from "react";
import type { FormEvent } from "react";
import { FormField } from "@/shared/components/forms/FormField";
import { Input } from "@/shared/components/ui/input";
import { Button } from "@/shared/components/ui/button";
import { cuitSchema } from "../schemas/proveedor";
import type { FiltrosProveedores as Filtros } from "../types/proveedores";

export const camposFiltro = [
  ["nombre", "Nombre"],
  ["cuit", "CUIT"],
  ["telefono", "Teléfono"],
  ["rubro", "Rubro"],
] as const;

export function FiltrosProveedores({
  filtros,
  onApply,
}: {
  filtros: Filtros;
  onApply: (filtros: Filtros) => void;
}) {
  const [error, setError] = useState<string>();

  function aplicar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const next: Filtros = {};
    const data = new FormData(event.currentTarget);
    for (const [campo] of camposFiltro) {
      const valor = String(data.get(campo) ?? "").trim();
      if (valor) next[campo] = valor;
    }
    if (next.cuit) {
      const resultado = cuitSchema.safeParse(next.cuit);
      if (!resultado.success) {
        setError(resultado.error.issues[0].message);
        event.currentTarget
          .querySelector<HTMLInputElement>("#proveedores-filtro-cuit")
          ?.focus();
        return;
      }
    }
    setError(undefined);
    onApply(next);
  }

  return (
    <form
      aria-label="Filtros del listado"
      onSubmit={aplicar}
      className="grid gap-4"
    >
      <div className="grid gap-4 min-[360px]:grid-cols-2 xl:grid-cols-4">
        {camposFiltro.map(([campo, label]) => {
          const id = `proveedores-filtro-${campo}`;
          const mensaje = campo === "cuit" ? error : undefined;
          return (
            <FormField key={campo} id={id} label={label} error={mensaje}>
              <Input
                id={id}
                name={campo}
                type="search"
                defaultValue={filtros[campo]}
                aria-invalid={!!mensaje}
                aria-describedby={mensaje ? `${id}-error` : undefined}
                onChange={
                  campo === "cuit" ? () => setError(undefined) : undefined
                }
              />
            </FormField>
          );
        })}
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="submit">Aplicar filtros</Button>
        <Button
          variant="outline"
          type="reset"
          onClick={() => {
            setError(undefined);
            onApply({});
          }}
        >
          Limpiar filtros
        </Button>
      </div>
    </form>
  );
}

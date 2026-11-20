import type { ComponentProps } from "react";
import type { RegistroCatalogo } from "./api";
import { selectCatalogo } from "./config";

type Props = Omit<
  ComponentProps<"select">,
  "children" | "value" | "onChange"
> & {
  opciones: readonly RegistroCatalogo[];
  value: number | null;
  nombreSeleccionado?: string;
  placeholder?: string;
  onChange: (id: number | null) => void;
};

export function SelectorCatalogo({
  opciones,
  value,
  nombreSeleccionado,
  placeholder = "Seleccioná una opción",
  onChange,
  ...props
}: Props) {
  const visibles = opciones.filter((opcion) => opcion.activo);
  const fueraDePagina =
    value !== null && !visibles.some((opcion) => opcion.id === value);
  return (
    <select
      className={selectCatalogo}
      {...props}
      value={value ?? ""}
      onChange={(event) =>
        onChange(event.target.value ? Number(event.target.value) : null)
      }
    >
      <option value="">{placeholder}</option>
      {fueraDePagina && (
        <option value={value} disabled>
          {nombreSeleccionado || `Selección actual · ID ${value}`}
        </option>
      )}
      {visibles.map(({ id, nombre }) => (
        <option key={id} value={id}>
          {nombre}
        </option>
      ))}
    </select>
  );
}

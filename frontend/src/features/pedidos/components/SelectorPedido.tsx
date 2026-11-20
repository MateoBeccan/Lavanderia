import { useId, useState } from "react";
import type { ComponentProps } from "react";
import type { UseQueryResult } from "@tanstack/react-query";
import { useClientes } from "@/features/clientes/hooks/queries";
import type { ClienteResponse } from "@/features/clientes/types/clientes";
import { usePrendasDisponibles } from "@/features/prendas/hooks/queries";
import { useServiciosDisponibles } from "@/features/servicios/hooks/queries";
import type { ServicioResponse } from "@/features/servicios/types/servicios";
import type { PagedResponse } from "@/shared/api/http";
import type { RegistroCatalogo } from "@/shared/catalogos/api";
import { SelectorCatalogo } from "@/shared/catalogos/SelectorCatalogo";
import { ErrorState, LoadingState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { errorPedido } from "../rules";

type SelectorProps = Pick<
  ComponentProps<typeof SelectorCatalogo>,
  | "id"
  | "value"
  | "nombreSeleccionado"
  | "placeholder"
  | "aria-invalid"
  | "aria-describedby"
  | "ref"
  | "onBlur"
>;
type Props<T> = SelectorProps & { onSelect: (registro?: T) => void };

const pagina = (page: number) => ({ page, size: 10, sort: ["id,asc"] });

function SelectorPaginado<T extends RegistroCatalogo>({
  consulta,
  page,
  setPage,
  onSelect,
  nombreOpcion,
  ...props
}: Props<T> & {
  consulta: UseQueryResult<PagedResponse<T>>;
  page: number;
  setPage: (page: number) => void;
  nombreOpcion?: (registro: T) => string;
}) {
  const etiqueta = useId();
  const opciones = consulta.data?.content ?? [];
  const total = consulta.data?.page.totalPages ?? 0;

  return (
    <>
      <SelectorCatalogo
        {...props}
        opciones={
          nombreOpcion
            ? opciones.map((r) => ({ ...r, nombre: nombreOpcion(r) }))
            : opciones
        }
        disabled={consulta.isPending || consulta.isError}
        onChange={(id) => onSelect(opciones.find((r) => r.id === id))}
      />
      {consulta.isPending && <LoadingState label="Cargando opciones…" />}
      {consulta.isError && (
        <ErrorState
          message={errorPedido(consulta.error)}
          onRetry={() => void consulta.refetch()}
        />
      )}
      {consulta.isSuccess && total === 0 && (
        <p className="text-sm text-muted-foreground">Sin opciones activas.</p>
      )}
      {(total > 1 || page > 0) && (
        <div
          role="group"
          aria-labelledby={etiqueta}
          className="flex flex-wrap items-center gap-2"
        >
          <Button
            variant="outline"
            className="h-8 px-2"
            type="button"
            onClick={() => setPage(page - 1)}
            disabled={page === 0}
          >
            Anterior
          </Button>
          <span
            id={etiqueta}
            aria-live="polite"
            className="text-xs text-muted-foreground"
          >
            Página {page + 1}
            {total > 0 ? ` de ${total}` : ""}
          </span>
          <Button
            variant="outline"
            className="h-8 px-2"
            type="button"
            onClick={() => setPage(page + 1)}
            disabled={page + 1 >= total}
          >
            Siguiente
          </Button>
        </div>
      )}
    </>
  );
}

type ClienteOpcion = RegistroCatalogo;

export function PedidoClienteSelector(props: Props<ClienteOpcion>) {
  const [page, setPage] = useState(0);
  const consulta = useClientes(pagina(page));
  return (
    <SelectorPaginado
      {...props}
      page={page}
      setPage={setPage}
      consulta={consulta}
      nombreOpcion={nombreCliente}
      onSelect={(registro) =>
        props.onSelect(
          registro && { ...registro, nombre: nombreCliente(registro) },
        )
      }
    />
  );
}

export function PedidoPrendaSelector(props: Props<RegistroCatalogo>) {
  const [page, setPage] = useState(0);
  const consulta = usePrendasDisponibles(pagina(page));
  return (
    <SelectorPaginado
      {...props}
      consulta={consulta}
      page={page}
      setPage={setPage}
    />
  );
}

const nombreCliente = ({ nombre, apellido, dni }: ClienteResponse) =>
  `${nombre} ${apellido}${dni ? ` · DNI ${dni}` : ""}`;

export function PedidoServicioSelector(props: Props<ServicioResponse>) {
  const [page, setPage] = useState(0);
  const consulta = useServiciosDisponibles(pagina(page));
  return (
    <SelectorPaginado
      {...props}
      consulta={consulta}
      page={page}
      setPage={setPage}
    />
  );
}

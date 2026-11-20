import { useState } from "react";
import {
  Link,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from "react-router";
import { toast } from "sonner";
import type { ReactNode } from "react";
import { useSession } from "@/features/auth/session";
import { ApiError } from "@/shared/api/http";
import { errorCatalogo } from "@/shared/catalogos/feedback";
import { idCatalogoValido } from "@/shared/catalogos/schema";
import { EstadoActivo } from "@/shared/components/EstadoActivo";
import { PageHeader } from "@/shared/components/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { FormField } from "@/shared/components/forms/FormField";
import { Button } from "@/shared/components/ui/button";
import { ConfirmDialog } from "@/shared/components/ui/confirm-dialog";
import { Input } from "@/shared/components/ui/input";
import { catalogo } from "../components/catalogo";
import { CatalogoInsumosForm } from "../components/CatalogoInsumosForm";
import {
  useCambiarRegistroInsumos,
  useRegistroInsumos,
} from "../hooks/queries";
import type { TipoCatalogoInsumos } from "../types/insumos";
import { CategoriasInsumoPage, InsumosPage } from "./CatalogoInsumosListados";

function Volver({ tipo }: { tipo: TipoCatalogoInsumos }) {
  const { search } = useLocation();
  return (
    <Button asChild variant="outline">
      <Link to={{ pathname: catalogo[tipo].path, search }}>
        Volver al listado
      </Link>
    </Button>
  );
}

function Ausente({ tipo }: { tipo: TipoCatalogoInsumos }) {
  return (
    <EmptyState
      title={
        tipo === "insumo" ? "Insumo no encontrado" : "Categoría no encontrada"
      }
      description={
        tipo === "insumo"
          ? "El insumo puede estar desactivado."
          : "La categoría puede estar desactivada."
      }
      action={<Volver tipo={tipo} />}
    />
  );
}

function SoloAdministrador({
  tipo,
  children,
}: {
  tipo: TipoCatalogoInsumos;
  children: ReactNode;
}) {
  const { user } = useSession();
  return user?.rol === "ADMINISTRADOR" ? (
    children
  ) : (
    <EmptyState
      title="Acceso restringido"
      description="La operación requiere el rol de administrador."
      action={<Volver tipo={tipo} />}
    />
  );
}

function Registro({
  tipo,
  id,
  editar,
}: {
  tipo: TipoCatalogoInsumos;
  id: string;
  editar: boolean;
}) {
  const { user } = useSession();
  const { search } = useLocation();
  const navigate = useNavigate();
  const { path } = catalogo[tipo];
  const query = useRegistroInsumos(tipo, id);
  const baja = useCambiarRegistroInsumos(tipo);
  if (query.isPending) return <LoadingState label={`Cargando ${tipo}…`} />;
  if (query.isError) {
    return query.error instanceof ApiError && query.error.status === 404 ? (
      <Ausente tipo={tipo} />
    ) : (
      <div className="grid justify-items-start gap-4">
        <ErrorState
          message={errorCatalogo(query.error)}
          onRetry={() => void query.refetch()}
        />
        <Volver tipo={tipo} />
      </div>
    );
  }
  const registro = query.data;
  if (editar)
    return (
      <>
        <PageHeader title={`Editar ${tipo}`} description={registro.nombre} />
        <CatalogoInsumosForm tipo={tipo} registro={registro} />
      </>
    );
  const tituloBaja = `Desactivar ${tipo}`;
  return (
    <>
      <PageHeader
        title={registro.nombre}
        description={`Detalle de ${tipo}`}
        action={
          <div className="flex flex-wrap gap-2">
            <Volver tipo={tipo} />
            {user?.rol === "ADMINISTRADOR" && registro.activo && (
              <>
                <Button asChild>
                  <Link to={{ pathname: `${path}/${id}/editar`, search }}>
                    Editar {tipo}
                  </Link>
                </Button>
                <ConfirmDialog
                  trigger={
                    <Button variant="outline" disabled={baja.isPending}>
                      {tituloBaja}
                    </Button>
                  }
                  title={tituloBaja}
                  description={`«${registro.nombre}» dejará de estar disponible en el catálogo. Guardá el ID ${id} para reactivar el registro.`}
                  confirmLabel={tituloBaja}
                  onConfirm={() =>
                    baja.mutate(
                      { tipo: "desactivar", id },
                      {
                        onSuccess: () => {
                          toast.success(`Baja confirmada · ID ${id}.`);
                          void navigate(
                            { pathname: path, search },
                            { replace: true },
                          );
                        },
                      },
                    )
                  }
                />
              </>
            )}
          </div>
        }
      />
      {baja.isPending && <LoadingState label="Desactivando…" />}
      {baja.isError && (
        <div className="mb-4">
          <ErrorState message={errorCatalogo(baja.error)} />
        </div>
      )}
      <dl className="surface grid gap-5 p-4 sm:grid-cols-2 sm:p-6 lg:grid-cols-3">
        <div>
          <dt className="text-sm text-muted-foreground">ID</dt>
          <dd className="mt-1">{registro.id}</dd>
        </div>
        {"categoriaNombre" in registro && (
          <div>
            <dt className="text-sm text-muted-foreground">Categoría</dt>
            <dd className="mt-1 wrap-anywhere">
              <Link
                className="underline underline-offset-4"
                to={`${catalogo.categoría.path}/${registro.categoriaId}`}
              >
                {registro.categoriaNombre}
              </Link>
            </dd>
          </div>
        )}
        <div>
          <dt className="text-sm text-muted-foreground">Estado</dt>
          <dd className="mt-1">
            <EstadoActivo activo={registro.activo} />
          </dd>
        </div>
      </dl>
    </>
  );
}

function RegistroPorId({
  tipo,
  editar = false,
}: {
  tipo: TipoCatalogoInsumos;
  editar?: boolean;
}) {
  const { id = "" } = useParams();
  return idCatalogoValido(id) ? (
    <Registro
      key={`${tipo}/${id}/${editar}`}
      tipo={tipo}
      id={id}
      editar={editar}
    />
  ) : (
    <Ausente tipo={tipo} />
  );
}

function Reactivar({ tipo }: { tipo: TipoCatalogoInsumos }) {
  const [valor, setValor] = useState("");
  const id = valor.trim();
  const valido = idCatalogoValido(id);
  const error =
    id && !valido ? "Ingresá un ID entero positivo válido." : undefined;
  const mutation = useCambiarRegistroInsumos(tipo);
  const navigate = useNavigate();
  const { search } = useLocation();
  const titulo = `Reactivar ${tipo}`;
  return (
    <>
      <PageHeader
        title={titulo}
        description="Ingresá el ID que figura en la ficha o en la confirmación de baja."
        action={<Volver tipo={tipo} />}
      />
      <section className="surface space-y-4 p-4 sm:p-6" aria-label={titulo}>
        <div className="max-w-sm">
          <FormField id="reactivar-id" label="ID del registro" error={error}>
            <Input
              id="reactivar-id"
              inputMode="numeric"
              value={valor}
              disabled={mutation.isPending}
              aria-invalid={!!error}
              aria-describedby={error && "reactivar-id-error"}
              onChange={(event) => {
                mutation.reset();
                setValor(event.target.value);
              }}
            />
          </FormField>
        </div>
        {mutation.isError && (
          <ErrorState message={errorCatalogo(mutation.error)} />
        )}
        <ConfirmDialog
          trigger={
            <Button disabled={!valido || mutation.isPending}>
              {mutation.isPending ? "Reactivando…" : titulo}
            </Button>
          }
          title={titulo}
          description={`El registro con ID ${id} volverá a estar disponible en el catálogo.`}
          confirmLabel={titulo}
          onConfirm={() =>
            mutation.mutate(
              { tipo: "activar", id },
              {
                onSuccess: () => {
                  toast.success(`Activación confirmada · ID ${id}.`);
                  void navigate(
                    { pathname: `${catalogo[tipo].path}/${id}`, search },
                    { replace: true },
                  );
                },
              },
            )
          }
        />
      </section>
    </>
  );
}

export function InsumosRoutes() {
  return (
    <Routes>
      <Route index element={<InsumosPage />} />
      <Route path="categorias">
        <Route index element={<CategoriasInsumoPage />} />
        <Route
          path="nuevo"
          element={
            <SoloAdministrador tipo="categoría">
              <PageHeader title="Nueva categoría" />
              <CatalogoInsumosForm key="categoría" tipo="categoría" />
            </SoloAdministrador>
          }
        />
        <Route
          path="reactivar"
          element={
            <SoloAdministrador tipo="categoría">
              <Reactivar key="categoría" tipo="categoría" />
            </SoloAdministrador>
          }
        />
        <Route path=":id" element={<RegistroPorId tipo="categoría" />} />
        <Route
          path=":id/editar"
          element={
            <SoloAdministrador tipo="categoría">
              <RegistroPorId tipo="categoría" editar />
            </SoloAdministrador>
          }
        />
      </Route>
      <Route
        path="nuevo"
        element={
          <SoloAdministrador tipo="insumo">
            <PageHeader title="Nuevo insumo" />
            <CatalogoInsumosForm key="insumo" tipo="insumo" />
          </SoloAdministrador>
        }
      />
      <Route
        path="reactivar"
        element={
          <SoloAdministrador tipo="insumo">
            <Reactivar key="insumo" tipo="insumo" />
          </SoloAdministrador>
        }
      />
      <Route path=":id" element={<RegistroPorId tipo="insumo" />} />
      <Route
        path=":id/editar"
        element={
          <SoloAdministrador tipo="insumo">
            <RegistroPorId tipo="insumo" editar />
          </SoloAdministrador>
        }
      />
      <Route path="*" element={<Ausente tipo="insumo" />} />
    </Routes>
  );
}

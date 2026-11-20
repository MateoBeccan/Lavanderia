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
import { useSession } from "@/features/auth/session";
import { ApiError } from "@/shared/api/http";
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
import type { RegistroCatalogo } from "./api";
import type { ConfigCatalogo } from "./config";
import { CatalogoForm } from "./CatalogoForm";
import { CatalogoListado } from "./CatalogoListado";
import { errorCatalogo } from "./feedback";
import { useModificarCatalogo, useRegistroCatalogo } from "./queries";
import { idCatalogoValido } from "./schema";

function Volver({ path }: { path: string }) {
  const { search } = useLocation();
  return (
    <Button asChild variant="outline">
      <Link to={{ pathname: path, search }}>Volver al listado</Link>
    </Button>
  );
}

function SoloAdministrador({
  children,
  path,
}: {
  children: React.ReactNode;
  path: string;
}) {
  const { user } = useSession();
  if (user?.rol === "ADMINISTRADOR") return children;
  return (
    <EmptyState
      title="Acceso restringido"
      description="Tu rol no tiene permiso para realizar esta operación."
      action={<Volver path={path} />}
    />
  );
}

function Ausente({ path }: { path: string }) {
  return (
    <EmptyState
      title="Registro no encontrado"
      description="El registro no existe o está desactivado."
      action={<Volver path={path} />}
    />
  );
}

function RegistroExistente<Request, Response extends RegistroCatalogo>({
  config,
  id,
  editar,
}: {
  config: ConfigCatalogo<Request, Response>;
  id: string;
  editar: boolean;
}) {
  const { user } = useSession();
  const { search } = useLocation();
  const consulta = useRegistroCatalogo(config.api, id);
  const { path } = config.api;
  if (consulta.isPending)
    return <LoadingState label={`Cargando ${config.singular}…`} />;
  const registro = consulta.data;
  const error = consulta.isError && (
    <ErrorState
      message={errorCatalogo(consulta.error)}
      onRetry={() => void consulta.refetch()}
    />
  );
  if (consulta.isError && (!editar || !registro)) {
    return consulta.error instanceof ApiError &&
      consulta.error.status === 404 ? (
      <Ausente path={path} />
    ) : (
      error
    );
  }
  if (!registro) return <Ausente path={path} />;
  if (editar)
    return (
      <>
        <PageHeader
          title={`Editar ${config.singular}`}
          description={registro.nombre}
        />
        {error && <div className="mb-4">{error}</div>}
        <CatalogoForm config={config} registro={registro} />
      </>
    );

  const datos = [
    { label: "ID", valor: registro.id },
    { label: "Estado", valor: registro.activo ? "Activo" : "Inactivo" },
    ...(config.datos ?? []).map(({ label, valor }) => ({
      label,
      valor: valor(registro),
    })),
  ];
  return (
    <>
      <PageHeader
        title={registro.nombre}
        description={`Detalle de ${config.singular}`}
        action={
          <div className="flex flex-wrap gap-2">
            <Volver path={path} />
            {user?.rol === "ADMINISTRADOR" && (
              <Button asChild>
                <Link
                  to={{ pathname: `${path}/${id}/editar`, search }}
                >{`Editar ${config.singular}`}</Link>
              </Button>
            )}
          </div>
        }
      />
      <dl className="surface grid gap-5 p-4 sm:grid-cols-2 sm:p-6 lg:grid-cols-3">
        {datos.map(({ label, valor }) => (
          <div key={label}>
            <dt className="text-sm text-muted-foreground">{label}</dt>
            <dd className="mt-1 wrap-anywhere">{valor}</dd>
          </div>
        ))}
      </dl>
    </>
  );
}

function RegistroPorId<Request, Response extends RegistroCatalogo>({
  config,
  editar = false,
}: {
  config: ConfigCatalogo<Request, Response>;
  editar?: boolean;
}) {
  const { id = "" } = useParams();
  if (!idCatalogoValido(id)) return <Ausente path={config.api.path} />;
  return <RegistroExistente key={id} id={id} config={config} editar={editar} />;
}

function Reactivar<Request, Response extends RegistroCatalogo>({
  config,
}: {
  config: ConfigCatalogo<Request, Response>;
}) {
  const [value, setValue] = useState("");
  const id = value.trim();
  const valido = idCatalogoValido(id);
  const mutation = useModificarCatalogo(config.api);
  const navigate = useNavigate();
  const { search } = useLocation();
  const { path } = config.api;
  const titulo = `Reactivar ${config.singular}`;
  return (
    <>
      <PageHeader
        title={titulo}
        description="Ingresá el ID del registro. El listado solo muestra registros activos."
        action={<Volver path={path} />}
      />
      <section className="surface space-y-4 p-4 sm:p-6" aria-label={titulo}>
        <div className="max-w-sm">
          <FormField
            id="reactivar-id"
            label="ID del registro"
            error={
              id && !valido
                ? "Ingresá un ID entero positivo válido."
                : undefined
            }
          >
            <Input
              id="reactivar-id"
              inputMode="numeric"
              value={value}
              disabled={mutation.isPending}
              aria-invalid={!!id && !valido}
              aria-describedby={
                id && !valido ? "reactivar-id-error" : undefined
              }
              onChange={(event) => {
                mutation.reset();
                setValue(event.target.value);
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
          description={`El registro con ID ${id} quedará disponible en el catálogo.`}
          confirmLabel={titulo}
          onConfirm={() =>
            mutation.mutate(
              { tipo: "activar", id },
              {
                onSuccess: () => {
                  toast.success(`Activación confirmada · ID ${id}.`);
                  void navigate(
                    { pathname: `${path}/${id}`, search },
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

export function CatalogoRoutes<Request, Response extends RegistroCatalogo>({
  config,
}: {
  config: ConfigCatalogo<Request, Response>;
}) {
  const { path } = config.api;
  return (
    <Routes>
      <Route index element={<CatalogoListado config={config} />} />
      <Route
        path="nuevo"
        element={
          <SoloAdministrador path={path}>
            <PageHeader title={`Nuev${config.genero} ${config.singular}`} />
            <CatalogoForm config={config} />
          </SoloAdministrador>
        }
      />
      <Route
        path="reactivar"
        element={
          <SoloAdministrador path={path}>
            <Reactivar config={config} />
          </SoloAdministrador>
        }
      />
      <Route path=":id" element={<RegistroPorId config={config} />} />
      <Route
        path=":id/editar"
        element={
          <SoloAdministrador path={path}>
            <RegistroPorId config={config} editar />
          </SoloAdministrador>
        }
      />
      <Route path="*" element={<Ausente path={path} />} />
    </Routes>
  );
}

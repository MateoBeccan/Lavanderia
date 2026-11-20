import {
  Link,
  Route,
  Routes,
  useLocation,
  useNavigate,
  useParams,
} from "react-router";
import { useSession } from "@/features/auth/session";
import { idCatalogoValido } from "@/shared/catalogos/schema";
import { ApiError } from "@/shared/api/http";
import { EstadoActivo } from "@/shared/components/EstadoActivo";
import { PageHeader } from "@/shared/components/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { DesactivarProveedor } from "../components/DesactivarProveedor";
import { InsumosSuministrados } from "../components/InsumosSuministrados";
import { ProveedorForm } from "../components/ProveedorForm";
import { proveedorError } from "../components/feedback";
import { useProveedor } from "../hooks/queries";
import { ProveedoresPage } from "./ProveedoresPage";
import { ReactivarProveedorPage } from "./ReactivarProveedorPage";

function VolverAProveedores() {
  const { search } = useLocation();
  return (
    <Button asChild variant="outline">
      <Link to={{ pathname: "/proveedores", search }}>
        Volver a proveedores
      </Link>
    </Button>
  );
}

function ProveedorNoEncontrado() {
  return (
    <EmptyState
      title="Proveedor no encontrado"
      description="El proveedor no existe o está desactivado."
      action={<VolverAProveedores />}
    />
  );
}

function AccesoRestringido() {
  return (
    <EmptyState
      title="Acceso restringido"
      description="La operación requiere el rol de administrador."
      action={<VolverAProveedores />}
    />
  );
}

function ProveedorExistente({ id, editar }: { id: string; editar: boolean }) {
  const { user } = useSession();
  const { search } = useLocation();
  const navigate = useNavigate();
  const consulta = useProveedor(id);
  if (consulta.isPending) return <LoadingState label="Cargando proveedor…" />;
  const proveedor = consulta.data;
  const error = consulta.isError && (
    <ErrorState
      message={proveedorError(consulta.error)}
      onRetry={() => void consulta.refetch()}
    />
  );
  if (!proveedor || (consulta.isError && !editar)) {
    if (consulta.error instanceof ApiError && consulta.error.status === 404)
      return <ProveedorNoEncontrado />;
    return (
      <div className="grid justify-items-start gap-4">
        {error}
        <VolverAProveedores />
      </div>
    );
  }
  if (editar) {
    return (
      <>
        <PageHeader title="Editar proveedor" description={proveedor.nombre} />
        {error && <div className="mb-4">{error}</div>}
        <ProveedorForm proveedor={proveedor} />
      </>
    );
  }

  const datos = [
    ["ID", proveedor.id],
    ["CUIT", proveedor.cuit],
    ["Teléfono", proveedor.telefono],
    ["Rubro", proveedor.rubro ?? "—"],
    ["Estado", <EstadoActivo activo={proveedor.activo} />],
  ] as const;

  return (
    <>
      <PageHeader
        title={proveedor.nombre}
        description="Detalle del proveedor"
        action={
          <div className="flex flex-wrap gap-2">
            <VolverAProveedores />
            {user?.rol === "ADMINISTRADOR" && (
              <>
                <Button asChild>
                  <Link to={{ pathname: `/proveedores/${id}/editar`, search }}>
                    Editar proveedor
                  </Link>
                </Button>
                <DesactivarProveedor
                  proveedor={proveedor}
                  onSuccess={() =>
                    void navigate(
                      { pathname: "/proveedores", search },
                      { replace: true },
                    )
                  }
                />
              </>
            )}
          </div>
        }
      />
      <section className="surface p-4 sm:p-6" aria-labelledby="proveedor-datos">
        <h2 id="proveedor-datos" className="mb-5 text-lg font-semibold">
          Datos del proveedor
        </h2>
        <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {datos.map(([label, value]) => (
            <div key={label}>
              <dt className="text-sm text-muted-foreground">{label}</dt>
              <dd className="mt-1 wrap-anywhere">{value}</dd>
            </div>
          ))}
        </dl>
      </section>
      <Button asChild variant="outline">
        <Link to={`/pagos-proveedor?proveedorId=${id}`}>
          Consultar pagos del proveedor
        </Link>
      </Button>
      <InsumosSuministrados proveedorId={id} activo={proveedor.activo} />
    </>
  );
}

function ProveedorPorId({ editar = false }: { editar?: boolean }) {
  const { id = "" } = useParams();
  if (!idCatalogoValido(id)) return <ProveedorNoEncontrado />;
  return <ProveedorExistente key={id} id={id} editar={editar} />;
}

export function ProveedorRoutes() {
  const { user } = useSession();
  const admin = user?.rol === "ADMINISTRADOR";
  return (
    <Routes>
      <Route index element={<ProveedoresPage />} />
      <Route
        path="nuevo"
        element={
          admin ? (
            <>
              <PageHeader title="Nuevo proveedor" />
              <ProveedorForm />
            </>
          ) : (
            <AccesoRestringido />
          )
        }
      />
      <Route
        path="reactivar"
        element={admin ? <ReactivarProveedorPage /> : <AccesoRestringido />}
      />
      <Route path=":id" element={<ProveedorPorId />} />
      <Route
        path=":id/editar"
        element={admin ? <ProveedorPorId editar /> : <AccesoRestringido />}
      />
      <Route path="*" element={<ProveedorNoEncontrado />} />
    </Routes>
  );
}

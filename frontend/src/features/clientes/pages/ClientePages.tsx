import { Link, Route, Routes, useLocation, useParams } from "react-router";
import { PageHeader } from "@/shared/components/PageHeader";
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { ApiError } from "@/shared/api/http";
import { ClienteForm } from "../components/ClienteForm";
import { EstadoActivo } from "@/shared/components/EstadoActivo";
import { clienteError } from "../components/feedback";
import { useCliente } from "../hooks/queries";
import { ClientesPage } from "./ClientesPage";

const fecha = new Intl.DateTimeFormat("es-AR", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "UTC",
});

function VolverAClientes() {
  const { search } = useLocation();
  return (
    <Button asChild variant="outline">
      <Link to={{ pathname: "/clientes", search }}>Volver a clientes</Link>
    </Button>
  );
}

function ClienteNoEncontrado() {
  return (
    <EmptyState
      title="Cliente no encontrado"
      description="El cliente no existe o está desactivado."
      action={<VolverAClientes />}
    />
  );
}

function ClienteExistente({ id, editar }: { id: string; editar: boolean }) {
  const { search } = useLocation();
  const consulta = useCliente(id);

  if (consulta.isPending) return <LoadingState label="Cargando cliente…" />;
  if (consulta.isError && (!editar || !consulta.data)) {
    if (consulta.error instanceof ApiError && consulta.error.status === 404)
      return <ClienteNoEncontrado />;
    return (
      <ErrorState
        message={clienteError(consulta.error)}
        onRetry={() => void consulta.refetch()}
      />
    );
  }

  const cliente = consulta.data;
  if (!cliente) return <ClienteNoEncontrado />;
  const nombre = cliente.nombre + " " + cliente.apellido;
  if (editar) {
    return (
      <>
        <PageHeader title="Editar cliente" description={nombre} />
        {consulta.isError && (
          <div className="mb-4">
            <ErrorState
              message={clienteError(consulta.error)}
              onRetry={() => void consulta.refetch()}
            />
          </div>
        )}
        <ClienteForm cliente={cliente} />
      </>
    );
  }

  // LocalDateTime llega sin zona. Mostrar la hora de negocio sin desplazarla.
  const creada = new Date(cliente.fechaCreacion + "Z");
  const datos = [
    { label: "ID", value: cliente.id },
    { label: "DNI", value: cliente.dni ?? "—" },
    { label: "Teléfono", value: cliente.telefono ?? "—" },
    { label: "Correo electrónico", value: cliente.email ?? "—" },
    { label: "Estado", value: <EstadoActivo activo={cliente.activo} /> },
    {
      label: "Fecha de alta",
      value: Number.isNaN(creada.getTime()) ? (
        cliente.fechaCreacion
      ) : (
        <time dateTime={cliente.fechaCreacion}>{fecha.format(creada)}</time>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title={nombre}
        description="Detalle del cliente"
        action={
          <div className="flex flex-wrap gap-2">
            <VolverAClientes />
            <Button asChild>
              <Link to={{ pathname: `/clientes/${id}/editar`, search }}>
                Editar cliente
              </Link>
            </Button>
          </div>
        }
      />
      <section className="surface p-4 sm:p-6" aria-labelledby="cliente-datos">
        <h2 id="cliente-datos" className="mb-5 text-lg font-semibold">
          Datos del cliente
        </h2>
        <dl className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
          {datos.map(({ label, value }) => (
            <div key={label}>
              <dt className="text-sm text-muted-foreground">{label}</dt>
              <dd className="mt-1 wrap-anywhere">{value}</dd>
            </div>
          ))}
        </dl>
      </section>
    </>
  );
}

function ClientePorId({ editar = false }: { editar?: boolean }) {
  const { id = "" } = useParams();
  if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > 9223372036854775807n)
    return <ClienteNoEncontrado />;
  return <ClienteExistente key={id} id={id} editar={editar} />;
}

function NuevoCliente() {
  return (
    <>
      <PageHeader
        title="Nuevo cliente"
        description="Ingresá los datos de la persona que querés registrar."
      />
      <ClienteForm />
    </>
  );
}

export function ClienteRoutes() {
  return (
    <Routes>
      <Route index element={<ClientesPage />} />
      <Route path="nuevo" element={<NuevoCliente />} />
      <Route path=":id" element={<ClientePorId />} />
      <Route path=":id/editar" element={<ClientePorId editar />} />
      <Route path="*" element={<ClienteNoEncontrado />} />
    </Routes>
  );
}

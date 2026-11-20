import { ArrowRight, ClipboardList, ShoppingCart, Wallet } from "lucide-react";
import { Link, NavLink, Outlet, Route, Routes } from "react-router";
import { PageHeader } from "@/shared/components/PageHeader";
import { EmptyState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { cn } from "@/shared/lib/utils";
import { ReporteCaja } from "./ReporteCaja";
import { ReporteCompras } from "./ReporteCompras";
import { ReportePedidos } from "./ReportePedidos";

const reportes = [
  {
    ruta: "pedidos",
    nombre: "Pedidos",
    descripcion: "Consultá pedidos por ingreso, cliente y estado.",
    icono: ClipboardList,
    Vista: ReportePedidos,
  },
  {
    ruta: "movimientos-caja",
    nombre: "Movimientos de Caja",
    descripcion: "Revisá ingresos y egresos por tipo y método de pago.",
    icono: Wallet,
    Vista: ReporteCaja,
  },
  {
    ruta: "compras",
    nombre: "Compras",
    descripcion: "Revisá compras por fecha, proveedor y estado de pago.",
    icono: ShoppingCart,
    Vista: ReporteCompras,
  },
];

function InicioReportes() {
  return (
    <section
      aria-label="Reportes disponibles"
      className="grid gap-4 md:grid-cols-3"
    >
      {reportes.map(({ ruta, nombre, descripcion, icono: Icono }) => (
        <Link
          key={ruta}
          to={`/reportes/${ruta}`}
          className="surface group flex min-w-0 flex-col gap-4 p-5 transition-colors hover:border-primary/60 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary"
        >
          <span className="icon-tile">
            <Icono size={20} aria-hidden="true" />
          </span>
          <div className="space-y-1">
            <h2 className="text-lg font-semibold">{nombre}</h2>
            <p className="text-sm text-muted-foreground">{descripcion}</p>
          </div>
          <span className="mt-auto self-end text-primary">
            <ArrowRight size={18} aria-hidden="true" />
          </span>
        </Link>
      ))}
    </section>
  );
}

function NavegacionReportes() {
  return (
    <>
      <nav
        aria-label="Cambiar de reporte"
        className="flex flex-wrap gap-1 rounded-lg bg-muted p-1"
      >
        {reportes.map(({ ruta, nombre }) => (
          <NavLink
            key={ruta}
            to={`/reportes/${ruta}`}
            end
            className={({ isActive }) =>
              cn(
                "rounded-md px-3 py-2 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-primary",
                isActive
                  ? "bg-card text-primary shadow-sm"
                  : "text-muted-foreground hover:text-foreground",
              )
            }
          >
            {nombre}
          </NavLink>
        ))}
      </nav>
      <Outlet />
    </>
  );
}

export function ReportesRoutes() {
  return (
    <div className="grid min-w-0 grid-cols-[minmax(0,1fr)] gap-4">
      <Routes>
        <Route
          index
          element={
            <>
              <PageHeader
                title="Reportes administrativos"
                description="Consultá la actividad de la lavandería y descargá reportes filtrados en PDF."
              />
              <InicioReportes />
            </>
          }
        />
        <Route element={<NavegacionReportes />}>
          {reportes.map(({ ruta, nombre, Vista }) => (
            <Route
              key={ruta}
              path={ruta}
              element={
                <>
                  <PageHeader
                    title={nombre}
                    action={
                      <Button asChild variant="outline">
                        <Link to="/reportes">Todos los reportes</Link>
                      </Button>
                    }
                  />
                  <Vista />
                </>
              }
            />
          ))}
        </Route>
        <Route
          path="*"
          element={
            <EmptyState
              title="Reporte no encontrado"
              description="Elegí un reporte desde el centro administrativo."
              action={
                <Button asChild variant="outline">
                  <Link to="/reportes">Todos los reportes</Link>
                </Button>
              }
            />
          }
        />
      </Routes>
    </div>
  );
}

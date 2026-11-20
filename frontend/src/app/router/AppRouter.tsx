import { Link, Navigate, Route, Routes, useLocation } from "react-router";
import { lazy, Suspense } from "react";
import { LoginPage } from "@/features/auth/LoginPage";
import { useSession } from "@/features/auth/session";
import { PageHeader } from "@/shared/components/PageHeader";
import { EmptyState, LoadingState } from "@/shared/components/feedback/States";
import { Button } from "@/shared/components/ui/button";
import { canAccess, navigation } from "./navigation";

const AppLayout = lazy(async () => ({
  default: (await import("@/app/layouts/AppLayout")).AppLayout,
}));
const Dashboard = lazy(async () => ({
  default: (await import("@/features/dashboard/Dashboard")).Dashboard,
}));
const ClienteRoutes = lazy(async () => ({
  default: (await import("@/features/clientes/pages/ClientePages"))
    .ClienteRoutes,
}));
const ProveedorRoutes = lazy(async () => ({
  default: (await import("@/features/proveedores/pages/ProveedorRoutes"))
    .ProveedorRoutes,
}));
const InsumosRoutes = lazy(async () => ({
  default: (await import("@/features/insumos/pages/InsumosRoutes"))
    .InsumosRoutes,
}));
const CompraRoutes = lazy(async () => ({
  default: (await import("@/features/compras/pages/CompraRoutes")).CompraRoutes,
}));
const ServicioRoutes = lazy(async () => ({
  default: (await import("@/features/servicios/pages/ServicioRoutes"))
    .ServicioRoutes,
}));
const PrendaRoutes = lazy(async () => ({
  default: (await import("@/features/prendas/pages/PrendaRoutes")).PrendaRoutes,
}));
const PedidoRoutes = lazy(async () => ({
  default: (await import("@/features/pedidos/pages/PedidoRoutes")).PedidoRoutes,
}));

const CajaRoutes = lazy(async () => ({
  default: (await import("@/features/caja/pages/CajaRoutes")).CajaRoutes,
}));
const PagoProveedorRoutes = lazy(async () => ({
  default: (
    await import("@/features/pagos-proveedor/pages/PagoProveedorRoutes")
  ).PagoProveedorRoutes,
}));
const CobroRoutes = lazy(async () => ({
  default: (await import("@/features/cobros/pages/CobroRoutes")).CobroRoutes,
}));
const ComprobanteRoutes = lazy(async () => ({
  default: (await import("@/features/comprobantes/pages/ComprobanteRoutes"))
    .ComprobanteRoutes,
}));
const ReportesRoutes = lazy(async () => ({
  default: (await import("@/features/reportes/pages/ReportesRoutes"))
    .ReportesRoutes,
}));

function MessagePage({
  title,
  message,
  description,
}: {
  title: string;
  message: string;
  description: string;
}) {
  return (
    <>
      <PageHeader title={title} />
      <EmptyState
        title={message}
        description={description}
        action={
          <Button asChild variant="outline">
            <Link to="/">Volver al inicio</Link>
          </Button>
        }
      />
    </>
  );
}

export function AppRouter() {
  const { user } = useSession();
  const location = useLocation();
  const returnPath = location.state?.from;
  const destination =
    typeof returnPath === "string" &&
    returnPath.startsWith("/") &&
    !returnPath.startsWith("//") &&
    !returnPath.startsWith("/login")
      ? returnPath
      : "/";

  return (
    <Routes>
      <Route
        path="/login"
        element={user ? <Navigate to={destination} replace /> : <LoginPage />}
      />
      <Route
        element={
          user ? (
            <Suspense
              fallback={
                <main className="p-8">
                  <LoadingState />
                </main>
              }
            >
              <AppLayout />
            </Suspense>
          ) : (
            <Navigate
              to="/login"
              replace
              state={{ from: location.pathname + location.search }}
            />
          )
        }
      >
        <Route
          index
          element={
            <Suspense fallback={<LoadingState />}>
              <Dashboard />
            </Suspense>
          }
        />
        <Route
          path="/clientes/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <ClienteRoutes />
            </Suspense>
          }
        />
        <Route
          path="/proveedores/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <ProveedorRoutes />
            </Suspense>
          }
        />
        <Route
          path="/compras/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <CompraRoutes />
            </Suspense>
          }
        />
        <Route
          path="/insumos/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <InsumosRoutes />
            </Suspense>
          }
        />
        <Route
          path="/servicios/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <ServicioRoutes />
            </Suspense>
          }
        />
        <Route
          path="/prendas/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <PrendaRoutes />
            </Suspense>
          }
        />
        <Route
          path="/pedidos/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <PedidoRoutes />
            </Suspense>
          }
        />
        {user?.rol === "EMPLEADO" && (
          <Route
            path="/caja/historial"
            element={
              <MessagePage
                title="Acceso restringido"
                message="No tenés permiso para acceder"
                description="El historial de cajas requiere el rol de administrador."
              />
            }
          />
        )}
        <Route
          path="/caja/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <CajaRoutes />
            </Suspense>
          }
        />
        <Route
          path="/cobros/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <CobroRoutes />
            </Suspense>
          }
        />
        <Route
          path="/pagos-proveedor/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <PagoProveedorRoutes />
            </Suspense>
          }
        />
        <Route
          path="/comprobantes/*"
          element={
            <Suspense fallback={<LoadingState />}>
              <ComprobanteRoutes />
            </Suspense>
          }
        />
        <Route
          path="/reportes/*"
          element={
            user?.rol === "ADMINISTRADOR" ? (
              <Suspense fallback={<LoadingState />}>
                <ReportesRoutes />
              </Suspense>
            ) : (
              <MessagePage
                title="Acceso restringido"
                message="No tenés permiso para acceder"
                description="Los reportes administrativos requieren el rol ADMINISTRADOR."
              />
            )
          }
        />
        {navigation
          .filter((item) => item.pending)
          .map((item) => (
            <Route
              key={item.path}
              path={item.path}
              element={
                user && canAccess(item, user.rol) ? (
                  <MessagePage
                    title={item.label}
                    message="Módulo pendiente de implementar"
                    description="Este módulo estará disponible en una próxima etapa."
                  />
                ) : (
                  <MessagePage
                    title="Acceso restringido"
                    message="No tenés permiso para acceder"
                    description="Tu rol actual no tiene acceso a este módulo."
                  />
                )
              }
            />
          ))}
        <Route
          path="*"
          element={
            <MessagePage
              title="Página no encontrada"
              message="No encontramos esta página"
              description="Elegí un módulo desde la navegación."
            />
          }
        />
      </Route>
    </Routes>
  );
}

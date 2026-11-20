import { Outlet, useLocation } from "react-router";
import { useEffect, useRef } from "react";
import { useSession } from "@/features/auth/session";
import { navigation } from "@/app/router/navigation";
import { Header } from "./Header";

export function AppLayout() {
  const { user } = useSession();
  const { pathname } = useLocation();
  const main = useRef<HTMLElement>(null);
  const section = "/" + pathname.toLowerCase().split("/")[1];
  const current =
    navigation.find((item) => item.path === section)?.label ?? "Página";
  useEffect(() => {
    document.title = current + " | Mira que te lavo";
    main.current?.focus();
  }, [current, pathname]);
  if (!user) return null;

  return (
    <div className="app-layout">
      <a href="#contenido" className="skip-link">
        Ir al contenido
      </a>
      <Header current={current} user={user} />
      <main id="contenido" tabIndex={-1} ref={main} className="app-main">
        <Outlet />
      </main>
    </div>
  );
}

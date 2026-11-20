import { Dialog } from "radix-ui";
import { Menu, X, ChevronRight, House } from "lucide-react";
import { Link } from "react-router";
import { useState } from "react";
import type { User } from "@/features/auth/contracts";
import { Brand } from "@/shared/components/Brand";
import { Sidebar } from "./Sidebar";
import { UserMenu } from "./UserMenu";

export function Breadcrumbs({ current }: { current: string }) {
  return (
    <nav aria-label="Ruta de navegación">
      <ol className="breadcrumbs">
        <li aria-hidden="true">
          <House size={15} />
        </li>
        {current !== "Inicio" && (
          <li>
            <Link to="/">Inicio</Link>
            <ChevronRight size={14} aria-hidden="true" />
          </li>
        )}
        <li aria-current="page">{current}</li>
      </ol>
    </nav>
  );
}

export function Header({ current, user }: { current: string; user: User }) {
  const [open, setOpen] = useState(false);
  return (
    <header className="app-header">
      <div className="header-brand-band">
        <div className="layout-width header-brand-row">
          <Link to="/" className="header-home">
            <Brand showSubtitle />
          </Link>
          <Dialog.Root open={open} onOpenChange={setOpen}>
            <Dialog.Trigger className="navigation-toggle">
              <Menu size={18} aria-hidden="true" /> Panel inicial
            </Dialog.Trigger>
            <Dialog.Portal>
              <Dialog.Overlay className="overlay" />
              <Dialog.Content
                className="navigation-drawer"
                aria-describedby={undefined}
              >
                <div className="navigation-drawer-heading">
                  <Dialog.Title className="min-w-0">Panel inicial</Dialog.Title>
                  <Dialog.Close
                    className="navigation-close"
                    aria-label="Cerrar navegación"
                  >
                    <X size={20} aria-hidden="true" />
                  </Dialog.Close>
                </div>
                <Sidebar role={user.rol} onNavigate={() => setOpen(false)} />
              </Dialog.Content>
            </Dialog.Portal>
          </Dialog.Root>
          <UserMenu user={user} />
        </div>
      </div>
      {current !== "Inicio" && (
        <div className="header-navigation">
          <div className="layout-width header-navigation-row">
            <Breadcrumbs current={current} />
          </div>
        </div>
      )}
    </header>
  );
}

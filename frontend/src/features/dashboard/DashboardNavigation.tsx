import { CircleHelp, Search, X } from "lucide-react";
import { Dialog } from "radix-ui";
import { useEffect, useId, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import {
  canAccess,
  dashboardActions,
  moduleDestinations,
} from "@/app/router/navigation";
import type { Role } from "@/features/auth/contracts";
import { Button } from "@/shared/components/ui/button";
import { Input } from "@/shared/components/ui/input";

const keyClass = "rounded border border-current/30 px-1.5 text-xs font-medium";
const storageKey = "lavanderia.dashboard.atajos";
const normalize = (value: string) =>
  value.toLocaleLowerCase("es").normalize("NFD").replace(/\p{M}/gu, "");

export function DashboardNavigation({ role }: { role: Role }) {
  const [enabled, setEnabled] = useState(() => {
    try {
      return sessionStorage.getItem(storageKey) !== "off";
    } catch {
      return true;
    }
  });
  const [dialog, setDialog] = useState<"search" | "help">();
  const [query, setQuery] = useState("");
  const [active, setActive] = useState(0);
  const search = useRef<HTMLInputElement>(null);
  const searchButton = useRef<HTMLButtonElement>(null);
  const helpButton = useRef<HTMLButtonElement>(null);
  const returnFocus = useRef<HTMLElement | null>(null);
  const resultsId = useId();
  const navigate = useNavigate();
  const actions = dashboardActions.filter((item) => canAccess(item, role));
  const results = moduleDestinations.filter(
    (item) =>
      canAccess(item, role) &&
      normalize(item.label).includes(normalize(query.trim())),
  );

  useEffect(() => {
    if (!enabled) return;
    function shortcut(event: KeyboardEvent) {
      if (
        dialog ||
        event.defaultPrevented ||
        event.repeat ||
        event.isComposing ||
        event.ctrlKey ||
        event.metaKey ||
        event.altKey ||
        (event.shiftKey && event.key !== "?" && event.key !== "/") ||
        (event.target instanceof HTMLElement &&
          (event.target.isContentEditable ||
            event.target.closest(
              "input, textarea, select, [role=textbox], [role=combobox]",
            ))) ||
        document.querySelector(
          "[role=dialog][data-state=open], [role=alertdialog][data-state=open], dialog[open], [role=menu][data-state=open]",
        )
      )
        return;
      if (event.key === "/" || event.key === "?") {
        event.preventDefault();
        const help = event.key === "?";
        returnFocus.current = help ? helpButton.current : searchButton.current;
        setQuery("");
        setActive(0);
        setDialog(help ? "help" : "search");
        return;
      }
      const action = dashboardActions.find(
        (item) => item.key === event.key.toUpperCase() && canAccess(item, role),
      );
      if (action) {
        event.preventDefault();
        void navigate(action.path);
      }
    }
    window.addEventListener("keydown", shortcut);
    return () => window.removeEventListener("keydown", shortcut);
  }, [dialog, enabled, navigate, role]);

  function choose(path: string) {
    returnFocus.current =
      path === "/" ? document.getElementById("contenido") : null;
    setDialog(undefined);
    if (path !== "/") void navigate(path);
  }

  return (
    <section className="dashboard-section" aria-labelledby="actions-title">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <h2 id="actions-title" className="text-lg font-bold">
          Acceso rápido
        </h2>
        <div className="flex flex-wrap gap-2">
          <Button
            ref={searchButton}
            variant="outline"
            aria-haspopup="dialog"
            onClick={() => {
              returnFocus.current = searchButton.current;
              setQuery("");
              setActive(0);
              setDialog("search");
            }}
          >
            <Search aria-hidden="true" /> Buscar módulos
            {enabled && (
              <kbd
                aria-hidden="true"
                className={keyClass + " hidden sm:inline"}
              >
                /
              </kbd>
            )}
          </Button>
          <Button
            ref={helpButton}
            variant="ghost"
            aria-haspopup="dialog"
            onClick={() => {
              returnFocus.current = helpButton.current;
              setDialog("help");
            }}
          >
            <CircleHelp aria-hidden="true" /> Atajos
          </Button>
        </div>
      </div>
      <ul className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-4">
        {actions.map(({ path, label, key, icon: Icon }, index) => (
          <li key={path}>
            <Button
              asChild
              variant={index === 0 ? "default" : "outline"}
              className="h-auto min-h-12 w-full justify-start px-3 py-2"
            >
              <Link to={path}>
                <Icon aria-hidden="true" />{" "}
                <span className="flex-1">{label}</span>
                {enabled && (
                  <kbd
                    aria-hidden="true"
                    className={keyClass + " hidden sm:inline"}
                  >
                    {key}
                  </kbd>
                )}
              </Link>
            </Button>
          </li>
        ))}
      </ul>
      <Dialog.Root
        open={Boolean(dialog)}
        onOpenChange={(open) => {
          if (!open) setDialog(undefined);
        }}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="overlay" />
          <Dialog.Content
            className="surface fixed top-1/2 left-1/2 z-50 grid max-h-[85dvh] w-[calc(100%-2rem)] max-w-lg -translate-1/2 gap-3 overflow-y-auto p-4 shadow-xl sm:p-5"
            onOpenAutoFocus={(event) => {
              if (dialog === "search") {
                event.preventDefault();
                search.current?.focus();
              }
            }}
            onCloseAutoFocus={(event) => {
              event.preventDefault();
              returnFocus.current?.focus();
            }}
          >
            <div className="flex items-center justify-between gap-2">
              <Dialog.Title className="text-lg font-bold">
                {dialog === "help" ? "Atajos del inicio" : "Buscar módulos"}
              </Dialog.Title>
              <Dialog.Close asChild>
                <Button variant="ghost" aria-label="Cerrar">
                  <X aria-hidden="true" />
                </Button>
              </Dialog.Close>
            </div>
            {dialog === "help" ? (
              <>
                <Dialog.Description className="text-sm text-muted-foreground">
                  Disponibles desde Inicio cuando no estás escribiendo y no hay
                  un diálogo abierto.
                </Dialog.Description>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={enabled}
                    onChange={(event) => {
                      const activo = event.target.checked;
                      setEnabled(activo);
                      try {
                        sessionStorage.setItem(
                          storageKey,
                          activo ? "on" : "off",
                        );
                      } catch {
                        // La preferencia sigue activa mientras el panel esté montado.
                      }
                    }}
                  />
                  Activar atajos de una tecla
                </label>
                <dl className="grid grid-cols-[1fr_auto] gap-2 text-sm">
                  {[
                    ...actions,
                    { label: "Buscar módulos", key: "/" },
                    { label: "Mostrar ayuda", key: "?" },
                    { label: "Cerrar", key: "Escape" },
                  ].map(({ label, key }) => (
                    <div key={key} className="contents">
                      <dt>{label}</dt>
                      <dd>
                        <kbd className={keyClass}>{key}</kbd>
                      </dd>
                    </div>
                  ))}
                </dl>
              </>
            ) : (
              <>
                <Dialog.Description className="text-sm text-muted-foreground">
                  Escribí un nombre o usá las flechas y Enter.
                </Dialog.Description>
                <Input
                  ref={search}
                  role="combobox"
                  aria-label="Nombre del módulo"
                  aria-autocomplete="list"
                  aria-expanded="true"
                  aria-controls={resultsId}
                  aria-activedescendant={
                    results[active] ? `${resultsId}-${active}` : undefined
                  }
                  placeholder="Buscar por nombre"
                  value={query}
                  onChange={(event) => {
                    setQuery(event.target.value);
                    setActive(0);
                  }}
                  onKeyDown={(event) => {
                    if (event.nativeEvent.isComposing || !results.length)
                      return;
                    if (event.key === "Enter") {
                      event.preventDefault();
                      choose(results[active].path);
                    } else if (
                      event.key === "ArrowDown" ||
                      event.key === "ArrowUp"
                    ) {
                      event.preventDefault();
                      const next =
                        (active +
                          (event.key === "ArrowDown" ? 1 : -1) +
                          results.length) %
                        results.length;
                      setActive(next);
                      document
                        .getElementById(`${resultsId}-${next}`)
                        ?.scrollIntoView({ block: "nearest" });
                    }
                  }}
                />
                <ul
                  id={resultsId}
                  role="listbox"
                  aria-label="Módulos disponibles"
                  className="max-h-[42dvh] overflow-y-auto"
                >
                  {results.map(({ path, label, icon: Icon }, index) => (
                    <li
                      key={path}
                      id={`${resultsId}-${index}`}
                      role="option"
                      aria-selected={index === active}
                      className="flex min-h-11 cursor-pointer items-center gap-3 rounded-md px-3 text-sm hover:bg-muted aria-selected:bg-brand-mist aria-selected:text-primary"
                      onClick={() => choose(path)}
                    >
                      <Icon size={17} aria-hidden="true" /> {label}
                    </li>
                  ))}
                </ul>
                {!results.length && (
                  <p role="status" className="text-sm text-muted-foreground">
                    No encontramos módulos.
                  </p>
                )}
              </>
            )}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </section>
  );
}

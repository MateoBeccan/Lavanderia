import "@testing-library/jest-dom/vitest";
import { cleanup, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router";
import { AppProviders } from "@/app/providers/AppProviders";
import { AppRouter } from "@/app/router/AppRouter";

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

function renderApp(path = "/") {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AppProviders>
        <AppRouter />
      </AppProviders>
    </MemoryRouter>,
  );
}

it("redirige una ruta protegida y valida campos requeridos", async () => {
  const input = userEvent.setup();
  renderApp("/pedidos");
  expect(
    await screen.findByRole("heading", { name: "Bienvenido de nuevo" }),
  ).toBeInTheDocument();
  await input.click(screen.getByRole("button", { name: "Iniciar sesión" }));
  expect(await screen.findByText("Ingresá tu usuario.")).toBeInTheDocument();
  expect(screen.getByLabelText("Contraseña")).toHaveAttribute(
    "aria-invalid",
    "true",
  );
});

it("muestra carga y luego el error de credenciales", async () => {
  const input = userEvent.setup();
  let respond!: (response: Response) => void;
  const fetcher = vi.fn(
    () =>
      new Promise<Response>((resolve) => {
        respond = resolve;
      }),
  );
  vi.stubGlobal("fetch", fetcher);
  renderApp("/login");
  await input.type(screen.getByLabelText("Usuario"), "cuenta.prueba");
  await input.type(screen.getByLabelText("Contraseña"), "clave-sintetica");
  await input.click(screen.getByRole("button", { name: "Iniciar sesión" }));
  expect(
    await screen.findByRole("button", { name: "Ingresando…" }),
  ).toBeDisabled();
  respond(
    Response.json(
      { detail: "Credenciales o token inválidos." },
      { status: 401 },
    ),
  );
  expect(await screen.findByRole("alert")).toHaveTextContent(
    "El usuario o la contraseña son incorrectos.",
  );
});

it("permite mostrar y volver a ocultar la contraseña con el teclado", async () => {
  const input = userEvent.setup();
  renderApp("/login");
  const password = await screen.findByLabelText("Contraseña");
  await input.type(password, "clave-sintetica");
  await input.tab();
  expect(
    screen.getByRole("button", { name: "Mostrar contraseña" }),
  ).toHaveFocus();
  await input.keyboard("{Enter}");
  expect(password).toHaveAttribute("type", "text");
  await input.keyboard(" ");
  expect(password).toHaveAttribute("type", "password");
  expect(password).toHaveValue("clave-sintetica");
});

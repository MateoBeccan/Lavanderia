import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, it, vi } from "vitest";
import { SelectorCatalogo } from "./SelectorCatalogo";

it("conserva el ID actual fuera de página y entrega un ID numérico", async () => {
  const change = vi.fn();
  render(
    <SelectorCatalogo
      aria-label="Prenda"
      value={42}
      onChange={change}
      opciones={[
        { id: 9, nombre: "Camisa", activo: true },
        { id: 10, nombre: "Pantalón", activo: false },
      ]}
    />,
  );
  const select = screen.getByRole<HTMLSelectElement>("combobox", {
    name: "Prenda",
  });
  expect(select.value).toBe("42");
  expect(screen.queryByRole("option", { name: "Pantalón" })).toBeNull();
  await userEvent.selectOptions(select, "9");
  expect(change).toHaveBeenCalledWith(9);
});

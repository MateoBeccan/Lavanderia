export const catalogo = {
  insumo: { path: "/insumos", titulo: "Insumos", nuevo: "Nuevo insumo" },
  categoría: {
    path: "/insumos/categorias",
    titulo: "Categorías de insumos",
    nuevo: "Nueva categoría",
  },
} as const;

export const tamaniosInsumos = [10, 20, 50];
const nombres = new Intl.Collator("es-AR", { sensitivity: "base" });

export function porNombre<T extends { nombre: string; id: number }>(
  a: T,
  b: T,
) {
  return nombres.compare(a.nombre, b.nombre) || a.id - b.id;
}

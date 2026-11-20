export function crearRegistroFinanciero<Estado>(
  nombre: string,
  inicial: Estado,
  recuperar: (id: string) => Estado | undefined,
  pendiente: (estado: Estado) => string | undefined,
) {
  const operaciones = new Map<number, Estado>();
  const listeners = new Set<() => void>();
  const marcador = (usuario: number) =>
    `lavanderia.${nombre}.pendiente.v1.${usuario}`;

  return {
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    leer(usuario: number) {
      const operacion = operaciones.get(usuario);
      if (operacion !== undefined) return operacion;
      try {
        const id = sessionStorage.getItem(marcador(usuario));
        const recuperada = id ? recuperar(id) : undefined;
        if (recuperada !== undefined) {
          operaciones.set(usuario, recuperada);
          return recuperada;
        }
      } catch {
        // El envío puede continuar en memoria si el navegador bloquea el almacenamiento.
      }
      return inicial;
    },
    actualizar(usuario: number, estado: Estado) {
      operaciones.set(usuario, estado);
      try {
        const id = pendiente(estado);
        if (id) sessionStorage.setItem(marcador(usuario), id);
        else sessionStorage.removeItem(marcador(usuario));
      } catch {
        // El UUID y el cuerpo del intento permanecen en memoria.
      }
      for (const listener of listeners) listener();
    },
  };
}

export function congelarEnvio<Cuerpo extends object>(body: Cuerpo) {
  return { clave: crypto.randomUUID(), body: Object.freeze({ ...body }) };
}

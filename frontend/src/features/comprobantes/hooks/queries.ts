import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { useSession } from "@/features/auth/session";
import { comprobantesApi } from "../api/comprobantes";
import type {
  PadreComprobantes,
  ReferenciaComprobante,
  SolicitudComprobante,
} from "../types/comprobantes";

const listadoKey = (padre: PadreComprobantes, id: string) =>
  ["comprobantes", "lista", padre, id] as const;
const detalleKey = (id: string) => ["comprobantes", "detalle", id] as const;
const operacionKey = ({ tipo, id }: ReferenciaComprobante) =>
  ["comprobantes", "operacion", tipo, id] as const;

export function useComprobantes(
  padre: PadreComprobantes,
  id: string,
  page: number,
) {
  const { api } = useSession();
  return useQuery({
    queryKey: [...listadoKey(padre, id), page],
    queryFn: ({ signal }) =>
      comprobantesApi.listar(api, padre, id, page, signal),
  });
}

export function useComprobante(id: string) {
  const { api } = useSession();
  return useQuery({
    queryKey: detalleKey(id),
    queryFn: ({ signal }) => comprobantesApi.detalle(api, id, signal),
  });
}

export function useComprobanteDeOperacion(operacion: ReferenciaComprobante) {
  const { api } = useSession();
  return useQuery({
    queryKey: operacionKey(operacion),
    queryFn: ({ signal }) =>
      comprobantesApi.deOperacion(api, operacion, signal),
    staleTime: 0,
  });
}

export function useRegistrarComprobante(operacion: ReferenciaComprobante) {
  const { api } = useSession();
  const cache = useQueryClient();
  const padre = operacion.tipo === "COBRO" ? "pedidos" : "compras";
  return useMutation({
    mutationFn: (body: SolicitudComprobante) =>
      comprobantesApi.registrar(api, operacion, body),
    retry: false,
    networkMode: "always",
    onSuccess: async (comprobante) => {
      const key = operacionKey(operacion);
      await cache.cancelQueries({ queryKey: key });
      cache.setQueryData(key, comprobante);
      cache.setQueryData(detalleKey(String(comprobante.id)), comprobante);
      toast.success("Comprobante registrado.");
    },
    onSettled: () =>
      cache.invalidateQueries({
        queryKey: listadoKey(padre, String(operacion.padreId)),
      }),
  });
}

package com.miraquetelavo.lavanderia.reporte.repository;

import com.miraquetelavo.lavanderia.reporte.dto.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.EGRESO;
import static com.miraquetelavo.lavanderia.movimientocaja.entity.NaturalezaMovimiento.INGRESO;

@Repository
@RequiredArgsConstructor
public class ReporteRepository {

    private final EntityManager em;

    public void limitarTiempoExportacion() {
        em.createNativeQuery("select set_config('statement_timeout', '5000', true)").getSingleResult();
    }

    public ReportePedidosResponse pedidos(LocalDateTime desde, LocalDateTime hasta, Long clienteId,
                                          String estadoPedido, String estadoPago, Pageable pagina) {
        var filtro = new Consulta("""
                from Pedido p
                join p.cliente c
                join p.estadoPedido ep
                left join p.estadoPago pago
                """, "p.fechaIngreso", desde, hasta);
        filtro.agregar("c.id = :cliente", "cliente", clienteId);
        filtro.agregar("ep.codigo = :estadoPedido", "estadoPedido", estadoPedido);
        filtro.agregar("pago.codigo = :estadoPago", "estadoPago", estadoPago);
        var agregado = filtro.query("select count(p), coalesce(sum(p.total), 0)", Tuple.class).getSingleResult();
        long cantidad = agregado.get(0, Long.class);
        var resumen = new ResumenPedidosResponse(cantidad, importe(agregado, 1));
        var datos = filtro.pagina("""
                p.id, p.fechaIngreso, p.fechaEntregaEstimada, c.id, concat(c.nombre, ' ', c.apellido),
                ep.codigo, ep.nombre, pago.codigo, pago.nombre, p.total
                """, "p.fechaIngreso desc, p.id desc", ReportePedidoItemResponse.class, pagina, cantidad);
        return new ReportePedidosResponse(datos, resumen, pagina.getPageNumber(), pagina.getPageSize(),
                cantidad, totalPaginas(cantidad, pagina));
    }

    public ReporteMovimientosResponse movimientos(LocalDateTime desde, LocalDateTime hasta, String tipoCodigo,
                                                    String metodoCodigo, Pageable pagina) {
        var filtro = new Consulta("""
                from MovimientoCaja m
                join m.tipoMovimiento tipo
                join m.metodoPago metodo
                join m.usuario u
                """, "m.fechaHora", desde, hasta);
        filtro.agregar("tipo.codigo = :tipo", "tipo", tipoCodigo);
        filtro.agregar("metodo.codigo = :metodo", "metodo", metodoCodigo);
        var agregado = filtro.query("""
                select count(m),
                       coalesce(sum(m.monto) filter (where tipo.codigo in :ingresos), 0),
                       coalesce(sum(m.monto) filter (where tipo.codigo in :egresos), 0)
                """, Tuple.class)
                .setParameter("ingresos", INGRESO.codigos())
                .setParameter("egresos", EGRESO.codigos())
                .getSingleResult();
        long cantidad = agregado.get(0, Long.class);
        var ingresos = importe(agregado, 1);
        var egresos = importe(agregado, 2);
        var resumen = new ResumenMovimientosResponse(cantidad, ingresos, egresos, ingresos.subtract(egresos));
        var datos = filtro.pagina("""
                m.id, m.caja.id, m.fechaHora, tipo.codigo, tipo.nombre, metodo.codigo, metodo.nombre,
                u.id, u.nombre, m.descripcion, m.monto
                """, "m.fechaHora desc, m.id desc", ReporteMovimientoItemResponse.class, pagina, cantidad);
        return new ReporteMovimientosResponse(datos, resumen, pagina.getPageNumber(), pagina.getPageSize(),
                cantidad, totalPaginas(cantidad, pagina));
    }

    public ReporteComprasResponse compras(LocalDateTime desde, LocalDateTime hasta, Long proveedorId,
                                          String estadoPago, Pageable pagina) {
        var filtro = new Consulta("""
                from Compra c
                join c.proveedor p
                join c.estadoPago pago
                """, "c.fechaCompra", desde, hasta);
        filtro.agregar("p.id = :proveedor", "proveedor", proveedorId);
        filtro.agregar("pago.codigo = :estadoPago", "estadoPago", estadoPago);
        var agregado = filtro.query("select count(c), coalesce(sum(c.totalCompra), 0)", Tuple.class).getSingleResult();
        long cantidad = agregado.get(0, Long.class);
        var resumen = new ResumenComprasResponse(cantidad, importe(agregado, 1));
        var datos = filtro.pagina("""
                c.id, c.fechaCompra, p.id, p.nombre, c.numeroComprobante, pago.codigo, pago.nombre, c.totalCompra
                """, "c.fechaCompra desc, c.id desc", ReporteCompraItemResponse.class, pagina, cantidad);
        return new ReporteComprasResponse(datos, resumen, pagina.getPageNumber(), pagina.getPageSize(),
                cantidad, totalPaginas(cantidad, pagina));
    }

    private static BigDecimal importe(Tuple fila, int indice) {
        return fila.get(indice, BigDecimal.class).setScale(2);
    }

    private static long totalPaginas(long cantidad, Pageable pagina) {
        return Math.ceilDiv(cantidad, pagina.getPageSize());
    }

    private final class Consulta {
        private final StringBuilder hql;
        private final Map<String, Object> parametros = new LinkedHashMap<>();

        private Consulta(String origen, String fecha, LocalDateTime desde, LocalDateTime hasta) {
            hql = new StringBuilder(origen).append(" where 1 = 1");
            agregar(fecha + " >= :desde", "desde", desde);
            agregar(fecha + " < :hasta", "hasta", hasta);
        }

        private void agregar(String condicion, String parametro, Object valor) {
            if (valor != null) {
                hql.append(" and ").append(condicion);
                parametros.put(parametro, valor);
            }
        }

        private <T> TypedQuery<T> query(String seleccion, Class<T> tipo) {
            return vincular(em.createQuery(seleccion + " " + hql, tipo));
        }

        private <T> TypedQuery<T> vincular(TypedQuery<T> consulta) {
            parametros.forEach(consulta::setParameter);
            return consulta;
        }

        private <T> List<T> pagina(String campos, String orden, Class<T> tipo, Pageable pagina, long cantidad) {
            if (pagina.getOffset() >= cantidad) {
                return List.of();
            }
            var consulta = vincular(em.createQuery("select new " + tipo.getName() + "(" + campos + ") "
                    + hql + " order by " + orden + " offset :offset rows fetch next :limite rows only", tipo));
            return consulta.setParameter("offset", pagina.getOffset())
                    .setParameter("limite", pagina.getPageSize()).getResultList();
        }
    }
}

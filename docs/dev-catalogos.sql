-- pgAdmin Query Tool. Solo en lavanderia_dev después de restaurar la estructura.
BEGIN;
SET LOCAL search_path = pg_catalog;
DO $$
BEGIN
    IF current_database() <> 'lavanderia_dev' OR inet_server_port() IS DISTINCT FROM 5432
       OR inet_server_addr() IS DISTINCT FROM '127.0.0.1'::inet
       OR session_user = 'lavanderia_dev_app' OR current_user = 'lavanderia_dev_app'
       OR NOT (SELECT rolsuper FROM pg_roles WHERE rolname = current_user)
       OR to_regclass('public.solicitudes_financieras_idempotentes') IS NULL
       OR to_regclass('public.ux_cajas_unica_abierta') IS NULL THEN
        RAISE EXCEPTION 'Verificar conexión administrativa a lavanderia_dev y estructura con V032 e índice de Caja';
    END IF;
END
$$;

INSERT INTO public.roles (nombre_rol, descripcion)
VALUES ('ADMINISTRADOR', 'Acceso a administración y gestión integral'),
       ('EMPLEADO', 'Acceso a operaciones diarias autorizadas')
ON CONFLICT (nombre_rol) DO NOTHING;

INSERT INTO public.estados_pedido (codigo, nombre, descripcion, orden)
VALUES ('PENDIENTE', 'Pendiente', 'Pedido registrado, pendiente de iniciar su procesamiento.', 1),
       ('EN_PROCESO', 'En proceso', 'Pedido que se encuentra siendo procesado.', 2),
       ('LISTO', 'Listo para entregar', 'Pedido finalizado y disponible para su entrega.', 3),
       ('ENTREGADO', 'Entregado', 'Pedido entregado al cliente.', 4),
       ('CANCELADO', 'Cancelado', 'Pedido cuya operación fue cancelada.', 5)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO public.estados_pago (codigo, nombre, descripcion, orden)
VALUES ('PENDIENTE', 'Pendiente', 'El importe total está pendiente.', 1),
       ('PARCIAL', 'Parcialmente pagado', 'Se ha pagado una parte del importe total.', 2),
       ('PAGADO', 'Pagado', 'El importe total está pagado.', 3)
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO public.metodos_pago (codigo, nombre)
VALUES ('EFECTIVO', 'Efectivo'), ('TRANSFERENCIA', 'Transferencia bancaria'),
       ('TARJETA', 'Tarjeta'), ('OTROS', 'Otros')
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO public.tipos_movimiento_caja (codigo, nombre, descripcion, orden)
VALUES ('INGRESO_MANUAL', 'Ingreso manual', 'Ingreso de dinero registrado manualmente.', 1),
       ('EGRESO_MANUAL', 'Egreso manual', 'Salida de dinero registrada manualmente.', 2),
       ('COBRO_PEDIDO', 'Cobro pedido', 'Ingreso asociado al cobro de un pedido.', 3),
       ('PAGO_PROVEEDOR', 'Pago proveedor', 'Egreso asociado al pago de una compra a proveedor.', 4)
ON CONFLICT (codigo) DO NOTHING;

COMMIT;

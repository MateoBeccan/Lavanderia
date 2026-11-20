CREATE TABLE solicitudes_financieras_idempotentes (
    usuario_id bigint NOT NULL,
    clave uuid NOT NULL,
    operacion varchar(32) NOT NULL,
    huella varchar(64) NOT NULL,
    ubicacion text NOT NULL,
    cuerpo text NOT NULL,
    creado_en timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_solicitudes_financieras_idempotentes PRIMARY KEY (usuario_id, clave),
    CONSTRAINT ck_solicitud_financiera_huella CHECK (huella ~ '^[a-f0-9]{64}$')
);

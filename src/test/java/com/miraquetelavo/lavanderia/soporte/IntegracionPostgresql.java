package com.miraquetelavo.lavanderia.soporte;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextCustomizerFactories;
import org.springframework.test.context.TestExecutionListeners;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@EnabledIfEnvironmentVariable(named = "LAVANDERIA_TEST_JDBC_URL", matches = ".+")
@Execution(ExecutionMode.SAME_THREAD)
@ActiveProfiles("test-postgresql")
@ContextCustomizerFactories(EsquemaPostgresql.Configuracion.class)
@TestExecutionListeners(
        listeners = EsquemaPostgresql.Limpieza.class,
        mergeMode = TestExecutionListeners.MergeMode.MERGE_WITH_DEFAULTS)
public @interface IntegracionPostgresql {
    String value();
}

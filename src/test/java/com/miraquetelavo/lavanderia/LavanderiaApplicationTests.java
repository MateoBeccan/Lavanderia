package com.miraquetelavo.lavanderia;

import com.miraquetelavo.lavanderia.cliente.dto.ClienteRequest;
import com.miraquetelavo.lavanderia.cliente.entity.Cliente;
import com.miraquetelavo.lavanderia.cliente.service.ClienteService;
import com.miraquetelavo.lavanderia.servicio.dto.ServicioRequest;
import com.miraquetelavo.lavanderia.servicio.entity.Servicio;
import com.miraquetelavo.lavanderia.servicio.service.ServicioService;
import jakarta.persistence.EntityManagerFactory;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.StringWriter;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "lavanderia.jwt.secret=${random.value}${random.value}",
		"spring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false",
		"spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect"
})
class LavanderiaApplicationTests {

	private static final StringWriter DDL = new StringWriter();

	@TestConfiguration(proxyBeanMethods = false)
	static class ConfiguracionDdl {

		@Bean
		HibernatePropertiesCustomizer generarDdlSinEjecutarlo() {
			return propiedades -> {
				propiedades.put("jakarta.persistence.schema-generation.scripts.action", "create");
				propiedades.put("jakarta.persistence.schema-generation.scripts.create-target", DDL);
			};
		}
	}

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private ClienteService clienteService;

	@Autowired
	private ServicioService servicioService;

	@MockitoBean(name = "transactionManager")
	private PlatformTransactionManager transactionManager;

	@MockitoBean
	private DataSource dataSource;

	@Test
	void reconoceClienteEnElMetamodeloJpa() {
		var entidad = entityManagerFactory.getMetamodel().entity(Cliente.class);

		assertThat(entidad.getIdType().getJavaType()).isEqualTo(Long.class);
		assertThat(entidad.getAttribute("activo").getJavaType()).isEqualTo(boolean.class);
		assertThat(entidad.getAttribute("dni").getJavaType()).isEqualTo(String.class);
	}

	@Test
	void generaRestriccionUniqueDeDniParaPostgresql() {
		assertThat(DDL.toString())
				.contains("dni varchar(8)")
				.containsPattern("constraint " + Cliente.UK_DNI + " unique\\s*\\(dni\\)");
	}

	@Test
	void generaColumnaNormalizadaYRestriccionUniqueParaPostgresql() {
		assertThat(DDL.toString())
				.contains("nombre_normalizado text generated always as (lower(nombre_servicio)) stored not null")
				.containsPattern("constraint " + Servicio.UK_NOMBRE + " unique\\s*\\(nombre_normalizado\\)")
				.contains("precio numeric(10,2)")
				.contains("activo boolean default true not null");
	}

	@Test
	void validaServicioAunqueNoPasePorHttp() {
		var solicitud = new ServicioRequest(" ", new BigDecimal("10.001"), null);

		assertThatThrownBy(() -> servicioService.registrar(solicitud))
				.isInstanceOfSatisfying(ConstraintViolationException.class, ex ->
						assertThat(ex.getConstraintViolations()).hasSize(3));
	}

	@Test
	void validaLasEntradasAlServicioAunqueNoPasenPorHttp() {
		var solicitud = new ClienteRequest(" ", "Pérez", "12.345.678", "1".repeat(31), "incorrecto");

		assertThatThrownBy(() -> clienteService.registrar(solicitud))
				.isInstanceOfSatisfying(ConstraintViolationException.class, ex -> {
					assertThat(ex.getConstraintViolations()).hasSize(4);
				});
	}

	@Test
	void validaDniObligatorioAlActualizarDesdeElServicio() {
		var solicitud = new ClienteRequest("Ana", "Pérez", null, null, null);

		assertThatThrownBy(() -> clienteService.actualizar(7L, solicitud))
				.isInstanceOfSatisfying(ConstraintViolationException.class, ex -> {
					assertThat(ex.getConstraintViolations()).singleElement()
							.satisfies(error -> assertThat(error.getMessage()).isEqualTo("El DNI es obligatorio."));
				});
	}

}

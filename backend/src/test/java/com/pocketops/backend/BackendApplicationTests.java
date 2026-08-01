package com.pocketops.backend;

import com.pocketops.backend.common.health.HealthResponse;
import com.pocketops.backend.common.health.HealthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:pocketops;MODE=MySQL;DB_CLOSE_DELAY=-1",
		"spring.datasource.driver-class-name=org.h2.Driver",
		"spring.datasource.username=sa",
		"spring.datasource.password=",
		"spring.flyway.enabled=false",
		"spring.jpa.hibernate.ddl-auto=create-drop"
})
class BackendApplicationTests {
	@Autowired
	private HealthService healthService;

	@Test
	void healthServiceReturnsUpStatus() {
		HealthResponse response = healthService.currentHealth();
		assertThat(response.status()).isEqualTo("UP");
		assertThat(response.database()).isEqualTo("UP");
	}
}

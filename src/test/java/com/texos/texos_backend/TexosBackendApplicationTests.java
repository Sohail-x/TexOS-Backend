package com.texos.texos_backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.FilterChainProxy;
import com.texos.texos_backend.service.JwtService;
import com.texos.texos_backend.observability.DashboardRequestMeasurements;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.springframework.http.MediaType;
import java.util.UUID;

@SpringBootTest
@AutoConfigureMockMvc
class TexosBackendApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JwtService jwtService;

	@Autowired
	private FilterChainProxy filterChainProxy;

	@PersistenceContext
	private EntityManager entityManager;

	@Value("${app.admin.username}")
	private String adminUsername;

	@Test
	void contextLoads() {
	}

	@Test
	void customerEndpointRequiresAuthentication() throws Exception {
		mockMvc.perform(get("/api/restaurants/1/customers"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void kitchenOrdersRequireKitchenStaffRole() throws Exception {
		mockMvc.perform(get("/api/kitchen-staff/orders"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/kitchen-staff/orders")
						.header("Authorization", "Bearer " + jwtService.generateAdminToken(adminUsername)))
				.andExpect(status().isForbidden());
	}

	@Test
	void kitchenCodeVerificationIsPublicAndRejectsUnknownCodes() throws Exception {
		mockMvc.perform(post("/api/kitchen-staff/verify")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"code\":\"123456\"}"))
				.andExpect(status().isNotFound())
				.andExpect(content().json("{\"message\":\"Invalid access code.\"}"));
	}

	@Test
	void actuatorMetricsRequireTexosAdminRole() throws Exception {
		mockMvc.perform(get("/actuator/metrics"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/actuator/metrics")
						.header("Authorization", "Bearer " + jwtService.generateAdminToken(adminUsername)))
				.andExpect(status().isOk());
	}

	@Test
	void hibernateInstrumentationCountsAndTimesReadQueriesInRequestContext() {
		DashboardRequestMeasurements.Measurement measurement =
				DashboardRequestMeasurements.begin(
						"/api/restaurants/{restaurantId}/dashboard/summary");
		try {
			entityManager.createNativeQuery("select 1").getSingleResult();
		} finally {
			DashboardRequestMeasurements.clear();
		}

		assertEquals(1, measurement.getQueryCount());
		assertTrue(measurement.getQueryExecutionNanos() > 0);
		assertTrue(measurement.getConnectionAcquisitionNanos() > 0);
	}

	@Test
	void hibernateInstrumentationCapturesQueriesForAuthEndpointContext() {
		DashboardRequestMeasurements.Measurement measurement =
				DashboardRequestMeasurements.begin("/api/auth/me");
		try {
			entityManager.createNativeQuery("select 1").getSingleResult();
		} finally {
			DashboardRequestMeasurements.clear();
		}

		assertEquals(1, measurement.getQueryCount());
		assertTrue(measurement.getQueryExecutionNanos() > 0);
		assertTrue(measurement.getConnectionAcquisitionNanos() > 0);
	}

	@Test
	void authenticationRequestsAreRateLimitedAcrossTheSharedDatabase() throws Exception {
		assertTrue(filterChainProxy.getFilters("/api/auth/login").stream()
				.anyMatch(com.texos.texos_backend.config.AuthRateLimitFilter.class::isInstance));
		String clientAddress = "rate-limit-test-" + UUID.randomUUID();
		for (int attempt = 1; attempt <= 11; attempt++) {
			var result = mockMvc.perform(post("/api/auth/login")
							.with(request -> {
								request.setRemoteAddr(clientAddress);
								return request;
							})
							.contentType(MediaType.APPLICATION_JSON)
							.content("{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}"))
					.andReturn();
			if (attempt < 11) {
				org.junit.jupiter.api.Assertions.assertNotEquals(
						429, result.getResponse().getStatus());
			} else {
				org.junit.jupiter.api.Assertions.assertEquals(
						429, result.getResponse().getStatus());
				org.junit.jupiter.api.Assertions.assertEquals(
						"900", result.getResponse().getHeader("Retry-After"));
			}
		}
	}
}

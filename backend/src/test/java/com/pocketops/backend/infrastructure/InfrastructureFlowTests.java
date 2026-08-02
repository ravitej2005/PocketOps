package com.pocketops.backend.infrastructure;

import com.pocketops.backend.auth.JsonTestSupport;
import com.pocketops.backend.websocket.InfrastructureUpdatesWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pocketops-infra;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "pocketops.auth.jwt.secret=test-secret-that-is-long-enough-for-hs256"
})
@AutoConfigureMockMvc
class InfrastructureFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InfrastructureUpdatesWebSocketHandler webSocketHandler;

    @Test
    void userCanCreateListAndDeleteOwnedInfrastructure() throws Exception {
        String accessToken = registerAndExtractAccessToken("infra-owner@example.com");

        String created = mockMvc.perform(post("/api/infrastructures")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "StormAPI Production",
                                  "type": "SELF_HOSTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("StormAPI Production"))
                .andExpect(jsonPath("$.type").value("SELF_HOSTED"))
                .andExpect(jsonPath("$.healthStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.capabilities[0]").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String infrastructureId = JsonTestSupport.extractString(created, "id");

        mockMvc.perform(get("/api/infrastructures")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(infrastructureId));

        mockMvc.perform(delete("/api/infrastructures/" + infrastructureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/infrastructures/" + infrastructureId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INFRASTRUCTURE_NOT_FOUND"));
    }

    @Test
    void crossUserInfrastructureAccessFails() throws Exception {
        String ownerToken = registerAndExtractAccessToken("owner@example.com");
        String otherToken = registerAndExtractAccessToken("other@example.com");

        String created = mockMvc.perform(post("/api/infrastructures")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Owner Only",
                                  "type": "MANAGED",
                                  "providerType": "demo"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String infrastructureId = JsonTestSupport.extractString(created, "id");

        mockMvc.perform(get("/api/infrastructures/" + infrastructureId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("INFRASTRUCTURE_NOT_FOUND"));

        mockMvc.perform(delete("/api/infrastructures/" + infrastructureId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        assertThatHandshake(ownerToken, infrastructureId, true);
        assertThatHandshake(otherToken, infrastructureId, false);
    }

    private void assertThatHandshake(String accessToken, String infrastructureId, boolean expected) {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest("GET", "/ws/infrastructures/" + infrastructureId);
        servletRequest.setQueryString("token=" + accessToken);
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        boolean accepted = webSocketHandler.beforeHandshake(
                new ServletServerHttpRequest(servletRequest),
                new ServletServerHttpResponse(servletResponse),
                new TextWebSocketHandler(),
                new HashMap<>()
        );
        org.assertj.core.api.Assertions.assertThat(accepted).isEqualTo(expected);
    }

    private String registerAndExtractAccessToken(String email) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "correct-horse-battery",
                                  "deviceName": "Pixel",
                                  "platform": "android"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonTestSupport.extractString(response, "accessToken");
    }
}

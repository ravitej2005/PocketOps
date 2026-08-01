package com.pocketops.backend.auth;

import com.pocketops.backend.session.UserSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pocketops-auth;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "pocketops.auth.jwt.secret=test-secret-that-is-long-enough-for-hs256"
})
@AutoConfigureMockMvc
class AuthFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserSessionRepository userSessionRepository;

    @MockitoBean
    private GitHubOAuthClient gitHubOAuthClient;

    @Test
    void registersLogsInRefreshesAndRejectsRotatedRefreshTokenReuse() throws Exception {
        String registerResponse = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "Dev@Example.com",
                                  "password": "correct-horse-battery",
                                  "deviceName": "Pixel",
                                  "platform": "android"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.email").value("dev@example.com"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String firstRefresh = JsonTestSupport.extractString(registerResponse, "refreshToken");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "dev@example.com",
                                  "password": "correct-horse-battery",
                                  "deviceName": "Browser",
                                  "platform": "web"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString());

        String refreshResponse = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String rotatedRefresh = JsonTestSupport.extractString(refreshResponse, "refreshToken");
        assertThat(rotatedRefresh).isNotEqualTo(firstRefresh);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + firstRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotatedRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listsAndRevokesSessions() throws Exception {
        String auth = register("sessions@example.com");
        String accessToken = JsonTestSupport.extractString(auth, "accessToken");

        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].current").value(true));

        String sessionId = userSessionRepository.findByUser_IdAndRevokedAtIsNullOrderByCreatedAtDesc(
                        JsonTestSupport.extractString(auth, "user.id"))
                .getFirst()
                .getId();

        mockMvc.perform(delete("/api/sessions/" + sessionId)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/sessions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logsInWithMockedGitHubOAuthProfile() throws Exception {
        when(gitHubOAuthClient.exchangeAuthorizationCode(any(), any()))
                .thenReturn(new GitHubUserProfile("12345", "octo@example.com"));

        mockMvc.perform(post("/api/auth/github")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "mock-code",
                                  "redirectUri": "pocketops://oauth/github",
                                  "deviceName": "Pixel",
                                  "platform": "android"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.email").value("octo@example.com"));
    }

    private String register(String email) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
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
    }
}

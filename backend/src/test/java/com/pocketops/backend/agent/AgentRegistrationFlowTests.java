package com.pocketops.backend.agent;

import com.pocketops.backend.auth.JsonTestSupport;
import com.pocketops.backend.infrastructure.HealthStatus;
import com.pocketops.backend.infrastructure.InfrastructureRepository;
import com.pocketops.backend.infrastructure.InfrastructureResourceRepository;
import com.pocketops.backend.proto.AgentControlGrpc;
import com.pocketops.backend.proto.AgentEnvelope;
import com.pocketops.backend.proto.Heartbeat;
import com.pocketops.backend.proto.InfrastructureSnapshot;
import com.pocketops.backend.proto.ResourceSnapshot;
import com.pocketops.backend.proto.ServerEnvelope;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pocketops-agent;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "pocketops.auth.jwt.secret=test-secret-that-is-long-enough-for-hs256",
        "pocketops.agent.grpc.port=19090",
        "pocketops.agent.heartbeat-timeout-seconds=1"
})
@AutoConfigureMockMvc
class AgentRegistrationFlowTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentRegistrationTokenRepository tokenRepository;

    @Autowired
    private AgentLifecycleService agentLifecycleService;

    @Autowired
    private AgentRegistrationService agentRegistrationService;

    @Autowired
    private InfrastructureRepository infrastructureRepository;

    @Autowired
    private InfrastructureResourceRepository infrastructureResourceRepository;

    @Test
    void registrationTokenIsSingleUseAndRevokedAgentCannotReconnect() throws Exception {
        RegisteredInfrastructure infrastructure = createSelfHostedInfrastructure("agent-owner@example.com");
        RegistrationCredential credential = createRegistrationCredential(infrastructure);

        String registrationResponse = registerAgent(credential.token());
        String agentId = JsonTestSupport.extractString(registrationResponse, "agentId");
        String identityToken = JsonTestSupport.extractString(registrationResponse, "identityToken");

        assertThat(agentRepository.findById(agentId).orElseThrow().getStatus()).isEqualTo(AgentStatus.ONLINE);

        mockMvc.perform(post("/api/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "registrationToken": "%s",
                                  "agentVersion": "test-agent"
                                }
                                """.formatted(credential.token())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));

        mockMvc.perform(post("/api/infrastructures/%s/agent/revoke".formatted(infrastructure.infrastructureId()))
                        .header("Authorization", "Bearer " + infrastructure.accessToken()))
                .andExpect(status().isOk());

        assertThat(agentRepository.findById(agentId).orElseThrow().getStatus()).isEqualTo(AgentStatus.REVOKED);
        assertThatThrownBy(() -> agentRegistrationService.authenticate(
                agentId,
                infrastructure.infrastructureId(),
                identityToken
        )).isInstanceOf(RuntimeException.class);
    }

    @Test
    void expiredRegistrationTokenIsRejected() throws Exception {
        RegisteredInfrastructure infrastructure = createSelfHostedInfrastructure("expiry-owner@example.com");
        RegistrationCredential credential = createRegistrationCredential(infrastructure);
        tokenRepository.findAll().forEach(token -> {
            token.setExpiresAt(Instant.now().minusSeconds(1));
            tokenRepository.save(token);
        });

        mockMvc.perform(post("/api/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "registrationToken": "%s",
                                  "agentVersion": "test-agent"
                                }
                                """.formatted(credential.token())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REGISTRATION_TOKEN_INVALID"));
    }

    @Test
    void grpcHeartbeatKeepsAgentOnlineAndTimeoutMarksUnknown() throws Exception {
        RegisteredInfrastructure infrastructure = createSelfHostedInfrastructure("heartbeat-owner@example.com");
        RegistrationCredential credential = createRegistrationCredential(infrastructure);
        String registrationResponse = registerAgent(credential.token());
        String agentId = JsonTestSupport.extractString(registrationResponse, "agentId");
        String identityToken = JsonTestSupport.extractString(registrationResponse, "identityToken");

        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 19090)
                .usePlaintext()
                .build();
        try {
            Metadata metadata = new Metadata();
            metadata.put(AgentGrpcService.IDENTITY_TOKEN_HEADER, identityToken);
            AgentControlGrpc.AgentControlStub stub = AgentControlGrpc.newStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
            CountDownLatch latch = new CountDownLatch(3);
            AtomicInteger ackCount = new AtomicInteger();
            StreamObserver<AgentEnvelope> requestObserver = stub.connect(new StreamObserver<>() {
                @Override
                public void onNext(ServerEnvelope value) {
                    ackCount.incrementAndGet();
                    latch.countDown();
                }

                @Override
                public void onError(Throwable t) {
                    latch.countDown();
                }

                @Override
                public void onCompleted() {
                }
            });

            requestObserver.onNext(AgentEnvelope.newBuilder()
                    .setAgentId(agentId)
                    .setInfrastructureId(infrastructure.infrastructureId())
                    .setMessageId("heartbeat-1")
                    .setTimestampUnixMs(Instant.now().toEpochMilli())
                    .setHeartbeat(Heartbeat.newBuilder().setAgentVersion("test-agent").build())
                    .build());
            requestObserver.onNext(AgentEnvelope.newBuilder()
                    .setAgentId(agentId)
                    .setInfrastructureId(infrastructure.infrastructureId())
                    .setMessageId("snapshot-1")
                    .setTimestampUnixMs(Instant.now().toEpochMilli())
                    .setInfrastructureSnapshot(InfrastructureSnapshot.newBuilder()
                            .addResources(ResourceSnapshot.newBuilder()
                                    .setExternalResourceId("container-1")
                                    .setDisplayName("stormapi")
                                    .setResourceType("CONTAINER")
                                    .setStatus("RUNNING")
                                    .setCriticality("NORMAL")
                                    .build())
                            .build())
                    .build());
            assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(ackCount.get()).isGreaterThanOrEqualTo(3);
            assertThat(agentRepository.findById(agentId).orElseThrow().getStatus()).isEqualTo(AgentStatus.ONLINE);
            var resource = infrastructureResourceRepository
                    .findByInfrastructure_IdAndExternalResourceId(infrastructure.infrastructureId(), "container-1")
                    .orElseThrow();
            assertThat(resource.getDisplayName()).isEqualTo("stormapi");
            assertThat(resource.getResourceType()).isEqualTo("CONTAINER");
            assertThat(resource.getStatus()).isEqualTo("RUNNING");
            assertThat(resource.getCriticality()).isEqualTo("NORMAL");
            assertThat(resource.getLastSeenAt()).isNotNull();
            mockMvc.perform(get("/api/infrastructures/%s/resources".formatted(infrastructure.infrastructureId()))
                            .header("Authorization", "Bearer " + infrastructure.accessToken()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].externalResourceId").value("container-1"))
                    .andExpect(jsonPath("$[0].status").value("RUNNING"));

            var agent = agentRepository.findById(agentId).orElseThrow();
            agent.setLastSeenAt(Instant.now().minusSeconds(5));
            agentRepository.save(agent);
            agentLifecycleService.markTimedOutAgentsOffline();

            assertThat(agentRepository.findById(agentId).orElseThrow().getStatus()).isEqualTo(AgentStatus.OFFLINE);
            assertThat(infrastructureRepository.findById(infrastructure.infrastructureId()).orElseThrow().getHealthStatus())
                    .isEqualTo(HealthStatus.UNKNOWN);
        } finally {
            channel.shutdownNow();
        }
    }

    private RegisteredInfrastructure createSelfHostedInfrastructure(String email) throws Exception {
        String auth = mockMvc.perform(post("/api/auth/register")
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
        String accessToken = JsonTestSupport.extractString(auth, "accessToken");
        String infra = mockMvc.perform(post("/api/infrastructures")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "StormAPI",
                                  "type": "SELF_HOSTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new RegisteredInfrastructure(accessToken, JsonTestSupport.extractString(infra, "id"));
    }

    private RegistrationCredential createRegistrationCredential(RegisteredInfrastructure infrastructure) throws Exception {
        String response = mockMvc.perform(post("/api/infrastructures/%s/agent-registration".formatted(infrastructure.infrastructureId()))
                        .header("Authorization", "Bearer " + infrastructure.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationToken").isString())
                .andExpect(jsonPath("$.installCommand").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return new RegistrationCredential(JsonTestSupport.extractString(response, "registrationToken"));
    }

    private String registerAgent(String registrationToken) throws Exception {
        return mockMvc.perform(post("/api/agents/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "registrationToken": "%s",
                                  "agentVersion": "test-agent"
                                }
                                """.formatted(registrationToken)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private record RegisteredInfrastructure(String accessToken, String infrastructureId) {
    }

    private record RegistrationCredential(String token) {
    }
}

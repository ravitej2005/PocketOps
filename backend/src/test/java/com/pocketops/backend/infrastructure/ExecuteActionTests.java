package com.pocketops.backend.infrastructure;

import com.pocketops.backend.agent.AgentEntity;
import com.pocketops.backend.agent.AgentGrpcCommandDispatcher;
import com.pocketops.backend.agent.AgentRepository;
import com.pocketops.backend.agent.AgentStatus;
import com.pocketops.backend.auth.JsonTestSupport;
import com.pocketops.backend.proto.ServerEnvelope;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pocketops-action;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "pocketops.auth.jwt.secret=test-secret-that-is-long-enough-for-hs256",
        "pocketops.agent.grpc.port=29090",
        "pocketops.agent.heartbeat-timeout-seconds=45"
})
@AutoConfigureMockMvc
class ExecuteActionTests {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentGrpcCommandDispatcher commandDispatcher;

    @Autowired
    private InfrastructureRepository infrastructureRepository;

    @Autowired
    private InfrastructureResourceRepository resourceRepository;

    // ---- AGENT OFFLINE ➜ immediate fail, no queue ----
    @Test
    void executeActionOnOfflineAgentReturnsServiceUnavailable() throws Exception {
        SetupResult setup = createInfrastructureWithResource("offline-action@example.com", AgentStatus.OFFLINE);
        mockMvc.perform(post("/api/infrastructures/%s/resources/%s/actions".formatted(
                        setup.infrastructureId, setup.externalResourceId))
                        .header("Authorization", "Bearer " + setup.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "STOP_CONTAINER" }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AGENT_OFFLINE"));
    }

    // ---- UNKNOWN ACTION ➜ rejected ----
    @Test
    void executeActionWithUnknownActionReturnsBadRequest() throws Exception {
        SetupResult setup = createInfrastructureWithResource("unknown-action@example.com", AgentStatus.ONLINE);
        mockMvc.perform(post("/api/infrastructures/%s/resources/%s/actions".formatted(
                        setup.infrastructureId, setup.externalResourceId))
                        .header("Authorization", "Bearer " + setup.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "DELETE_CONTAINER" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPABILITY_UNSUPPORTED"));
    }

    // ---- CAPABILITY NOT PRESENT ➜ rejected ----
    @Test
    void executeActionWithMissingCapabilityReturnsBadRequest() throws Exception {
        SetupResult setup = createInfrastructureWithResource("no-cap@example.com", AgentStatus.ONLINE);
        // Remove STOP capability from infrastructure
        InfrastructureEntity infra = infrastructureRepository.findById(setup.infrastructureId).orElseThrow();
        infra.getCapabilities().remove(Capability.STOP);
        infrastructureRepository.save(infra);

        mockMvc.perform(post("/api/infrastructures/%s/resources/%s/actions".formatted(
                        setup.infrastructureId, setup.externalResourceId))
                        .header("Authorization", "Bearer " + setup.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "STOP_CONTAINER" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CAPABILITY_UNSUPPORTED"));
    }

    // ---- RESOURCE NOT FOUND ➜ rejected ----
    @Test
    void executeActionOnNonexistentResourceReturns404() throws Exception {
        SetupResult setup = createInfrastructureWithResource("noresource@example.com", AgentStatus.ONLINE);
        mockMvc.perform(post("/api/infrastructures/%s/resources/%s/actions".formatted(
                        setup.infrastructureId, "nonexistent-resource-id"))
                        .header("Authorization", "Bearer " + setup.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "STOP_CONTAINER" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ---- ONLINE AGENT WITH NO ACTIVE GRPC STREAM ➜ AGENT_OFFLINE ----
    @Test
    void executeActionOnOnlineAgentWithoutStreamReturnsServiceUnavailable() throws Exception {
        SetupResult setup = createInfrastructureWithResource("no-stream@example.com", AgentStatus.ONLINE);
        // Agent is ONLINE in DB, but no gRPC stream registered in dispatcher.
        // This simulates the race condition where agent disconnects between DB check and dispatch.
        mockMvc.perform(post("/api/infrastructures/%s/resources/%s/actions".formatted(
                        setup.infrastructureId, setup.externalResourceId))
                        .header("Authorization", "Bearer " + setup.accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "STOP_CONTAINER" }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AGENT_OFFLINE"));
    }

    // ---- ONLINE AGENT WITH ACTIVE GRPC STREAM ➜ allow-listed command dispatched ----
    @Test
    void executeActionDispatchesStopCommandToActiveAgentStream() throws Exception {
        SetupResult setup = createInfrastructureWithResource("dispatch-action@example.com", AgentStatus.ONLINE);
        AgentEntity agent = agentRepository.findByInfrastructure_Id(setup.infrastructureId).orElseThrow();
        RecordingObserver observer = new RecordingObserver();
        commandDispatcher.registerStream(agent.getId(), observer);
        try {
            mockMvc.perform(post("/api/infrastructures/%s/resources/%s/actions".formatted(
                            setup.infrastructureId, setup.externalResourceId))
                            .header("Authorization", "Bearer " + setup.accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "action": "STOP_CONTAINER" }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("DISPATCHED"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());

            ServerEnvelope command = observer.command;
            org.assertj.core.api.Assertions.assertThat(command).isNotNull();
            org.assertj.core.api.Assertions.assertThat(command.getCommand().getAction().name())
                    .isEqualTo("STOP_CONTAINER");
            org.assertj.core.api.Assertions.assertThat(command.getCommand().getExternalResourceId())
                    .isEqualTo(setup.externalResourceId);
            org.assertj.core.api.Assertions.assertThat(command.getCommand().getCorrelationId()).isNotBlank();
        } finally {
            commandDispatcher.unregisterStream(agent.getId(), observer);
        }
    }

    // ---- Helpers ----

    private SetupResult createInfrastructureWithResource(String email, AgentStatus agentStatus) throws Exception {
        // Register user
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

        // Create infrastructure
        String infra = mockMvc.perform(post("/api/infrastructures")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "TestInfra",
                                  "type": "SELF_HOSTED"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String infrastructureId = JsonTestSupport.extractString(infra, "id");
        InfrastructureEntity infrastructure = infrastructureRepository.findById(infrastructureId).orElseThrow();

        // Create agent with specified status
        AgentEntity agent = agentRepository.findByInfrastructure_Id(infrastructureId).orElseGet(() -> {
            AgentEntity a = new AgentEntity();
            a.setInfrastructure(infrastructure);
            return a;
        });
        agent.setIdentityTokenHash("test-hash-" + email);
        agent.setStatus(agentStatus);
        agent.setRegisteredAt(Instant.now());
        agent.setLastSeenAt(Instant.now());
        agentRepository.save(agent);

        // Create resource
        String externalResourceId = "test-container-" + email.hashCode();
        InfrastructureResourceEntity resource = new InfrastructureResourceEntity();
        resource.setInfrastructure(infrastructure);
        resource.setExternalResourceId(externalResourceId);
        resource.setDisplayName("TestContainer");
        resource.setResourceType("CONTAINER");
        resource.setStatus("RUNNING");
        resource.setCriticality("NORMAL");
        resource.setLastSeenAt(Instant.now());
        resourceRepository.save(resource);

        return new SetupResult(accessToken, infrastructureId, externalResourceId);
    }

    private record SetupResult(String accessToken, String infrastructureId, String externalResourceId) {
    }

    private static final class RecordingObserver implements StreamObserver<ServerEnvelope> {
        private ServerEnvelope command;

        @Override
        public void onNext(ServerEnvelope value) {
            command = value;
        }

        @Override
        public void onError(Throwable throwable) {
        }

        @Override
        public void onCompleted() {
        }
    }
}

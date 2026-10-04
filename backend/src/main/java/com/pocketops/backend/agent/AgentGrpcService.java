package com.pocketops.backend.agent;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.infrastructure.InfrastructureResourceService;
import com.pocketops.backend.monitoring.MonitoringService;
import com.pocketops.backend.proto.AgentControlGrpc;
import com.pocketops.backend.proto.AgentEnvelope;
import com.pocketops.backend.proto.ConfigAck;
import com.pocketops.backend.proto.Heartbeat;
import com.pocketops.backend.proto.InfrastructureSnapshot;
import com.pocketops.backend.proto.ServerEnvelope;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AgentGrpcService extends AgentControlGrpc.AgentControlImplBase {
    static final Metadata.Key<String> IDENTITY_TOKEN_HEADER =
            Metadata.Key.of("agent-identity-token", Metadata.ASCII_STRING_MARSHALLER);

    private final AgentRegistrationService agentRegistrationService;
    private final AgentLifecycleService agentLifecycleService;
    private final InfrastructureResourceService infrastructureResourceService;
    private final MonitoringService monitoringService;
    private final AgentGrpcCommandDispatcher commandDispatcher;

    public AgentGrpcService(
            AgentRegistrationService agentRegistrationService,
            AgentLifecycleService agentLifecycleService,
            InfrastructureResourceService infrastructureResourceService,
            MonitoringService monitoringService,
            AgentGrpcCommandDispatcher commandDispatcher
    ) {
        this.agentRegistrationService = agentRegistrationService;
        this.agentLifecycleService = agentLifecycleService;
        this.infrastructureResourceService = infrastructureResourceService;
        this.monitoringService = monitoringService;
        this.commandDispatcher = commandDispatcher;
    }

    @Override
    public StreamObserver<AgentEnvelope> connect(StreamObserver<ServerEnvelope> responseObserver) {
        StreamObserver<ServerEnvelope> serializedResponseObserver = new SerializedStreamObserver(responseObserver);
        return new StreamObserver<>() {
            private AgentIdentity identity;

            @Override
            public void onNext(AgentEnvelope envelope) {
                try {
                    if (identity == null) {
                        identity = authenticate(envelope);
                        serializedResponseObserver.onNext(ack("connected"));
                        commandDispatcher.registerStream(identity.agent().getId(), serializedResponseObserver);
                    }
                    if (envelope.hasHeartbeat()) {
                        Heartbeat heartbeat = envelope.getHeartbeat();
                        agentLifecycleService.recordHeartbeat(identity, heartbeat.getAgentVersion());
                        serializedResponseObserver.onNext(ack("heartbeat"));
                    }
                    if (envelope.hasInfrastructureSnapshot()) {
                        InfrastructureSnapshot snapshot = envelope.getInfrastructureSnapshot();
                        infrastructureResourceService.reconcile(
                                identity.agent().getId(),
                                snapshot.getResourcesList()
                        );
                        serializedResponseObserver.onNext(ack("infrastructure_snapshot"));
                    }
                    if (envelope.hasContainerMetric()) {
                        monitoringService.recordMetric(
                                identity.infrastructureId(),
                                envelope.getContainerMetric(),
                                envelope.getTimestampUnixMs()
                        );
                    }
                    if (envelope.hasCommandResult()) {
                        // Agent executed a command; request an immediate snapshot so state updates
                        // propagate within milliseconds rather than waiting for the periodic interval.
                        serializedResponseObserver.onNext(requestSnapshot());
                    }
                } catch (ApiException ex) {
                    serializedResponseObserver.onError(Status.UNAUTHENTICATED
                            .withDescription(ex.getMessage())
                            .asRuntimeException());
                } catch (RuntimeException ex) {
                    serializedResponseObserver.onError(Status.INTERNAL
                            .withDescription("Agent stream failed.")
                            .asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (identity != null) {
                    commandDispatcher.unregisterStream(identity.agent().getId(), serializedResponseObserver);
                }
            }

            @Override
            public void onCompleted() {
                if (identity != null) {
                    commandDispatcher.unregisterStream(identity.agent().getId(), serializedResponseObserver);
                }
                serializedResponseObserver.onCompleted();
            }
        };
    }

    private static final class SerializedStreamObserver implements StreamObserver<ServerEnvelope> {
        private final StreamObserver<ServerEnvelope> delegate;

        private SerializedStreamObserver(StreamObserver<ServerEnvelope> delegate) {
            this.delegate = delegate;
        }

        @Override
        public synchronized void onNext(ServerEnvelope value) {
            delegate.onNext(value);
        }

        @Override
        public synchronized void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public synchronized void onCompleted() {
            delegate.onCompleted();
        }
    }

    private AgentIdentity authenticate(AgentEnvelope envelope) {
        String identityToken = AgentGrpcIdentityTokenContext.current();
        if (identityToken == null || identityToken.isBlank()) {
            throw new ApiException(
                    ErrorCode.AUTHENTICATION_REQUIRED,
                    HttpStatus.UNAUTHORIZED,
                    "Agent authentication failed."
            );
        }
        return agentRegistrationService.authenticate(
                envelope.getAgentId(),
                envelope.getInfrastructureId(),
                identityToken
        );
    }

    private ServerEnvelope ack(String status) {
        return ServerEnvelope.newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setTimestampUnixMs(Instant.now().toEpochMilli())
                .setConfigAck(ConfigAck.newBuilder().setStatus(status).build())
                .build();
    }

    private ServerEnvelope requestSnapshot() {
        return ServerEnvelope.newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setTimestampUnixMs(Instant.now().toEpochMilli())
                .setConfigAck(ConfigAck.newBuilder().setStatus("request_snapshot").build())
                .build();
    }
}

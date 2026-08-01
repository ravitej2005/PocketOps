package com.pocketops.backend.agent;

import com.pocketops.backend.common.error.ApiException;
import com.pocketops.backend.common.error.ErrorCode;
import com.pocketops.backend.proto.AgentControlGrpc;
import com.pocketops.backend.proto.AgentEnvelope;
import com.pocketops.backend.proto.ConfigAck;
import com.pocketops.backend.proto.Heartbeat;
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

    public AgentGrpcService(
            AgentRegistrationService agentRegistrationService,
            AgentLifecycleService agentLifecycleService
    ) {
        this.agentRegistrationService = agentRegistrationService;
        this.agentLifecycleService = agentLifecycleService;
    }

    @Override
    public StreamObserver<AgentEnvelope> connect(StreamObserver<ServerEnvelope> responseObserver) {
        return new StreamObserver<>() {
            private AgentIdentity identity;

            @Override
            public void onNext(AgentEnvelope envelope) {
                try {
                    if (identity == null) {
                        identity = authenticate(envelope);
                        responseObserver.onNext(ack("connected"));
                    }
                    if (envelope.hasHeartbeat()) {
                        Heartbeat heartbeat = envelope.getHeartbeat();
                        agentLifecycleService.recordHeartbeat(identity, heartbeat.getAgentVersion());
                        responseObserver.onNext(ack("heartbeat"));
                    }
                } catch (ApiException ex) {
                    responseObserver.onError(Status.UNAUTHENTICATED
                            .withDescription(ex.getMessage())
                            .asRuntimeException());
                } catch (RuntimeException ex) {
                    responseObserver.onError(Status.INTERNAL
                            .withDescription("Agent stream failed.")
                            .asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                // The scheduled heartbeat timeout owns ONLINE -> OFFLINE transitions.
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
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
}

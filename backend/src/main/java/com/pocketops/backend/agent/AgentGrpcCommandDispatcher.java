package com.pocketops.backend.agent;

import com.pocketops.backend.proto.Command;
import com.pocketops.backend.proto.CommandAction;
import com.pocketops.backend.proto.ServerEnvelope;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AgentGrpcCommandDispatcher {
    private final Map<String, StreamObserver<ServerEnvelope>> activeStreams = new ConcurrentHashMap<>();

    public void registerStream(String agentId, StreamObserver<ServerEnvelope> responseObserver) {
        activeStreams.put(agentId, responseObserver);
    }

    public void unregisterStream(String agentId) {
        activeStreams.remove(agentId);
    }

    public void dispatch(String agentId, String infrastructureId, String externalResourceId, String action, String correlationId) {
        StreamObserver<ServerEnvelope> stream = activeStreams.get(agentId);
        if (stream == null) {
            throw new IllegalStateException("No active gRPC stream for agent: " + agentId);
        }

        CommandAction commandAction = switch (action) {
            case "START_CONTAINER" -> CommandAction.START_CONTAINER;
            case "STOP_CONTAINER" -> CommandAction.STOP_CONTAINER;
            case "RESTART_CONTAINER" -> CommandAction.RESTART_CONTAINER;
            default -> throw new IllegalArgumentException("Unknown action: " + action);
        };

        Command command = Command.newBuilder()
                .setCorrelationId(correlationId)
                .setExternalResourceId(externalResourceId)
                .setAction(commandAction)
                .build();

        ServerEnvelope envelope = ServerEnvelope.newBuilder()
                .setMessageId(UUID.randomUUID().toString())
                .setTimestampUnixMs(Instant.now().toEpochMilli())
                .setCommand(command)
                .build();

        stream.onNext(envelope);
    }
}
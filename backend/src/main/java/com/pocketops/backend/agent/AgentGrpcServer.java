package com.pocketops.backend.agent;

import io.grpc.Server;
import io.grpc.ServerInterceptors;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class AgentGrpcServer implements SmartLifecycle {
    private final AgentGrpcService agentGrpcService;
    private final AgentGrpcIdentityInterceptor agentGrpcIdentityInterceptor;
    private final AgentProperties agentProperties;
    private Server server;
    private boolean running;

    public AgentGrpcServer(
            AgentGrpcService agentGrpcService,
            AgentGrpcIdentityInterceptor agentGrpcIdentityInterceptor,
            AgentProperties agentProperties
    ) {
        this.agentGrpcService = agentGrpcService;
        this.agentGrpcIdentityInterceptor = agentGrpcIdentityInterceptor;
        this.agentProperties = agentProperties;
    }

    @Override
    public void start() {
        try {
            NettyServerBuilder builder = NettyServerBuilder.forPort(agentProperties.grpc().port())
                    .addService(ServerInterceptors.intercept(agentGrpcService, agentGrpcIdentityInterceptor));
            AgentProperties.Tls tls = agentProperties.grpc().tls();
            if (tls != null && tls.enabled()) {
                if (tls.certChainPath() == null || tls.privateKeyPath() == null) {
                    throw new IllegalStateException("Agent gRPC TLS requires certificate chain and private key paths.");
                }
                builder.useTransportSecurity(new File(tls.certChainPath()), new File(tls.privateKeyPath()));
            }
            server = builder.build().start();
            running = true;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to start Agent gRPC server.", ex);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

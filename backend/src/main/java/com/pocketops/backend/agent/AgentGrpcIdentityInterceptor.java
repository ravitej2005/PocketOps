package com.pocketops.backend.agent;

import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import org.springframework.stereotype.Component;

@Component
public class AgentGrpcIdentityInterceptor implements ServerInterceptor {
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next
    ) {
        String identityToken = headers.get(AgentGrpcService.IDENTITY_TOKEN_HEADER);
        ServerCall.Listener<ReqT> listener = next.startCall(call, headers);
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onMessage(ReqT message) {
                AgentGrpcIdentityTokenContext.set(identityToken);
                try {
                    super.onMessage(message);
                } finally {
                    AgentGrpcIdentityTokenContext.clear();
                }
            }
        };
    }
}

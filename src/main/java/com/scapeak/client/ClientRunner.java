package com.scapeak.client;

import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.stack.core.Stack;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

/**
 * @author sooyaaa
 * @createTime 2023/4/25
 */
public class ClientRunner {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CompletableFuture<OpcUaClient> future = new CompletableFuture<>();
    private OpcUaClient client;

    public ClientRunner(String opcuaUrl) throws Exception {
        run(opcuaUrl);
    }


    Predicate<EndpointDescription> endpointFilter() {
        return e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri());
    }

    public void createClient(String opcuaUrl) throws Exception {

        client = OpcUaClient.create(
                opcuaUrl,
                endpoints ->
                        endpoints.stream()
                                .filter(endpointFilter())
                                .findFirst(),
                configBuilder ->
                        configBuilder
                                .setApplicationName(LocalizedText.english("eclipse milo opc-ua client"))
                                .setApplicationUri("urn:eclipse:milo:examples:client")
                                .setIdentityProvider(new AnonymousProvider())
                                .setRequestTimeout(uint(5000))
                                .build()
        );
    }

    public ClientRunner run(String opcuaUrl) throws Exception {
        createClient(opcuaUrl);
        return this;
    }

    public CompletableFuture<OpcUaClient> getFuture() {
        return future;
    }

    public OpcUaClient getClient() {
        return client;
    }

    public void setClient(OpcUaClient client) {
        this.client = client;
    }
}
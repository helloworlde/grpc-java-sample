package io.github.helloworlde.grpc.nameresovler;

import io.grpc.NameResolver;
import io.grpc.NameResolverProvider;

import java.net.URI;

public class CustomNameResolverProvider extends NameResolverProvider {
    @Override
    public NameResolver newNameResolver(URI targetUri, NameResolver.Args args) {
        return new CustomNameResolver(targetUri.toString());
    }

    @Override
    protected boolean isAvailable() {
        return true;
    }

    @Override
    protected int priority() {
        return 10;
    }

    @Override
    public String getDefaultScheme() {
        return "http";
    }
}

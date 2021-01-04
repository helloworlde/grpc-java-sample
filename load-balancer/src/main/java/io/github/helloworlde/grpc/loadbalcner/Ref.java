package io.github.helloworlde.grpc.loadbalcner;

public final class Ref<T> {
    T value;

    public Ref(T value) {
        this.value = value;
    }

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}

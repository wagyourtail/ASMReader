package xyz.wagyourtail.asm.compiler;

import java.io.IOException;

@FunctionalInterface
public interface IOBiFunction<T, U, V> {

    V apply(T t, U u) throws IOException;

}

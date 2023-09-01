package xyz.wagyourtail.asmreader.iofunction;

import java.io.IOException;

@FunctionalInterface
public interface IOSupplier<T> {

    T get() throws IOException;

}

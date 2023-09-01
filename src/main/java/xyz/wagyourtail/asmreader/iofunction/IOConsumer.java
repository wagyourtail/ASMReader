package xyz.wagyourtail.asmreader.iofunction;

import java.io.IOException;

@FunctionalInterface
public interface IOConsumer<T> {

    void accept(T t) throws IOException;

}

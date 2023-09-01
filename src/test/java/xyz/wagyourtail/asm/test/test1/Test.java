package xyz.wagyourtail.asm.test.test1;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class Test {
    @Target({ElementType.TYPE, ElementType.TYPE_USE})
    public @interface Inner {
    }
}
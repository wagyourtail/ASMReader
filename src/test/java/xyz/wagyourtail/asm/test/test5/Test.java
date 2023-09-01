package xyz.wagyourtail.asm.test.test5;


import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

public class Test {
    @Target({ElementType.PARAMETER})
    public @interface Inner {
        int value() default 0;
    }
}
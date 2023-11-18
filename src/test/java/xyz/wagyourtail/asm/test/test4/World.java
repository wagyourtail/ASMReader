package xyz.wagyourtail.asm.test.test4;

import xyz.wagyourtail.asm.test.SuperClass;
import xyz.wagyourtail.asm.test.SuperInterface1;
import xyz.wagyourtail.asm.test.test1.Test;

public class World extends @Test.Inner SuperClass implements @Test.Inner SuperInterface1 {

    public static final int a = 5;
    public static final long b = 13587152378125L;
    public static final float c = 5.5f;
    public static final double d = 5.5d;
    public static final String e = "echo";
    public static final String f = "ec\"ho";

    public static final boolean g = true;

    @Override
    public void test() {

    }
}

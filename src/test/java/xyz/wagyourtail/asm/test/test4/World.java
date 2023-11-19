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

    public static final String g = "\u00a7e[DEBUG]\u00a7r ";
    public static final String h = "\\u00a7e[DEBUG]\\u00a7r ";


    public static final boolean i = true;

    private static final Double nan = Double.NaN;

    private static final Double inf = Double.POSITIVE_INFINITY;

    private static final Float ninf = Float.NEGATIVE_INFINITY;

    private static final Double zero = 0.0;

    @Override
    public void test() {

    }
}

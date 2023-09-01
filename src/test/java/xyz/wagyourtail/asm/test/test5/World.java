package xyz.wagyourtail.asm.test.test5;

public class World {
    public static void test(@Test.Inner(42) final int a) {
        System.out.println(a);
    }

    public static void test2(@Test.Inner(72) float b) {
        System.out.println(b);
    }
}

package xyz.wagyourtail.asm.test.test8;

public class World {
    public static void main(String[] args) {
        test((Hello1 & Hello2) () -> System.out.println("Hello World!"));
    }

    public static void test(Hello2 tester) {
        tester.test();
    }

    interface Hello1 {

    }

    interface Hello2 {
        void test();
    }
}
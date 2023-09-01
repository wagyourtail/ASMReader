package xyz.wagyourtail.asm.test.test1;

public class World implements Runnable, Comparable<@Test.Inner Object> {
    public static void main(String[] args) {
        System.out.println("Hello, world!");
        System.out.println(Test.Inner.class);
    }

    @Override
    public void run() {
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }
}

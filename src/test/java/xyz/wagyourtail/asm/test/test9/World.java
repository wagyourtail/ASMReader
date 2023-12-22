package xyz.wagyourtail.asm.test.test9;

public class World {
    public static void main(String[] args) {
        switch (new Object()) {
            case Hello1.TEST -> {
                System.out.println("Hello World!");
            }
            case String s -> System.out.println(s);
            default -> System.out.println("default");
        }
    }

    enum Hello1 {
        TEST
    }
}
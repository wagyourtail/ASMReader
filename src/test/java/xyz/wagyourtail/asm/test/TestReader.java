package xyz.wagyourtail.asm.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import xyz.wagyourtail.asm.compiler.TokenReader;
import xyz.wagyourtail.asm.compiler.file.ASMReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(value = TestInstance.Lifecycle.PER_CLASS)
public class TestReader {

    private String test;

    public TokenReader readInputToken(String input) {
        return new TokenReader(new InputStreamReader(TestReader.class.getResourceAsStream(input)));
    }

    public String readInputString(String input) throws IOException {
        return new String(TestReader.class.getResourceAsStream(input).readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    @TestAnnotation(value = {
            @TestInstance(value = TestInstance.Lifecycle.PER_CLASS),
            @TestInstance(value = TestInstance.Lifecycle.PER_METHOD)
    }, t2 = "test", t3 = 0, t4 = 0.0, t5 = 0.0F, t6 = 0L, t7 = '1', t8 = TestReader.class)
    public void test1() throws IOException {
        TokenReader reader = readInputToken("/Test1.javasm");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, new Textifier(), pw);
        ASMReader asmReader = new ASMReader(reader);
        asmReader.accept(traceClassVisitor);
        assertEquals(readInputString("/Test1.javasm"), baos.toString());
    }

    public @interface TestAnnotation {
        TestInstance[] value();
        String t2();
        int t3();
        double t4();
        float t5();
        long t6();
        char t7();
        Class<?> t8();
    }

}

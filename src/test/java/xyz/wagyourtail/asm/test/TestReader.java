package xyz.wagyourtail.asm.test;

import org.apache.commons.io.function.IOConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;
import xyz.wagyourtail.asm.compiler.DeterministicTextifier;
import xyz.wagyourtail.asm.compiler.TokenReader;
import xyz.wagyourtail.asm.compiler.file.ASMReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;

@TestInstance(value = TestInstance.Lifecycle.PER_CLASS)
public class TestReader {

    private String test;

    public void compileJavasm(String asm, ClassVisitor visitor) throws IOException {
        TokenReader reader = new TokenReader(new StringReader(asm));
        ASMReader asmReader = new ASMReader(reader);
        asmReader.accept(visitor);
    }

    public String classToTextify(IOConsumer<ClassVisitor> visitor) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, new DeterministicTextifier(), pw);
        visitor.accept(traceClassVisitor);
        return baos.toString();
    }

    public String classToAsmify(IOConsumer<ClassVisitor> visitor) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, new ASMifier(), pw);
        visitor.accept(traceClassVisitor);
        return baos.toString();
    }

    public void readInClass(String path, ClassVisitor visitor) throws IOException {
        ClassReader reader = new ClassReader(TestReader.class.getResourceAsStream(path));
        reader.accept(visitor, 0);
    }

    @Test
    public void test1() throws IOException {
        String original = classToTextify(e -> readInClass("test1/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test1/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }

    @Test
    public void test2() throws IOException {
        String original = classToTextify(e -> readInClass("test2/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test2/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }

    @Test
    public void test3() throws IOException {
        String original = classToTextify(e -> readInClass("test3/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test3/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }

    @Test
    public void test5() throws IOException {
        String original = classToTextify(e -> readInClass("test5/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test5/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }

    @Test
    public void test6() throws IOException {
        String original = classToTextify(e -> readInClass("test6/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test6/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }

    @Test
    public void test7() throws IOException {
        String original = classToTextify(e -> readInClass("test7/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test7/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }

}

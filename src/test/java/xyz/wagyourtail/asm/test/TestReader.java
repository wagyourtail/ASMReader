package xyz.wagyourtail.asm.test;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.ASMifier;
import org.objectweb.asm.util.TraceClassVisitor;
import xyz.wagyourtail.asmreader.DeterministicTextifier;
import xyz.wagyourtail.asmreader.file.ClassReader;
import xyz.wagyourtail.asmreader.iofunction.IOConsumer;
import xyz.wagyourtail.asmreader.token.TokenReader;

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
        ClassReader asmReader = new ClassReader(reader);
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
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, new FixASMIfier(), pw);
        visitor.accept(traceClassVisitor);
        return baos.toString();
    }

    public void readInClass(String path, ClassVisitor visitor) throws IOException {
        org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(TestReader.class.getResourceAsStream(path));
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
        assertEquals(original, recompiled);
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
    public void test4() throws IOException {
        String original = classToTextify(e -> readInClass("test4/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test4/World.class", e));
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

    @Test
    public void test8() throws IOException {
        String original = classToTextify(e -> readInClass("test8/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test8/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }

    @Test
    public void test9() throws IOException {
        String original = classToTextify(e -> readInClass("test9/World.class", e));
        String recompiled = classToTextify(e -> compileJavasm(original, e));
        assertEquals(original, recompiled);
        // asmifier original and recompiled
        String originalAsm = classToAsmify(e -> readInClass("test9/World.class", e));
        String recompiledAsm = classToAsmify(e -> compileJavasm(original, e));
        assertEquals(originalAsm, recompiledAsm);
    }


    private static class FixASMIfier extends ASMifier {
        public FixASMIfier() {
            super(Opcodes.ASM9, "classWriter", 0);
        }

        public FixASMIfier(final int api, final String classVisitorVariableName, final int annotationVisitorId) {
            super(api, classVisitorVariableName, annotationVisitorId);
        }

        private void appendAccessFlags(final int accessFlags) {
            boolean isEmpty = true;
            if ((accessFlags & Opcodes.ACC_PUBLIC) != 0) {
                stringBuilder.append("ACC_PUBLIC");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_PRIVATE) != 0) {
                stringBuilder.append("ACC_PRIVATE");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_PROTECTED) != 0) {
                stringBuilder.append("ACC_PROTECTED");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_FINAL) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_FINAL");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_STATIC) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_STATIC");
                isEmpty = false;
            }
            if ((accessFlags & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_SUPER | Opcodes.ACC_TRANSITIVE))
                != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                if ((accessFlags & 0x40000) == 0) {
                    if ((accessFlags & 0x200000) == 0) {
                        stringBuilder.append("ACC_SYNCHRONIZED");
                    } else {
                        stringBuilder.append("ACC_TRANSITIVE");
                    }
                } else {
                    stringBuilder.append("ACC_SUPER");
                }
                isEmpty = false;
            }
            if ((accessFlags & (Opcodes.ACC_VOLATILE | Opcodes.ACC_BRIDGE | Opcodes.ACC_STATIC_PHASE))
                != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                if ((accessFlags & 0x80000) == 0) {
                    if ((accessFlags & 0x200000) == 0) {
                        stringBuilder.append("ACC_BRIDGE");
                    } else {
                        stringBuilder.append("ACC_STATIC_PHASE");
                    }
                } else {
                    stringBuilder.append("ACC_VOLATILE");
                }
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_VARARGS) != 0
                && (accessFlags & (0x40000 | 0x80000)) == 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_VARARGS");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_TRANSIENT) != 0 && (accessFlags & 0x80000) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_TRANSIENT");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_NATIVE) != 0
                && (accessFlags & (0x40000 | 0x80000)) == 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_NATIVE");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_ENUM) != 0
                && (accessFlags & (0x40000 | 0x80000 | 0x100000)) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_ENUM");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_ANNOTATION) != 0
                && (accessFlags & (0x40000 | 0x100000)) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_ANNOTATION");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_ABSTRACT) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_ABSTRACT");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_INTERFACE) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_INTERFACE");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_STRICT) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_STRICT");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_SYNTHETIC) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_SYNTHETIC");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_DEPRECATED) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_DEPRECATED");
                isEmpty = false;
            }
            if ((accessFlags & Opcodes.ACC_RECORD) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                stringBuilder.append("ACC_RECORD");
                isEmpty = false;
            }
            if ((accessFlags & (Opcodes.ACC_MANDATED | Opcodes.ACC_MODULE)) != 0) {
                if (!isEmpty) {
                    stringBuilder.append(" | ");
                }
                if ((accessFlags & 0x40000) == 0) {
                    stringBuilder.append("ACC_MANDATED");
                } else {
                    stringBuilder.append("ACC_MODULE");
                }
                isEmpty = false;
            }
            if (isEmpty) {
                stringBuilder.append('0');
            }
        }

        @Override
        public void visitParameter(String parameterName, int access) {
            if (parameterName == null) {
                stringBuilder.setLength(0);
                stringBuilder.append(name).append(".visitParameter(null");
                stringBuilder.append(", ");
                appendAccessFlags(access);
                text.add(stringBuilder.append(");\n").toString());
                return;
            }
            super.visitParameter(parameterName, access);
        }

        @Override
        protected ASMifier createASMifier(String visitorVariableName, int annotationVisitorId) {
            return new FixASMIfier(api, visitorVariableName, annotationVisitorId);
        }
    }
}

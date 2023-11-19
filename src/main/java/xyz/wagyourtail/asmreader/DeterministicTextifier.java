package xyz.wagyourtail.asmreader;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceSignatureVisitor;

public class DeterministicTextifier extends Textifier {
    private static final String CLASS_SUFFIX = ".class";
    private static final String DEPRECATED = "// DEPRECATED\n";

    private int access;

    public DeterministicTextifier() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.access = access;
    }

    private void appendRawAccess(final int accessFlags) {
        stringBuilder
                .append("// access flags 0x")
                .append(Integer.toHexString(accessFlags).toUpperCase())
                .append('\n');
    }

    private void appendJavaDeclaration(final String name, final String signature) {
        TraceSignatureVisitor traceSignatureVisitor = new TraceSignatureVisitor(access);
        new SignatureReader(signature).accept(traceSignatureVisitor);
        stringBuilder.append("// declaration: ");
        if (traceSignatureVisitor.getReturnType() != null) {
            stringBuilder.append(traceSignatureVisitor.getReturnType());
            stringBuilder.append(' ');
        }
        stringBuilder.append(name);
        stringBuilder.append(traceSignatureVisitor.getDeclaration());
        if (traceSignatureVisitor.getExceptions() != null) {
            stringBuilder.append(" throws ").append(traceSignatureVisitor.getExceptions());
        }
        stringBuilder.append('\n');
    }

    private void appendAccess(final int accessFlags) {
        if ((accessFlags & Opcodes.ACC_PUBLIC) != 0) {
            stringBuilder.append("public ");
        }
        if ((accessFlags & Opcodes.ACC_PRIVATE) != 0) {
            stringBuilder.append("private ");
        }
        if ((accessFlags & Opcodes.ACC_PROTECTED) != 0) {
            stringBuilder.append("protected ");
        }
        if ((accessFlags & Opcodes.ACC_FINAL) != 0) {
            stringBuilder.append("final ");
        }
        if ((accessFlags & Opcodes.ACC_STATIC) != 0) {
            stringBuilder.append("static ");
        }
        if ((accessFlags & Opcodes.ACC_SYNCHRONIZED) != 0) {
            stringBuilder.append("synchronized ");
        }
        if ((accessFlags & Opcodes.ACC_VOLATILE) != 0) {
            stringBuilder.append("volatile ");
        }
        if ((accessFlags & Opcodes.ACC_TRANSIENT) != 0) {
            stringBuilder.append("transient ");
        }
        if ((accessFlags & Opcodes.ACC_ABSTRACT) != 0) {
            stringBuilder.append("abstract ");
        }
        if ((accessFlags & Opcodes.ACC_STRICT) != 0) {
            stringBuilder.append("strictfp ");
        }
        if ((accessFlags & Opcodes.ACC_SYNTHETIC) != 0) {
            stringBuilder.append("synthetic ");
        }
        if ((accessFlags & Opcodes.ACC_MANDATED) != 0) {
            stringBuilder.append("mandated ");
        }
        if ((accessFlags & Opcodes.ACC_ENUM) != 0) {
            stringBuilder.append("enum ");
        }
    }

    private Textifier addNewTextifier(final String endText) {
        Textifier textifier = createTextifier();
        text.add(textifier.getText());
        if (endText != null) {
            text.add(endText);
        }
        return textifier;
    }

    public void appendValue(Object value) {
        if (value instanceof Double) {
            stringBuilder.append(value).append('D');
        } else if (value instanceof Float) {
            stringBuilder.append(value).append('F');
        } else if (value instanceof Long) {
            stringBuilder.append(value).append('L');
        } else if (value instanceof String) {
            Printer.appendString(stringBuilder, (String) value);
        } else if (value instanceof Type) {
            stringBuilder.append(((Type) value).getDescriptor()).append(CLASS_SUFFIX);
        } else {
            stringBuilder.append(value);
        }
    }

    @Override
    public void visitLdcInsn(Object value) {
        stringBuilder.setLength(0);
        stringBuilder.append(tab2).append("LDC ");
        appendValue(value);
        stringBuilder.append('\n');
        text.add(stringBuilder.toString());
    }

    public Textifier visitField(
            final int access,
            final String name,
            final String descriptor,
            final String signature,
            final Object value) {
        stringBuilder.setLength(0);
        stringBuilder.append('\n');
        if ((access & Opcodes.ACC_DEPRECATED) != 0) {
            stringBuilder.append(tab).append(DEPRECATED);
        }
        stringBuilder.append(tab);
        appendRawAccess(access);
        if (signature != null) {
            stringBuilder.append(tab);
            appendDescriptor(FIELD_SIGNATURE, signature);
            stringBuilder.append(tab);
            appendJavaDeclaration(name, signature);
        }

        stringBuilder.append(tab);
        appendAccess(access);

        appendDescriptor(FIELD_DESCRIPTOR, descriptor);
        stringBuilder.append(' ').append(name);
        if (value != null) {
            stringBuilder.append(" = ");
            appendValue(value);
        }

        stringBuilder.append('\n');
        text.add(stringBuilder.toString());
        return addNewTextifier(null);
    }

    @Override
    protected Textifier createTextifier() {
        return new DeterministicTextifier();
    }

}

package xyz.wagyourtail.asmreader;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
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
    private String condyIndent = tab2;

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

    private void visitType(final Type value) {
        stringBuilder.append(value.getClassName()).append(CLASS_SUFFIX);
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
        } else if (value instanceof ConstantDynamic) {
            appendCondy((ConstantDynamic) value);
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

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        stringBuilder.setLength(0);
        stringBuilder.append(tab2).append("INVOKEDYNAMIC").append(' ');
        stringBuilder.append(name);
        appendDescriptor(METHOD_DESCRIPTOR, descriptor);
        stringBuilder.append(" [");
        stringBuilder.append('\n');
        stringBuilder.append(tab3);
        appendHandle(bootstrapMethodHandle);
        stringBuilder.append('\n');
        stringBuilder.append(tab3).append("// arguments:");
        if (bootstrapMethodArguments.length == 0) {
            stringBuilder.append(" none");
        } else {
            stringBuilder.append('\n');
            for (Object value : bootstrapMethodArguments) {
                stringBuilder.append(tab3);
                if (value instanceof Type) {
                    Type type = (Type) value;
                    if (type.getSort() == Type.METHOD) {
                        appendDescriptor(METHOD_DESCRIPTOR, type.getDescriptor());
                    } else {
                        visitType(type);
                    }
                } else if (value instanceof Handle) {
                    appendHandle((Handle) value);
                } else {
                    condyIndent = tab3;
                    appendValue(value);
                    condyIndent = tab2;
                }
                stringBuilder.append(", \n");
            }
            stringBuilder.setLength(stringBuilder.length() - 3);
        }
        stringBuilder.append('\n');
        stringBuilder.append(tab2).append("]\n");
        text.add(stringBuilder.toString());
    }

    public void appendCondy(ConstantDynamic condy) {
        stringBuilder.append("// constant dynamic: ").append("\n").append(condyIndent);
        condyIndent = condyIndent + tab;
        tab3 = condyIndent;
        stringBuilder.append(condy.getDescriptor()).append(" : ");
        Printer.appendString(stringBuilder, condy.getName());
        stringBuilder.append(" [\n").append(condyIndent);
        appendHandle(condy.getBootstrapMethod());
        stringBuilder.append("\n");
        for (int i = 0; i < condy.getBootstrapMethodArgumentCount(); i++) {
            stringBuilder.append(condyIndent);
            Object value = condy.getBootstrapMethodArgument(i);
            if (value instanceof Type) {
                Type type = (Type) value;
                if (type.getSort() == Type.METHOD) {
                    appendDescriptor(METHOD_DESCRIPTOR, type.getDescriptor());
                } else {
                    visitType(type);
                }
            } else if (value instanceof Handle) {
                appendHandle((Handle) value);
            } else {
                appendValue(value);
            }
            stringBuilder.append(", \n");
        }
        condyIndent = condyIndent.substring(0, condyIndent.length() - tab.length());
        stringBuilder.append(condyIndent).append("]");
        tab3 = tab2 + tab;
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

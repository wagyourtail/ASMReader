package xyz.wagyourtail.asmreader;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;

public class DeterministicTextifier extends Textifier {
    private static final String CLASS_SUFFIX = ".class";

    public DeterministicTextifier() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visitLdcInsn(Object value) {
        stringBuilder.setLength(0);
        stringBuilder.append(tab2).append("LDC ");
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
        stringBuilder.append('\n');
        text.add(stringBuilder.toString());
    }

    @Override
    protected Textifier createTextifier() {
        return new DeterministicTextifier();
    }
}

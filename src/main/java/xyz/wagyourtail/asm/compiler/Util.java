package xyz.wagyourtail.asm.compiler;

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.Map;

public class Util {

    private static final Map<String, Integer> accessMap = Map.ofEntries(
        Map.entry("public", Opcodes.ACC_PUBLIC),
        Map.entry("private", Opcodes.ACC_PRIVATE),
        Map.entry("protected", Opcodes.ACC_PROTECTED),
        Map.entry("final", Opcodes.ACC_FINAL),
        Map.entry("static", Opcodes.ACC_STATIC),
        Map.entry("synchronized", Opcodes.ACC_SYNCHRONIZED),
        Map.entry("volatile", Opcodes.ACC_VOLATILE),
        Map.entry("transient", Opcodes.ACC_TRANSIENT),
        Map.entry("abstract", Opcodes.ACC_ABSTRACT),
        Map.entry("strictfp", Opcodes.ACC_STRICT),
        Map.entry("synthetic", Opcodes.ACC_SYNTHETIC),
        Map.entry("mandated", Opcodes.ACC_MANDATED)
    );

    public static int getAccess(TokenReader lister) throws IOException {
        int access = 0;
        Token tk;
        while ((tk = lister.popIf(t -> t.type == Token.TokenType.TOKEN && accessMap.containsKey(t.value.toLowerCase()))) != null) {
            access |= accessMap.get(tk.value);
        }
        return access;
    }

    public static int indexOfFirst(String val, char... c) {
        int min = -1;
        for (char ch : c) {
            int i = val.indexOf(ch);
            if (i != -1 && (min == -1 || i < min)) {
                min = i;
            }
        }
        return min;
    }
}

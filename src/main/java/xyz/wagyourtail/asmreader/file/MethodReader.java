package xyz.wagyourtail.asmreader.file;

import org.objectweb.asm.*;
import xyz.wagyourtail.asmreader.token.Token;
import xyz.wagyourtail.asmreader.token.TokenReader;
import xyz.wagyourtail.asmreader.token.TokenType;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;

public class MethodReader extends AbstractReader implements AnnotationVisitorSupplier {
    MethodVisitor visitor;
    boolean abstractFlag;
    boolean interfaceFlag;

    public MethodReader(TokenReader reader) {
        super(reader);
    }

    public void acceptWithHeader(IMethodVisitorConstructor cv) throws IOException {
        if (this.visitor != null) throw new IllegalStateException("Already accepted");
        readMethodHeader(cv);
        readMethodContent();
        visitEnd();
    }

    public void readMethodHeader(IMethodVisitorConstructor cv) throws IOException {
        int access = 0;
        Integer accessFlags = null;
        // access flags
        int a;
        do {
            Token accessComment = reader.popIf(t -> t.type == TokenType.COMMENT && AbstractReader.ACCESS_FLAGS.matcher(t.value).find());
            if (accessComment != null) {
                Matcher m = AbstractReader.ACCESS_FLAGS.matcher(accessComment.value);
                if (m.find()) {
                    accessFlags = Integer.parseInt(m.group("accessFlags"), 16);
                }
                continue;
            }
            reader.popNonCommentIf(e -> false);
            a = AbstractReader.getAccess(reader);
            access |= a;
            if (a != 0) {
                continue;
            }
            Token type = reader.popExpect(TokenType.TOKEN);
            if (type.value.contains("(")) {
                // split name and desc
                int paren = type.value.lastIndexOf('(');
                String name = type.value.substring(0, paren);
                String desc = type.value.substring(paren);
                Token thro = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("throws"));
                List<Type> exceptions = new ArrayList<>();
                if (thro != null) {
                    while (true) {
                        Token tk = reader.popIf(e -> e.type == TokenType.TOKEN && !AbstractReader.OPCODES.containsKey(e.value.toUpperCase()) && !e.value.matches("L\\d+") && !AbstractReader.SPECIAL_OPCODES.contains(e.value.toUpperCase()) && !e.value.startsWith("@"));
                        if (tk == null) {
                            break;
                        }
                        exceptions.add(Type.getObjectType(tk.value));
                    }
                }
                visitor = cv.visitMethod(accessFlags == null ? access : accessFlags, name, desc, null, exceptions.stream().map(Type::getInternalName).toArray(String[]::new));
                this.abstractFlag = ((accessFlags == null ? access : accessFlags) & ACC_ABSTRACT) != 0;
                this.interfaceFlag = ((accessFlags == null ? access : accessFlags) & ACC_INTERFACE) != 0;
            } else {
                throw new IllegalStateException("expected method type");
            }
            break;
        } while (true);
    }

    public void accept(MethodVisitor visitor, boolean abstractFlag, boolean interfaceFlag) throws IOException {
        if (this.visitor != null) throw new IllegalStateException("Already accepted");
        this.visitor = visitor;
        this.abstractFlag = abstractFlag;
        this.interfaceFlag = interfaceFlag;
        readMethodContent();
    }


    protected void readMethodContent() throws IOException {
        // read method content
        boolean visitCode = false;
        Integer maxStack = null;
        Integer maxLocals = null;
        Map<Integer, Label> labels = new HashMap<>();
        boolean completed = false;
        while (!completed) {
            // parameter comment
            Token parameterComment = reader.popIf(t -> t.type == TokenType.COMMENT && t.value.trim().startsWith("parameter"));
            if (parameterComment != null) {
                String[] parts = parameterComment.value.trim().substring(10).split(" ");
                String name;
                if (parameterComment.value.trim().endsWith("<no name>")) {
                    name = null;
                    parts = Arrays.copyOf(parts, parts.length - 2);
                } else {
                    name = parts[parts.length - 1];
                    parts = Arrays.copyOf(parts, parts.length - 1);
                }
                int access = 0;
                for (String part : parts) {
                    if (ACCESS_MAP.containsKey(part.toUpperCase())) {
                        access |= ACCESS_MAP.get(part.toUpperCase());
                    }
                }
                visitor.visitParameter(name, access);
                continue;
            }
            // annotable parameter comment
            Token annotableParameterComment = reader.popIf(t -> t.type == TokenType.COMMENT && AbstractReader.ANNOTABLE_PARAMETER_COUNT.matcher(t.value).find());
            if (annotableParameterComment != null) {
                Matcher m = AbstractReader.ANNOTABLE_PARAMETER_COUNT.matcher(annotableParameterComment.value);
                m.find();
                int count = Integer.parseInt(m.group("count"));
                boolean invisible = m.group("invisible").equals("invisible");
                visitor.visitAnnotableParameterCount(count, !invisible);
                continue;
            }
            Token annotation = reader.popNonCommentIf(t -> t.type == TokenType.TOKEN && t.value.startsWith("@"));
            if (annotation != null) {
                boolean finalVisitCode = visitCode;
                readAnnotation(annotation, labels, this);
                continue;
            }
            if (!visitCode) {
                if (interfaceFlag) {
                    // expect default=value
                    Token tk = reader.popIf(e -> e.type == TokenType.TOKEN && e.value.startsWith("default="));
                    if (tk != null) {
                        if (!tk.value.startsWith("default=")) {
                            reader.throwAtPos("Expected default=value");
                        }
                        if (tk.value.endsWith("=")) {
                            tk = reader.popNonComment();
                        }
                        Object primitive = readPrimitive(tk, 0);
                        AnnotationVisitor def = visitor.visitAnnotationDefault();
                        def.visit(null, primitive);
                        def.visitEnd();
                    }
                }
                if (abstractFlag) break;
                visitor.visitCode();
                visitCode = true;
            }
            Token tk = reader.popNonCommentExpect(TokenType.TOKEN);
            String value = tk.value.toUpperCase();
            if (value.matches("L\\d+")) {
                // label
                int label = Integer.parseInt(value.substring(1));
                if (!labels.containsKey(label)) {
                    labels.put(label, new Label());
                }
                visitor.visitLabel(labels.get(label));
                continue;
            }
            switch (value) {
                case "FRAME" -> {
                    tk = reader.popNonCommentExpect(TokenType.TOKEN);
                    switch (tk.value) {
                        case "FULL", "NEW" -> {
                            List<Object> locals = readFrameTypes(labels);
                            List<Object> stack = readFrameTypes(labels);
                            visitor.visitFrame(tk.value.equals("FULL") ? F_FULL : F_NEW, locals.size(), locals.toArray(), stack.size(), stack.toArray());
                        }
                        case "APPEND" -> {
                            List<Object> locals = readFrameTypes(labels);
                            visitor.visitFrame(F_APPEND, locals.size(), locals.toArray(), 0, null);
                        }
                        case "CHOP" -> {
                            Token tk2 = reader.popNonCommentExpect(TokenType.TOKEN);
                            visitor.visitFrame(F_CHOP, Integer.parseInt(tk2.value), null, 0, null);
                        }
                        case "SAME1" -> {
                            Token tk2 = reader.popNonCommentExpect(TokenType.TOKEN);
                            visitor.visitFrame(F_SAME1, 0, null, 1, new Object[]{tk2.value});
                        }
                        case "SAME" -> visitor.visitFrame(F_SAME, 0, null, 0, null);
                    }
                }
                case "LINENUMBER" -> {
                    Token lineNum = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (!lineNum.value.matches("\\d+")) {
                        reader.throwAtPos("Expected line number");
                    }
                    Token label = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (!label.value.matches("L\\d+")) {
                        reader.throwAtPos("Expected label");
                    }
                    visitor.visitLineNumber(Integer.parseInt(lineNum.value), labels.computeIfAbsent(Integer.parseInt(label.value.substring(1)), e -> new Label()));
                }
                case "LOCALVARIABLE" -> {
                    Token name = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (name.value.startsWith("@")) {
                        readAnnotation(name, labels, this);
                    } else {
                        Token descTk = reader.popNonCommentExpect(TokenType.TOKEN);
                        Token start = reader.popNonCommentExpect(TokenType.TOKEN);
                        if (!start.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token end = reader.popNonCommentExpect(TokenType.TOKEN);
                        if (!end.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token index = reader.popNonCommentExpect(TokenType.TOKEN);
                        if (!index.value.matches("\\d+")) {
                            reader.throwAtPos("Expected index");
                        }
                        String signature = readSignature();
                        visitor.visitLocalVariable(
                                name.value,
                                descTk.value,
                                signature,
                                labels.computeIfAbsent(Integer.parseInt(start.value.substring(1)), e -> new Label()),
                                labels.computeIfAbsent(Integer.parseInt(end.value.substring(1)), e -> new Label()),
                                Integer.parseInt(index.value)
                        );
                    }
                }
                case "MAXSTACK", "MAXLOCALS" -> {
                    Token equals = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (!equals.value.equals("=")) {
                        reader.throwAtPos("Expected =");
                    }
                    Token max = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (!max.value.matches("\\d+")) {
                        reader.throwAtPos("Expected integer");
                    }
                    if (value.equals("MAXSTACK")) {
                        maxStack = Integer.parseInt(max.value);
                    } else {
                        maxLocals = Integer.parseInt(max.value);
                    }
                    if (maxStack != null && maxLocals != null) {
                        visitor.visitMaxs(maxStack, maxLocals);
                        completed = true;
                    }
                }
                case "TRYCATCHBLOCK" -> {
                    Token start = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (start.value.startsWith("@")) {
                        readAnnotation(start, labels, this);
                    } else {
                        if (!start.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token end = reader.popNonCommentExpect(TokenType.TOKEN);
                        if (!end.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token handler = reader.popNonCommentExpect(TokenType.TOKEN);
                        if (!handler.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token type = reader.popNonCommentExpect(TokenType.TOKEN);
                        visitor.visitTryCatchBlock(
                                labels.computeIfAbsent(Integer.parseInt(start.value.substring(1)), e -> new Label()),
                                labels.computeIfAbsent(Integer.parseInt(end.value.substring(1)), e -> new Label()),
                                labels.computeIfAbsent(Integer.parseInt(handler.value.substring(1)), e -> new Label()),
                                type.value.equals("null") ? null : type.value
                        );
                    }
                }
                default -> {
                    if (!AbstractReader.OPCODES.containsKey(value)) {
                        reader.throwAtPos("Expected a valid opcode");
                    }
                    int opcode = AbstractReader.OPCODES.get(value);
                    switch (opcode) {
                        case BIPUSH, SIPUSH -> {
                            Token val = reader.popNonCommentExpect(TokenType.TOKEN);
                            if (!val.value.matches("\\d+")) {
                                reader.throwAtPos("Expected integer");
                            }
                            visitor.visitIntInsn(opcode, Integer.parseInt(val.value));
                        }
                        case LDC -> {
                            // TODO: support condy's
                            visitor.visitLdcInsn(readPrimitive(reader.popNonComment(), 0));
                        }
                        case ILOAD, ALOAD, FLOAD, DLOAD, LLOAD, ISTORE, ASTORE, FSTORE, DSTORE, LSTORE, RET -> {
                            Token index = reader.popNonCommentExpect(TokenType.TOKEN);
                            if (!index.value.matches("\\d+")) {
                                reader.throwAtPos("Expected index");
                            }
                            visitor.visitVarInsn(opcode, Integer.parseInt(index.value));
                        }
                        case IINC -> {
                            Token index = reader.popNonCommentExpect(TokenType.TOKEN);
                            if (!index.value.matches("\\d+")) {
                                reader.throwAtPos("Expected index");
                            }
                            Token inc = reader.popNonCommentExpect(TokenType.TOKEN);
                            if (!inc.value.matches("[-+]?\\d+")) {
                                reader.throwAtPos("Expected increment");
                            }
                            visitor.visitIincInsn(Integer.parseInt(index.value), Integer.parseInt(inc.value));
                        }
                        case IFEQ, IFNE, IFLT, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, GOTO, JSR, IFNULL, IFNONNULL -> {
                            Token label = reader.popNonCommentExpect(TokenType.TOKEN);
                            if (!label.value.matches("L\\d+")) {
                                reader.throwAtPos("Expected label");
                            }
                            visitor.visitJumpInsn(opcode, labels.computeIfAbsent(Integer.parseInt(label.value.substring(1)), e -> new Label()));
                        }
                        case TABLESWITCH -> {
                            Map<Integer, Label> tableEntries = new HashMap<>();
                            Integer min = null;
                            Integer current = null;
                            Integer next;
                            Label label;
                            do {
                                Token swtk = reader.popNonCommentExpect(TokenType.TOKEN);
                                // split on : if there
                                String[] split = swtk.value.split(":");
                                if (split[0].equals("default")) {
                                    next = null;
                                } else {
                                    next = Integer.parseInt(split[0]);
                                    if (min == null) {
                                        min = next;
                                    } else {
                                        if (next != current + 1) {
                                            reader.throwAtPos("Expected consecutive keys");
                                        }
                                    }
                                }
                                if (split.length == 1) {
                                    swtk = reader.popNonCommentExpect(TokenType.TOKEN);
                                    if (swtk.value.equals(":")) swtk = reader.popNonCommentExpect(TokenType.TOKEN);
                                    if (!swtk.value.matches("L\\d+")) {
                                        reader.throwAtPos("Expected label");
                                    }
                                    label = labels.computeIfAbsent(Integer.parseInt(swtk.value.substring(1)), e -> new Label());
                                } else {
                                    if (!split[1].matches("L\\d+")) {
                                        reader.throwAtPos("Expected label");
                                    }
                                    label = labels.computeIfAbsent(Integer.parseInt(split[1].substring(1)), e -> new Label());
                                }
                                tableEntries.put(next, label);
                                if (next != null) {
                                    current = next;
                                }
                            } while (next != null);
                            visitor.visitTableSwitchInsn(min == null ? 0 : min, current == null ? 0 : current, tableEntries.get(null), IntStream.range(min, current + 1).mapToObj(tableEntries::get).toArray(Label[]::new));
                        }
                        case LOOKUPSWITCH -> {
                            Map<Integer, Label> tableEntries = new HashMap<>();
                            Integer current;
                            do {
                                Token swtk = reader.popNonCommentExpect(TokenType.TOKEN);
                                // split on : if there
                                String[] split = swtk.value.split(":");
                                if (split[0].equals("default")) {
                                    current = null;
                                } else {
                                    current = Integer.parseInt(split[0]);
                                }
                                if (split.length == 1) {
                                    swtk = reader.popNonCommentExpect(TokenType.TOKEN);
                                    if (swtk.value.equals(":")) swtk = reader.popNonCommentExpect(TokenType.TOKEN);
                                    if (!swtk.value.matches("L\\d+")) {
                                        reader.throwAtPos("Expected label");
                                    }
                                    tableEntries.put(current, labels.computeIfAbsent(Integer.parseInt(swtk.value.substring(1)), e -> new Label()));
                                } else {
                                    if (!split[1].matches("L\\d+")) {
                                        reader.throwAtPos("Expected label");
                                    }
                                    tableEntries.put(current, labels.computeIfAbsent(Integer.parseInt(split[1].substring(1)), e -> new Label()));
                                }
                            } while (current != null);
                            int[] keys = tableEntries.keySet().stream().filter(Objects::nonNull).mapToInt(e -> e).toArray();
                            visitor.visitLookupSwitchInsn(tableEntries.get(null), keys, Arrays.stream(keys).mapToObj(tableEntries::get).toArray(Label[]::new));
                        }
                        case GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD -> {
                            Token owner = reader.popNonCommentExpect(TokenType.TOKEN);
                            int dot = owner.value.indexOf('.');
                            String ownerStr;
                            String name;
                            if (dot != -1) {
                                ownerStr = owner.value.substring(0, dot);
                                name = owner.value.substring(dot + 1);
                            } else {
                                ownerStr = owner.value;
                                Token nameTk = reader.popNonCommentExpect(TokenType.TOKEN);
                                name = nameTk.value;
                            }
                            reader.popNonCommentExpect(TokenType.TOKEN, ":");
                            Token descTk = reader.popNonCommentExpect(TokenType.TOKEN);
                            visitor.visitFieldInsn(opcode, ownerStr, name, descTk.value);
                        }
                        case INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL -> {
                            Token owner = reader.popNonCommentExpect(TokenType.TOKEN);
                            int dot = owner.value.indexOf('.');
                            String ownerStr;
                            String name;
                            if (dot != -1) {
                                ownerStr = owner.value.substring(0, dot);
                                name = owner.value.substring(dot + 1);
                            } else {
                                ownerStr = owner.value;
                                Token nameTk = reader.popNonCommentExpect(TokenType.TOKEN);
                                name = nameTk.value;
                            }
                            Token descTk = reader.popNonCommentExpect(TokenType.TOKEN);
                            Token itf = reader.popIf(e -> e.type == TokenType.TOKEN && e.value.equals("(itf)"));
                            visitor.visitMethodInsn(opcode, ownerStr, name, descTk.value, itf != null || opcode == INVOKEINTERFACE);
                        }
                        case INVOKEDYNAMIC -> {
                            Token func = reader.popNonCommentExpect(TokenType.TOKEN);
                            int paren = func.value.lastIndexOf('(');
                            String name = func.value.substring(0, paren);
                            String indyDesc = func.value.substring(paren);
                            List<Object> args = readDynamicArgs();
                            if (!(args.get(0) instanceof Handle)) {
                                reader.throwAtPos("Expected first INDY arg to be a handle!!!");
                            }
                            Handle bsm = (Handle) args.remove(0);
                            // first is bsm, lest are args
                            visitor.visitInvokeDynamicInsn(name, indyDesc, bsm, args.toArray());
                        }
                        case NEW, ANEWARRAY, CHECKCAST, INSTANCEOF -> {
                            Token descTk = reader.popNonCommentExpect(TokenType.TOKEN);
                            visitor.visitTypeInsn(opcode, descTk.value);
                        }
                        case NEWARRAY -> {
                            Token typeTk = reader.popNonCommentExpect(TokenType.TOKEN);
                            if (!AbstractReader.TYPES.containsKey(typeTk.value)) {
                                reader.throwAtPos("Expected valid type");
                            }
                            visitor.visitIntInsn(opcode, AbstractReader.TYPES.get(typeTk.value));
                        }
                        case MULTIANEWARRAY -> {
                            Token descTk = reader.popNonCommentExpect(TokenType.TOKEN);
                            Token dimsTk = reader.popNonCommentExpect(TokenType.TOKEN);
                            if (!dimsTk.value.matches("\\d+")) {
                                reader.throwAtPos("Expected integer");
                            }
                            visitor.visitMultiANewArrayInsn(descTk.value, Integer.parseInt(dimsTk.value));
                        }
                        default -> visitor.visitInsn(opcode);
                    }
                }
            }
        }
    }

    public List<String> readArray() throws IOException {
        Token tk = reader.popExpect(TokenType.TOKEN);
        if (!tk.value.startsWith("[")) {
            reader.throwAtPos("Expected [");
        }
        if (tk.value.equals("[]")) {
            return List.of();
        }
        if (tk.value.endsWith("]")) {
            return List.of(tk.value.substring(1, tk.value.length() - 1));
        }
        List<String> ret = new ArrayList<>();
        ret.add(tk.value.substring(1));
        while (!tk.value.endsWith("]")) {
            tk = reader.popExpect(TokenType.TOKEN);
            if (tk.value.endsWith("]")) {
                ret.add(tk.value.substring(0, tk.value.length() - 1));
            } else {
                ret.add(tk.value);
            }
        }
        return ret;
    }

    public List<Object> readFrameTypes(Map<Integer, Label> labels) throws IOException {
        return readArray().stream().map(e -> {
            if (AbstractReader.FRAME_TYPES.containsKey(e)) {
                return AbstractReader.FRAME_TYPES.get(e);
            }
            if (e.startsWith("L")) {
                int label = Integer.parseInt(e.substring(1));
                if (!labels.containsKey(label)) {
                    labels.put(label, new Label());
                }
                return labels.get(label);
            }
            return e;
        }).toList();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {// if comment says parameter
        if (token != null) {
            boolean invisible = false;
            int parameter = -1;
            Set<String> parts = Arrays.stream(token.value.split(",")).map(String::trim).collect(Collectors.toSet());
            for (String part : parts) {
                if (part.equals("invisible")) {
                    invisible = true;
                } else if (part.startsWith("parameter")) {
                    String[] split = part.split(" ");
                    if (split.length != 2) {
                        reader.throwAtPos("Expected parameter NUMBER", -part.length());
                    }
                    parameter = Integer.parseInt(split[1]);
                }
            }
            if (parameter == -1) {
                return visitor.visitAnnotation(desc, !invisible);
            }
            return visitor.visitParameterAnnotation(parameter, desc, !invisible);
        }
        return visitor.visitAnnotation(desc, true);
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
        TypeReference ref = new TypeReference(typeRef);
        return switch (ref.getSort()) {
            case TypeReference.EXCEPTION_PARAMETER ->
                    visitor.visitTryCatchAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
            case TypeReference.METHOD_TYPE_PARAMETER, TypeReference.METHOD_TYPE_PARAMETER_BOUND, TypeReference.METHOD_RETURN, TypeReference.METHOD_RECEIVER, TypeReference.METHOD_FORMAL_PARAMETER, TypeReference.THROWS ->
                    visitor.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
            case TypeReference.INSTANCEOF, TypeReference.NEW, TypeReference.CONSTRUCTOR_REFERENCE, TypeReference.METHOD_REFERENCE, TypeReference.CAST, TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT, TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT ->
                    visitor.visitInsnAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
            default -> {
                reader.throwAtPos("Unknown type reference sort: " + ref.getSort());
                yield null;
            }
        };
    }

    @Override
    public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, Token token) throws IOException {
        return visitor.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, token == null || !token.value.contains("invisible"));
    }

    @Override
    public void visitEnd() {
        visitor.visitEnd();
    }

    @FunctionalInterface
    public interface IMethodVisitorConstructor {
        MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions);
    }
}

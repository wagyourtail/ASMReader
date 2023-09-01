package xyz.wagyourtail.asm.compiler.file;

import org.objectweb.asm.*;
import org.objectweb.asm.util.Printer;
import xyz.wagyourtail.asm.compiler.Token;
import xyz.wagyourtail.asm.compiler.TokenReader;
import xyz.wagyourtail.asm.compiler.Util;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;

public class ASMReader {
    private final TokenReader reader;

    public ASMReader(TokenReader reader) {
        this.reader = reader;
    }

    private static final Pattern CLASS_VERSION = Pattern.compile("^\\s*class\\s*version:?\\s*(?<major>\\d+)\\.(?<minor>\\d+).*");

    private static final Pattern SIGNATURE = Pattern.compile("^\\s*signature:?\\s*(?<signature>.*)");

    public void accept(ClassVisitor visitor) throws IOException {
        int access = readHeader(visitor);
        readContent(visitor, access);
        readFooter(visitor);
    }

    private int readHeader(ClassVisitor visitor) throws IOException {
        int version = 0;
        String signature = null;
        int access = 0;
        while (reader.peek().type == Token.TokenType.COMMENT) {
            Token tk = reader.pop();
            Matcher m = CLASS_VERSION.matcher(tk.value);
            if (m.find()) {
                version = Integer.parseInt(m.group("minor")) << 16 | Integer.parseInt(m.group("major"));
                continue;
            }
            m = SIGNATURE.matcher(tk.value);
            if (m.find()) {
                signature = m.group("signature");
            }
            if (tk.value.trim().equals("DEPRECATED")) {
                access |= ACC_DEPRECATED;
            }
        }

        access |= Util.getAccess(reader);

        String classTk = reader.popNonCommentExpect(Token.TokenType.TOKEN, Set.of("class", "interface", "enum", "@interface"));
        if (classTk.equals("interface")) {
            access |= ACC_INTERFACE;
        } else if (classTk.equals("enum")) {
            access |= ACC_ENUM;
        } else if (classTk.equals("@interface")) {
            access |= ACC_ANNOTATION | ACC_INTERFACE;
        }

        Token nameTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
        Type type = Type.getObjectType(nameTk.value);

        Token extendsTk = reader.popNonCommentIf(e -> e.type == Token.TokenType.TOKEN && e.value.equals("extends"));
        Type superType;
        if (extendsTk != null) {
            Token superTk = reader.popExpect(Token.TokenType.TOKEN);
            if (superTk.value.equals("java/lang/Record")) {
                access |= ACC_RECORD;
            }
            superType = Type.getObjectType(superTk.value);
        } else {
            superType = Type.getObjectType("java/lang/Object");
        }

        Token implementsTk = reader.popNonCommentIf(e -> e.type == Token.TokenType.TOKEN && e.value.equals("implements"));
        List<Type> interfaces = new ArrayList<>();
        if (implementsTk != null) {
            while (true) {
                Token tk = reader.popNonCommentIf(t -> t.type == Token.TokenType.TOKEN && !t.value.equals("{"));
                if (tk == null) {
                    break;
                }
                interfaces.add(Type.getObjectType(tk.value));
            }
        }

        reader.popNonCommentExpect(Token.TokenType.TOKEN, "{");
        // if not abstract or interface, | ACC_SUPER
        if ((access & ACC_ABSTRACT) == 0 && (access & ACC_INTERFACE) == 0) {
            access |= ACC_SUPER;
        }
        visitor.visit(version, access, type.getInternalName(), signature, superType.getInternalName(), interfaces.stream().map(Type::getInternalName).toArray(String[]::new));
        return access;
    }

    private void readAnnotation(Token beginning, Map<Integer, Label> labels, AnnotationVisitorSupplier visitor) throws IOException {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        Token tk = beginning;
        String value = tk.value;
        int semi = value.indexOf(';');
        if (semi < 0) reader.throwAtPos("Expected Type Descriptor after @");
        Type type = Type.getType(value.substring(1, semi + 1));
        value = value.substring(semi + 1).trim();
        boolean exit = true;
        while (exit) {
            switch (tk.type) {
                case TOKEN -> {
                    for (char c : value.toCharArray()) {
                        if (c == '(') {
                            depth++;
                        } else if (c == ')') {
                            depth--;
                            continue;
                        }
                        if (depth < 0) {
                            reader.throwAtPos("Unexpected content after annotation )");
                        }
                    }
                    sb.append(value);
                    if (depth == 0) {
                        exit = false;
                    }
                }
                case CHAR -> sb.append("'").append(value).append("'");
                case STRING -> sb.append("\"").append(value).append("\"");
                case COMMENT -> {
                    // no-op
                }
                case EOF -> reader.throwAtPos("Unexpected EOF while reading annotation");
            }
            if (exit) {
                tk = reader.pop();
                value = tk.value;
            }
        }
        Token typeAnnotation = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals(":"));
        AnnotationVisitor av;
        if (typeAnnotation != null) {
            int typeRef = reverseTypeRef();
            TypePath typePath = reverseTypePath();
            Token lvAnnotation = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("["));
            if (lvAnnotation != null) {
                List<Label> start = new ArrayList<>();
                List<Label> end = new ArrayList<>();
                List<Integer> index = new ArrayList<>();
                do {
                    Token startL = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (!startL.value.matches("L\\d+")) {
                        reader.throwAtPos("Expected label");
                    }
                    start.add(labels.computeIfAbsent(Integer.parseInt(startL.value.substring(1)), e -> new Label()));
                    reader.popNonCommentExpect(Token.TokenType.TOKEN, "-");
                    Token endL = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (!endL.value.matches("L\\d+")) {
                        reader.throwAtPos("Expected label");
                    }
                    end.add(labels.computeIfAbsent(Integer.parseInt(endL.value.substring(1)), e -> new Label()));
                    reader.popNonCommentExpect(Token.TokenType.TOKEN, "-");
                    Token indexL = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (!indexL.value.matches("\\d+")) {
                        reader.throwAtPos("Expected index");
                    }
                    index.add(Integer.parseInt(indexL.value));
                    reader.popNonCommentExpect(Token.TokenType.TOKEN, "]");
                } while (reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("[")) != null);
                Token invis = reader.popIf(t -> t.type == Token.TokenType.COMMENT);
                av = visitor.visitLocalVariableAnnotation(typeRef, typePath, start.toArray(new Label[0]), end.toArray(new Label[0]), index.stream().mapToInt(e -> e).toArray(), type.getDescriptor(), invis);
            } else {
                // read to see if we have an "invisible" comment
                Token invis = reader.popIf(t -> t.type == Token.TokenType.COMMENT);
                av = visitor.visitTypeAnnotation(typeRef, typePath, type.getDescriptor(), invis);
            }
        } else {
            // read to see if we have an "invisible" comment
            Token invis = reader.popIf(t -> t.type == Token.TokenType.COMMENT);
            av = visitor.visitAnnotation(type.getDescriptor(), invis);
        }
        String content = readAnnotationContent(sb.toString().trim(), av);
        if (!content.isEmpty()) {
            reader.throwAtPos("Unexpected content after annotation )");
        }
        av.visitEnd();
    }

    private int reverseTypeRef() throws IOException {
        Token tk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
        String sv = tk.value;
        if (sv.endsWith(",")) {
            sv = sv.substring(0, sv.length() - 1);
        }
        int value = 0;
        switch (sv) {
            case "CLASS_TYPE_PARAMETER" -> {
                value += TypeReference.CLASS_TYPE_PARAMETER << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 16;
            }
            case "METHOD_TYPE_PARAMETER" -> {
                value += TypeReference.METHOD_TYPE_PARAMETER << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 16;
            }
            case "CLASS_EXTENDS" -> {
                value += TypeReference.CLASS_EXTENDS << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 8;
            }
            case "CLASS_TYPE_PARAMETER_BOUND" -> {
                value += TypeReference.CLASS_TYPE_PARAMETER_BOUND << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 16;
                param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 8;
            }
            case "METHOD_TYPE_PARAMETER_BOUND" -> {
                value += TypeReference.METHOD_TYPE_PARAMETER_BOUND << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 16;
                param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 8;
            }
            case "FIELD" -> {
                value += TypeReference.FIELD << 24;
            }
            case "METHOD_RETURN" -> {
                value += TypeReference.METHOD_RETURN << 24;
            }
            case "METHOD_RECEIVER" -> {
                value += TypeReference.METHOD_RECEIVER << 24;
            }
            case "METHOD_FORMAL_PARAMETER" -> {
                value += TypeReference.METHOD_FORMAL_PARAMETER << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 16;
            }
            case "THROWS" -> {
                value += TypeReference.THROWS << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 8;
            }
            case "LOCAL_VARIABLE" -> {
                value += TypeReference.LOCAL_VARIABLE << 24;
            }
            case "RESOURCE_VARIABLE" -> {
                value += TypeReference.RESOURCE_VARIABLE << 24;
            }
            case "EXCEPTION_PARAMETER" -> {
                value += TypeReference.EXCEPTION_PARAMETER << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param) << 8;
            }
            case "INSTANCEOF" -> {
                value += TypeReference.INSTANCEOF << 24;
            }
            case "NEW" -> {
                value += TypeReference.NEW << 24;
            }
            case "CONSTRUCTOR_REFERENCE" -> {
                value += TypeReference.CONSTRUCTOR_REFERENCE << 24;
            }
            case "METHOD_REFERENCE" -> {
                value += TypeReference.METHOD_REFERENCE << 24;
            }
            case "CAST" -> {
                value += TypeReference.CAST << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param);
            }
            case "CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT" -> {
                value += TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param);
            }
            case "METHOD_INVOCATION_TYPE_ARGUMENT" -> {
                value += TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param);
            }
            case "CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT" -> {
                value += TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param);
            }
            case "METHOD_REFERENCE_TYPE_ARGUMENT" -> {
                value += TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT << 24;
                String param = reader.popNonCommentExpect(Token.TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                value += Integer.parseInt(param);
            }
            default -> reader.throwAtPos("Unknown type reference: " + tk.value);
        }
        return value;
    }

    private TypePath reverseTypePath() throws IOException {
        Token tk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
        if (tk.value.equals("null")) {
            return null;
        }
        return TypePath.fromString(tk.value);
    }

    private String readAnnotationContent(String annotation, AnnotationVisitor visitor) throws IOException {
        // annotation should look like (name=value,name2={arr1,arr2}), values can be annotations, or primitives
        // remove first/last char, check if ()
        if (annotation.charAt(0) != '(') {
            reader.throwAtPos("Expected annotation to start with ( and end with )", -annotation.length());
        }
        annotation = annotation.substring(1);
        while (!annotation.startsWith(")")) {
            // read name
            int eq = annotation.indexOf('=');
            if (eq == -1) {
                reader.throwAtPos("Expected = in annotation", -annotation.length());
            }
            if (annotation.startsWith(",")) {
                // skip comma
                annotation = annotation.substring(1);
                eq--;
            }
            String name = annotation.substring(0, eq).trim();
            annotation = readAnnotationValue(annotation.substring(eq + 1).trim(), name, visitor);
        }
        return annotation.substring(1);
    }

    private String readAnnotationArray(String annotationContent, AnnotationVisitor visitor) throws IOException {
        while (!annotationContent.startsWith("}")) {
            if (annotationContent.startsWith("{")) {
                annotationContent = readAnnotationArray(annotationContent.substring(1), visitor.visitArray(null));
            } else if (!annotationContent.startsWith(",")) {
                annotationContent = readAnnotationValue(annotationContent, null, visitor);
            } else {
                // skip comma
                annotationContent = annotationContent.substring(1);
            }
        }
        visitor.visitEnd();
        return annotationContent.substring(1);
    }

    private String readAnnotationValue(String annotationContent, String name, AnnotationVisitor visitor) throws IOException {
        annotationContent = annotationContent.stripLeading();
        // enum, annotation, or primitive
        if (annotationContent.startsWith("@")) {
            // annotation
            int semi = annotationContent.indexOf(';');
            if (semi == -1) {
                reader.throwAtPos("Expected Type Descriptor after @", -annotationContent.length());
            }
            Type type = Type.getType(annotationContent.substring(1, semi + 1));
            annotationContent = annotationContent.substring(semi + 1).trim();
            return readAnnotationContent(annotationContent, visitor.visitAnnotation(name, type.getDescriptor()));
        } else if (annotationContent.startsWith("\"")) {
            // string
            annotationContent = annotationContent.substring(1);
            Matcher m = TokenReader.STRING_CHAR.matcher(annotationContent);
            if (!m.find()) {
                reader.throwAtPos("Expected end of string", -annotationContent.length());
            }
            visitor.visit(name, annotationContent.substring(0, m.end() - 1).translateEscapes());
            return annotationContent.substring(m.end());
        } else if (annotationContent.startsWith("{")) {
            // array
            return readAnnotationArray(annotationContent.substring(1), visitor.visitArray(name));
        } else {
            // primitive
            int end = Util.indexOfFirst(annotationContent, ',', '}', ')');
            if (end == -1) {
                reader.throwAtPos("Expected ',', '}' or ')' in annotation", -annotationContent.length());
            }
            String value = annotationContent.substring(0, end).trim();
            // class, enum, string or primitive
            if (value.matches("L([^;]+);\\s*\\.\\s*.+")) {
                // enum
                int dot = value.indexOf('.');
                Type enumType = Type.getType(value.substring(0, dot));
                String enumValue = value.substring(dot + 1);
                visitor.visitEnum(name, enumType.getDescriptor(), enumValue);
            } else if (value.endsWith(".class")) {
                // class
                Type type = Type.getObjectType(value.substring(0, value.length() - 6).replace('.', '/'));
                visitor.visit(name, type);
            } else {
                if (value.startsWith("(")) {
                    int end2 = Util.indexOfFirst(annotationContent.substring(end + 1), ')', ',', '}');
                    if (end2 == -1) {
                        reader.throwAtPos("Expected ',', '}' or ')' in annotation", -annotationContent.length());
                    }
                    value += annotationContent.substring(end, end + end2 + 1);
                    end = end + end2 + 1;
                }
                Token.TokenType type = Token.TokenType.TOKEN;
                if (value.startsWith("\"")) {
                    type = Token.TokenType.STRING;
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'")) {
                    type = Token.TokenType.CHAR;
                    value = value.substring(1, value.length() - 1);
                }
                visitor.visit(name, readPrimitive(new Token(value, type), -annotationContent.length()));
            }
            return annotationContent.substring(end);
        }
    }

    private Object readPrimitive(Token tk, int offset) throws IOException {
        if (tk.type == Token.TokenType.STRING) {
            return tk.value.translateEscapes();
        }
        if (tk.type == Token.TokenType.CHAR) {
            String val = tk.value;
            val = val.translateEscapes();
            if (val.length() != 1) {
                reader.throwAtPos("Expected single char", offset);
            }
            return val.charAt(0);
        }
        String value = tk.value;
        if (value.endsWith(".class")) {
            // class
            return Type.getType(value.substring(0, value.length() - 6).replace('.', '/'));
        }
        if (value.contains(".")) {
            // float/double
            if (value.endsWith("F") || value.endsWith("f")) {
                return Float.parseFloat(value.substring(0, value.length() - 1));
            } else if (value.endsWith("D") || value.endsWith("d")) {
                return Double.parseDouble(value.substring(0, value.length() - 1));
            } else {
                return Double.parseDouble(value);
            }
        } else if (value.matches("\\d+L?")) {
            // int/long
            if (value.endsWith("L") || value.endsWith("l")) {
                return Long.parseLong(value.substring(0, value.length() - 1));
            } else {
                return Integer.parseInt(value);
            }
        } else if (value.equals("true")) {
            // boolean
            return Boolean.TRUE;
        } else if (value.equals("false")) {
            // boolean
            return Boolean.FALSE;
        } else if (value.equals("null")) {
            // null
            return null;
        } else if (value.startsWith("'")) {
            // unescape char
            String val = value.substring(1, value.length() - 1);
            val = val.translateEscapes();
            if (val.length() != 1) {
                reader.throwAtPos("Expected single char", offset);
            }
            return val.charAt(0);
        } else if (value.startsWith("(short)")) {
            return Short.parseShort(value.substring(7));
        } else if (value.startsWith("(byte)")) {
            return Byte.parseByte(value.substring(6));
        } else {
            reader.throwAtPos("Unknown primitive value: " + value, offset);
            return null;
        }
    }

    private static final Pattern COMPILED_FROM = Pattern.compile("^\\s*compiled\\s*from:?\\s*(?<compiledFrom>.*)");
    private static final Pattern ACCESS_FLAGS = Pattern.compile("^\\s*access\\s*flags:?\\s*0x(?<accessFlags>[0-9a-fA-F]+)");

    private String readSignature() throws IOException {
        Token tk = reader.popIf(t -> t.type == Token.TokenType.COMMENT && SIGNATURE.matcher(t.value).find());
        if (tk == null) {
            return null;
        }
        Matcher m = SIGNATURE.matcher(tk.value);
        m.find();
        return m.group("signature");
    }

    private static final Map<String, Integer> opcodes = IntStream.range(0, Printer.OPCODES.length)
            .mapToObj(e -> Map.entry(Printer.OPCODES[e], e))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Map<String, Integer> FRAME_TYPES = Map.of(
            "T", TOP,
            "I", INTEGER,
            "F", FLOAT,
            "D", DOUBLE,
            "J", LONG,
            "N", NULL,
            "U", UNINITIALIZED_THIS
    );

    private static final Map<String, Integer> TYPES = IntStream.range(0, Printer.TYPES.length)
            .filter(e -> !Printer.TYPES[e].isEmpty())
            .mapToObj(e -> Map.entry(Printer.TYPES[e], e))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    private static final Set<String> SPECIAL_OPCODES = Set.of(
            "FRAME",
            "LINENUMBER",
            "LOCALVARIABLE",
            "MAXSTACK",
            "MAXLOCALS",
            "TRYCATCHBLOCK"
    );

    private static final Pattern ANNOTABLE_PARAMETER_COUNT = Pattern.compile("^\\s*annotable\\s*parameter\\s*count:?\\s*(?<count>\\d+)\\s*\\((?<invisible>invisible|visible)\\)");

    private static final Pattern HANDLE_KIND = Pattern.compile("^\\s*handle\\s*kind:?\\s*0x(?<kind>[\\da-fA-F]+)\\s*:\\s*(?<type>.*)");

    private void readMethodContent(MethodVisitor visitor, String desc, boolean abstrac, boolean intf) throws IOException {
        // read method content
        boolean visitCode = false;
        Integer maxStack = null;
        Integer maxLocals = null;
        Map<Integer, Label> labels = new HashMap<>();
        boolean completed = false;
        while (!completed) {
            // parameter comment
            Token parameterComment = reader.popIf(t -> t.type == Token.TokenType.COMMENT && t.value.trim().startsWith("parameter"));
            if (parameterComment != null) {
                visitor.visitParameter(parameterComment.value.trim().substring(10).trim(), 0);
                continue;
            }
            // annotable parameter comment
            Token annotableParameterComment = reader.popIf(t -> t.type == Token.TokenType.COMMENT && ANNOTABLE_PARAMETER_COUNT.matcher(t.value).find());
            if (annotableParameterComment != null) {
                Matcher m = ANNOTABLE_PARAMETER_COUNT.matcher(annotableParameterComment.value);
                m.find();
                int count = Integer.parseInt(m.group("count"));
                boolean invisible = m.group("invisible").equals("invisible");
                visitor.visitAnnotableParameterCount(count, !invisible);
                continue;
            }
            Token annotation = reader.popNonCommentIf(t -> t.type == Token.TokenType.TOKEN && t.value.startsWith("@"));
            if (annotation != null) {
                boolean finalVisitCode = visitCode;
                readAnnotation(annotation, labels, new AnnotationVisitorSupplier() {
                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                                // if comment says parameter
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
                                if (!finalVisitCode) {
                                    return visitor.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
                                } else {
                                    return visitor.visitInsnAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
                                }
                            }
                        }
                );
                continue;
            }
            if (!visitCode) {
                if (intf) {
                    // expect default=value
                    Token tk = reader.popIf(e -> e.type == Token.TokenType.TOKEN && e.value.startsWith("default="));
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
                if (abstrac) break;
                visitor.visitCode();
                visitCode = true;
            }
            Token tk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
                    tk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
                            Token tk2 = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            visitor.visitFrame(F_CHOP, Integer.parseInt(tk2.value), null, 0, null);
                        }
                        case "SAME1" -> {
                            Token tk2 = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            visitor.visitFrame(F_SAME1, 0, null, 1, new Object[]{tk2.value});
                        }
                        case "SAME" -> visitor.visitFrame(F_SAME, 0, null, 0, null);
                    }
                }
                case "LINENUMBER" -> {
                    Token lineNum = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (!lineNum.value.matches("\\d+")) {
                        reader.throwAtPos("Expected line number");
                    }
                    Token label = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (!label.value.matches("L\\d+")) {
                        reader.throwAtPos("Expected label");
                    }
                    visitor.visitLineNumber(Integer.parseInt(lineNum.value), labels.computeIfAbsent(Integer.parseInt(label.value.substring(1)), e -> new Label()));
                }
                case "LOCALVARIABLE" -> {
                    Token name = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (name.value.startsWith("@")) {
                        readAnnotation(name, labels, new AnnotationVisitorSupplier() {
                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, Token token) throws IOException {
                                return visitor.visitLocalVariableAnnotation(typeRef, typePath, start, end, index, desc, token == null || !token.value.contains("invisible"));
                            }
                        });
                    } else {
                        Token descTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                        Token start = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                        if (!start.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token end = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                        if (!end.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token index = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                        if (!index.value.matches("\\d+")) {
                            reader.throwAtPos("Expected index");
                        }
                        String signature = readSignature();
                        visitor.visitLocalVariable(name.value, descTk.value, signature, labels.computeIfAbsent(Integer.parseInt(start.value.substring(1)), e -> new Label()), labels.computeIfAbsent(Integer.parseInt(end.value.substring(1)), e -> new Label()), Integer.parseInt(index.value));
                    }
                }
                case "MAXSTACK", "MAXLOCALS" -> {
                    Token equals = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (!equals.value.equals("=")) {
                        reader.throwAtPos("Expected =");
                    }
                    Token max = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
                    Token start = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    if (start.value.startsWith("@")) {
                        readAnnotation(start, labels, new AnnotationVisitorSupplier() {
                            @Override
                            public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                                throw new UnsupportedOperationException();
                            }

                            @Override
                            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
                                return visitor.visitTryCatchAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
                            }
                        });
                    } else {
                        if (!start.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token end = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                        if (!end.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token handler = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                        if (!handler.value.matches("L\\d+")) {
                            reader.throwAtPos("Expected label");
                        }
                        Token type = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                        visitor.visitTryCatchBlock(labels.computeIfAbsent(Integer.parseInt(start.value.substring(1)), e -> new Label()), labels.computeIfAbsent(Integer.parseInt(end.value.substring(1)), e -> new Label()), labels.computeIfAbsent(Integer.parseInt(handler.value.substring(1)), e -> new Label()), type.value);
                    }
                }
                default -> {
                    if (!opcodes.containsKey(value)) {
                        reader.throwAtPos("Expected a valid opcode");
                    }
                    int opcode = opcodes.get(value);
                    switch (opcode) {
                        case BIPUSH, SIPUSH -> {
                            Token val = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            if (!val.value.matches("\\d+")) {
                                reader.throwAtPos("Expected integer");
                            }
                            visitor.visitIntInsn(opcode, Integer.parseInt(val.value));
                        }
                        case LDC -> visitor.visitLdcInsn(readPrimitive(reader.popNonComment(), 0));
                        case ILOAD, ALOAD, FLOAD, DLOAD, LLOAD, ISTORE, ASTORE, FSTORE, DSTORE, LSTORE, RET -> {
                            Token index = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            if (!index.value.matches("\\d+")) {
                                reader.throwAtPos("Expected index");
                            }
                            visitor.visitVarInsn(opcode, Integer.parseInt(index.value));
                        }
                        case IINC -> {
                            Token index = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            if (!index.value.matches("\\d+")) {
                                reader.throwAtPos("Expected index");
                            }
                            Token inc = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            if (!inc.value.matches("[-+]?\\d+")) {
                                reader.throwAtPos("Expected increment");
                            }
                            visitor.visitIincInsn(Integer.parseInt(index.value), Integer.parseInt(inc.value));
                        }
                        case IFEQ, IFNE, IFLT, IFGT, IFLE, IF_ICMPEQ, IF_ICMPNE, IF_ICMPLT, IF_ICMPGE, IF_ICMPGT, IF_ICMPLE, IF_ACMPEQ, IF_ACMPNE, GOTO, JSR, IFNULL, IFNONNULL -> {
                            Token label = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
                                Token swtk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
                                    swtk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                                    if (swtk.value.equals(":")) swtk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
                                Token swtk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                                // split on : if there
                                String[] split = swtk.value.split(":");
                                if (split[0].equals("default")) {
                                    current = null;
                                } else {
                                    current = Integer.parseInt(split[0]);
                                }
                                if (split.length == 1) {
                                    swtk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                                    if (swtk.value.equals(":")) swtk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
                            int[] keys = tableEntries.keySet().stream().mapToInt(e -> e).toArray();
                            visitor.visitLookupSwitchInsn(tableEntries.get(null), keys, Arrays.stream(keys).mapToObj(tableEntries::get).toArray(Label[]::new));
                        }
                        case GETSTATIC, PUTSTATIC, GETFIELD, PUTFIELD -> {
                            Token owner = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            int dot = owner.value.indexOf('.');
                            String ownerStr;
                            String name;
                            if (dot != -1) {
                                ownerStr = owner.value.substring(0, dot);
                                name = owner.value.substring(dot + 1);
                            } else {
                                ownerStr = owner.value;
                                Token nameTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                                name = nameTk.value;
                            }
                            reader.popNonCommentExpect(Token.TokenType.TOKEN, ":");
                            Token descTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            visitor.visitFieldInsn(opcode, ownerStr, name, descTk.value);
                        }
                        case INVOKEINTERFACE, INVOKESPECIAL, INVOKESTATIC, INVOKEVIRTUAL -> {
                            Token owner = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            int dot = owner.value.indexOf('.');
                            String ownerStr;
                            String name;
                            if (dot != -1) {
                                ownerStr = owner.value.substring(0, dot);
                                name = owner.value.substring(dot + 1);
                            } else {
                                ownerStr = owner.value;
                                Token nameTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                                name = nameTk.value;
                            }
                            Token descTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            visitor.visitMethodInsn(opcode, ownerStr, name, descTk.value, opcode == INVOKEINTERFACE);
                        }
                        case INVOKEDYNAMIC -> {
                            Token func = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            int paren = func.value.lastIndexOf('(');
                            String name = func.value.substring(0, paren);
                            String indyDesc = func.value.substring(paren);
                            Token openSq = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            if (!openSq.value.equals("[")) {
                                reader.throwAtPos("Expected [");
                            }
                            Integer handleType = null;
                            Handle bsm = null;
                            List<Object> args = new ArrayList<>();
                            while (!reader.peekExpect(Token.TokenType.TOKEN, "]")) {
                                Token handleKind = reader.popIf(t -> t.type == Token.TokenType.COMMENT && HANDLE_KIND.matcher(t.value).find());
                                if (handleKind != null) {
                                    Matcher m = HANDLE_KIND.matcher(handleKind.value);
                                    m.find();
                                    handleType = Integer.parseInt(m.group("kind"), 16);
                                    continue;
                                }
                                Token nextTk = reader.popNonComment();
                                Object next = null;
                                if (nextTk.type == Token.TokenType.TOKEN && nextTk.value.equals(",")) {
                                    continue;
                                }
                                if (handleType != null) {
                                    String owner;
                                    String hname;
                                    String hdesc;
                                    String hvalue = nextTk.value;
                                    if (hvalue.endsWith(",")) {
                                        hvalue = hvalue.substring(0, hvalue.length() - 1);
                                    }
                                    int dot = nextTk.value.indexOf('.');
                                    if (dot != -1) {
                                        owner = hvalue.substring(0, dot);
                                        hvalue = hvalue.substring(dot + 1);
                                    } else {
                                        nextTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                                        owner = hvalue;
                                        hvalue = nextTk.value;
                                        if (hvalue.endsWith(",")) {
                                            hvalue = hvalue.substring(0, hvalue.length() - 1);
                                        }
                                    }
                                    int hparen = hvalue.lastIndexOf('(');
                                    if (hparen != -1) {
                                        hdesc = hvalue.substring(hparen);
                                        hname = hvalue.substring(0, hparen);
                                    } else {
                                        nextTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                                        hname = hvalue;
                                        hvalue = nextTk.value;
                                        if (hvalue.endsWith(",")) {
                                            hvalue = hvalue.substring(0, hvalue.length() - 1);
                                        }
                                        hdesc = hvalue;
                                    }
                                    if (hdesc.endsWith(")")) {
                                        hdesc = hdesc.substring(1, hdesc.length() - 1);
                                    }
                                    boolean itf = false;
                                    if (!nextTk.value.endsWith(",")) {
                                        String s = reader.peekExpect(Token.TokenType.TOKEN, Set.of("itf", "itf,"));
                                        if (s != null) itf = true;
                                    }
                                    next = new Handle(handleType, owner, hname, hdesc, itf);
                                    handleType = null;
                                } else if (nextTk.type == Token.TokenType.TOKEN && nextTk.value.equals(":")) {
                                    throw new UnsupportedOperationException("ConstantDynamic not supported yet");
                                } else {
                                    String ivalue = nextTk.value;
                                    if (ivalue.endsWith(",")) {
                                        ivalue = ivalue.substring(0, ivalue.length() - 1);
                                    }
                                    if (ivalue.endsWith(".class")) {
                                        // to type
                                        next = Type.getObjectType(ivalue.substring(0, ivalue.length() - 6).replace('.', '/'));
                                    } else {
                                        try {
                                            next = readPrimitive(nextTk, 0);
                                        } catch (TokenReader.UnexpectedTokenException ex) {
                                            if (ex.msg.startsWith("Unknown primitive value")) {
                                                try {
                                                    next = Type.getType(ivalue);
                                                } catch (IllegalArgumentException e) {
                                                    try {
                                                        reader.throwAtPos("Expected a valid type");
                                                    } catch (TokenReader.UnexpectedTokenException ee) {
                                                        ee.addSuppressed(e);
                                                        ee.addSuppressed(ex);
                                                        throw ee;
                                                    }
                                                }
                                            } else {
                                                try {
                                                    reader.throwAtPos("Expected a valid primitive");
                                                } catch (TokenReader.UnexpectedTokenException ee) {
                                                    ee.addSuppressed(ex);
                                                    throw ee;
                                                }
                                            }
                                        }
                                    }
                                }
                                if (bsm == null) {
                                    assert next instanceof Handle;
                                    bsm = (Handle) next;
                                } else {
                                    args.add(next);
                                }
                            }
                            reader.popNonCommentExpect(Token.TokenType.TOKEN, "]");
                            // first is bsm, lest are args
                            visitor.visitInvokeDynamicInsn(name, indyDesc, bsm, args.toArray());
                        }
                        case NEW, ANEWARRAY, CHECKCAST, INSTANCEOF -> {
                            Token descTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            visitor.visitTypeInsn(opcode, descTk.value);
                        }
                        case NEWARRAY -> {
                            Token typeTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            if (!TYPES.containsKey(typeTk.value)) {
                                reader.throwAtPos("Expected valid type");
                            }
                            visitor.visitIntInsn(opcode, TYPES.get(typeTk.value));
                        }
                        case MULTIANEWARRAY -> {
                            Token descTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                            Token dimsTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
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
        visitor.visitEnd();
    }

    public List<String> readArray() throws IOException {
        Token tk = reader.popExpect(Token.TokenType.TOKEN);
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
            tk = reader.popExpect(Token.TokenType.TOKEN);
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
            if (FRAME_TYPES.containsKey(e)) {
                return FRAME_TYPES.get(e);
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

    private void readContent(ClassVisitor visitor, int classAccess) throws IOException {
        String signature = null;
        AnnotationVisitorSupplier lastAnnotationVisitor = new AnnotationVisitorSupplier() {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                return visitor.visitAnnotation(desc, token == null || !token.value.contains("invisible"));
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
                return visitor.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
            }
        };
        int access = 0;
        Integer accessFlags = null;
        while (!reader.peekExpect(Token.TokenType.TOKEN, "}")) {
            // comments
            Token sourceComment = reader.popIf(t -> t.type == Token.TokenType.COMMENT && COMPILED_FROM.matcher(t.value).find());
            if (sourceComment != null) {
                Matcher m = COMPILED_FROM.matcher(sourceComment.value);
                if (m.find()) {
                    visitor.visitSource(m.group("compiledFrom"), null);
                }
                continue;
            }
            // deprecated comment
            Token deprecatedComment = reader.popIf(t -> t.type == Token.TokenType.COMMENT && t.value.trim().equals("DEPRECATED"));
            if (deprecatedComment != null) {
                access |= ACC_DEPRECATED;
                continue;
            }
            {
                String sig = readSignature();
                if (sig != null) {
                    signature = sig;
                    continue;
                }
            }
            // access flags
            Token accessComment = reader.popIf(t -> t.type == Token.TokenType.COMMENT && ACCESS_FLAGS.matcher(t.value).find());
            if (accessComment != null) {
                Matcher m = ACCESS_FLAGS.matcher(accessComment.value);
                if (m.find()) {
                    accessFlags = Integer.parseInt(m.group("accessFlags"), 16);
                }
                continue;
            }
            // tokens
            reader.popNonCommentIf(e -> false);
            int a = Util.getAccess(reader);
            access |= a;
            if (a != 0) {
                continue;
            }
            Token annotation = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.startsWith("@"));
            if (annotation != null) {
                if (access != 0 || accessFlags != null) {
                    reader.throwAtPos("Didn't expect access modifier with annotation");
                }
                readAnnotation(annotation, null, lastAnnotationVisitor);
                continue;
            }
            Token nestMember = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("NESTMEMBER"));
            if (nestMember != null) {
                if (access != 0 || accessFlags != null) {
                    reader.throwAtPos("Didn't expect access modifier with NESTMEMBER");
                }
                Token tk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                visitor.visitNestMember(tk.value);
                continue;
            }
            Token nestHost = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("NESTHOST"));
            if (nestHost != null) {
                if (access != 0) {
                    reader.throwAtPos("Didn't expect access modifier with NESTHOST");
                }
                Token tk = reader.popExpect(Token.TokenType.TOKEN);
                visitor.visitNestHost(tk.value);
                continue;
            }
            Token recordComponent = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("RECORDCOMPONENT"));
            if (recordComponent != null) {
                lastAnnotationVisitor.visitEnd();
                if (access != 0 || accessFlags != null) {
                    reader.throwAtPos("Didn't expect access modifier with RECORDCOMPONENT");
                }
                String sig = null;
                while (reader.peekExpect(Token.TokenType.COMMENT) != null) {
                    String s = readSignature();
                    if (s != null) {
                        sig = s;
                    }
                }
                Token type = reader.popExpect(Token.TokenType.TOKEN);
                Token name = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                RecordComponentVisitor rcv = visitor.visitRecordComponent(name.value, type.value, sig);
                lastAnnotationVisitor = new AnnotationVisitorSupplier() {
                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                        return rcv.visitAnnotation(desc, token == null || !token.value.contains("invisible"));
                    }

                    @Override
                    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
                        return rcv.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
                    }

                    @Override
                    public void visitEnd() {
                        rcv.visitEnd();
                    }
                };
                continue;
            }
            Token innerClass = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("INNERCLASS"));
            if (innerClass != null) {
                Token name = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                Token outerName = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                Token innerName = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                visitor.visitInnerClass(name.value, outerName.value, innerName.value, accessFlags == null ? access : accessFlags);
                access = 0;
                accessFlags = null;
                continue;
            }
//            Token outerClass = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("OUTERCLASS"));
//            if (outerClass != null) {
//                Token name = reader.popNonCommentExpect(Token.TokenType.TOKEN);
//                visitor.visitOuterClass(name.value, null, null);
//            }
            // detect field/method
            Token type = reader.popNonCommentIf(e -> e.type == Token.TokenType.TOKEN);
            if (type != null) {
                if (type.value.equals("}")) {
                    break;
                }
                if (type.value.contains("(")) {
                    // method
                    lastAnnotationVisitor.visitEnd();
                    // split name and desc
                    int paren = type.value.lastIndexOf('(');
                    String name = type.value.substring(0, paren);
                    String desc = type.value.substring(paren);
                    Token thro = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("throws"));
                    List<Type> exceptions = new ArrayList<>();
                    if (thro != null) {
                        while (true) {
                            Token tk = reader.popIf(e -> e.type == Token.TokenType.TOKEN && !opcodes.containsKey(e.value.toUpperCase()) && !e.value.matches("L\\d+") && !SPECIAL_OPCODES.contains(e.value.toUpperCase()) && !e.value.startsWith("@"));
                            if (tk == null) {
                                break;
                            }
                            exceptions.add(Type.getObjectType(tk.value));
                        }
                    }
                    readMethodContent(visitor.visitMethod(accessFlags == null ? access : accessFlags, name, desc, signature, exceptions.stream().map(Type::getInternalName).toArray(String[]::new)), desc, (access & ACC_ABSTRACT) != 0, (classAccess & ACC_ANNOTATION) != 0);

                    lastAnnotationVisitor = new AnnotationVisitorSupplier() {
                        @Override
                        public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                            throw new UnsupportedOperationException();
                        }

                        @Override
                        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
                            throw new UnsupportedOperationException();
                        }
                    };
                    signature = null;
                    access = 0;
                    accessFlags = null;
                } else {
                    // field
                    lastAnnotationVisitor.visitEnd();
                    // get name
                    Token name = reader.popNonCommentExpect(Token.TokenType.TOKEN);
                    // check if has =
                    Token equals = reader.popIf(t -> t.type == Token.TokenType.TOKEN && t.value.equals("="));
                    Object value = null;
                    if (equals != null) {
                        // read value
                        Token tk = reader.popNonComment();
                        value = readPrimitive(tk, 0);
                    }
                    FieldVisitor fv = visitor.visitField(accessFlags == null ? access : accessFlags, name.value, type.value, signature, value);
                    lastAnnotationVisitor = new AnnotationVisitorSupplier() {
                        @Override
                        public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                            return fv.visitAnnotation(desc, token == null || !token.value.contains("invisible"));
                        }

                        @Override
                        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
                            return fv.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
                        }

                        @Override
                        public void visitEnd() {
                            fv.visitEnd();
                        }
                    };
                    signature = null;
                    access = 0;
                    accessFlags = null;
                }
            }
        }
        lastAnnotationVisitor.visitEnd();
    }

    private void readFooter(ClassVisitor visitor) throws IOException {
        reader.popNonCommentExpect(Token.TokenType.TOKEN, Set.of("}"));
        visitor.visitEnd();
    }

    interface AnnotationVisitorSupplier {
        AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException;
        AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException;

        default AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, Token token) throws IOException {
            throw new UnsupportedOperationException();
        }
        default void visitEnd() {}
    }
}

package xyz.wagyourtail.asmreader.file;

import org.objectweb.asm.*;
import org.objectweb.asm.util.Printer;
import xyz.wagyourtail.asmreader.token.Token;
import xyz.wagyourtail.asmreader.token.TokenReader;
import xyz.wagyourtail.asmreader.token.TokenType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.objectweb.asm.Opcodes.*;

public abstract class AbstractReader implements AnnotationVisitorSupplier {
    public static final Pattern CLASS_VERSION = Pattern.compile("^\\s*class\\s*version:?\\s*(?<major>\\d+)\\.(?<minor>\\d+).*", Pattern.CASE_INSENSITIVE);
    public static final Pattern SIGNATURE = Pattern.compile("^\\s*signature:?\\s*(?<signature>.*)", Pattern.CASE_INSENSITIVE);
    public static final Pattern COMPILED_FROM = Pattern.compile("^\\s*compiled\\s*from:?\\s*(?<compiledFrom>.*)", Pattern.CASE_INSENSITIVE);
    public static final Pattern ACCESS_FLAGS = Pattern.compile("^\\s*access\\s*flags:?\\s*0x(?<accessFlags>[0-9a-fA-F]+)", Pattern.CASE_INSENSITIVE);
    public static final Map<String, Integer> OPCODES = IntStream.range(0, Printer.OPCODES.length)
            .mapToObj(e -> Map.entry(Printer.OPCODES[e], e))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    public static final Map<String, Integer> FRAME_TYPES = Map.of(
            "T", TOP,
            "I", INTEGER,
            "F", FLOAT,
            "D", DOUBLE,
            "J", LONG,
            "N", NULL,
            "U", UNINITIALIZED_THIS
    );
    public static final Map<String, Integer> TYPES = IntStream.range(0, Printer.TYPES.length)
            .filter(e -> !Printer.TYPES[e].isEmpty())
            .mapToObj(e -> Map.entry(Printer.TYPES[e], e))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    public static final Set<String> SPECIAL_OPCODES = Set.of(
            "FRAME",
            "LINENUMBER",
            "LOCALVARIABLE",
            "MAXSTACK",
            "MAXLOCALS",
            "TRYCATCHBLOCK"
    );
    public static final Pattern ANNOTABLE_PARAMETER_COUNT = Pattern.compile("^\\s*annotable\\s*parameter\\s*count:?\\s*(?<count>\\d+)\\s*\\((?<invisible>invisible|visible)\\)", Pattern.CASE_INSENSITIVE);
    public static final Pattern HANDLE_KIND = Pattern.compile("^\\s*handle\\s*kind:?\\s*0x(?<kind>[\\da-fA-F]+)\\s*:\\s*(?<type>.*)", Pattern.CASE_INSENSITIVE);
    public static final Set<Integer> CLASS_TYPE_REF = Set.of(TypeReference.CLASS_TYPE_PARAMETER, TypeReference.CLASS_TYPE_PARAMETER_BOUND, TypeReference.CLASS_EXTENDS);
    private static final Map<String, Integer> ACCESS_MAP = Map.ofEntries(
            Map.entry("PUBLIC", Opcodes.ACC_PUBLIC),
            Map.entry("PRIVATE", Opcodes.ACC_PRIVATE),
            Map.entry("PROTECTED", Opcodes.ACC_PROTECTED),
            Map.entry("FINAL", Opcodes.ACC_FINAL),
            Map.entry("STATIC", Opcodes.ACC_STATIC),
            Map.entry("SYNCHRONIZED", Opcodes.ACC_SYNCHRONIZED),
            Map.entry("VOLATILE", Opcodes.ACC_VOLATILE),
            Map.entry("TRANSIENT", Opcodes.ACC_TRANSIENT),
            Map.entry("ABSTRACT", Opcodes.ACC_ABSTRACT),
            Map.entry("STRICTFP", Opcodes.ACC_STRICT),
            Map.entry("SYNTHETIC", Opcodes.ACC_SYNTHETIC),
            Map.entry("MANDATED", Opcodes.ACC_MANDATED)
    );
    protected final TokenReader reader;
    Set<Integer> RECORD_COMPONENT_TYPE_REF = Set.of(TypeReference.CLASS_TYPE_PARAMETER, TypeReference.CLASS_TYPE_PARAMETER_BOUND, TypeReference.CLASS_EXTENDS, TypeReference.FIELD);

    public AbstractReader(TokenReader reader) {
        this.reader = reader;
    }

    public static int getAccess(TokenReader lister) throws IOException {
        int access = 0;
        Token tk;
        while ((tk = lister.popIf(t -> t.type == TokenType.TOKEN && ACCESS_MAP.containsKey(t.value.toUpperCase()))) != null) {
            access |= ACCESS_MAP.get(tk.value.toUpperCase());
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

    protected String readSignature() throws IOException {
        Token tk = reader.popIf(t -> t.type == TokenType.COMMENT && SIGNATURE.matcher(t.value).find());
        if (tk == null) {
            return null;
        }
        Matcher m = SIGNATURE.matcher(tk.value);
        m.find();
        return m.group("signature");
    }

    protected Object readPrimitive(Token tk, int offset) throws IOException {
        if (tk.type == TokenType.STRING) {
            return tk.value.translateEscapes();
        }
        if (tk.type == TokenType.CHAR) {
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
        if (value.matches("-?\\d*\\.\\d+[FDfd]?")) {
            // float/double
            if (value.endsWith("F") || value.endsWith("f")) {
                return Float.parseFloat(value.substring(0, value.length() - 1));
            } else if (value.endsWith("D") || value.endsWith("d")) {
                return Double.parseDouble(value.substring(0, value.length() - 1));
            } else {
                return Double.parseDouble(value);
            }
        } else if (value.matches("-?\\d+[Ll]?")) {
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

    protected void readAnnotation(Token beginning, Map<Integer, Label> labels, AnnotationVisitorSupplier visitor) throws IOException {
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
        Token typeAnnotation = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals(":"));
        AnnotationVisitor av;
        if (typeAnnotation != null) {
            int typeRef = reverseTypeRef();
            TypePath typePath = reverseTypePath();
            Token lvAnnotation = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("["));
            if (lvAnnotation != null) {
                List<Label> start = new ArrayList<>();
                List<Label> end = new ArrayList<>();
                List<Integer> index = new ArrayList<>();
                do {
                    Token startL = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (!startL.value.matches("L\\d+")) {
                        reader.throwAtPos("Expected label");
                    }
                    start.add(labels.computeIfAbsent(Integer.parseInt(startL.value.substring(1)), e -> new Label()));
                    reader.popNonCommentExpect(TokenType.TOKEN, "-");
                    Token endL = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (!endL.value.matches("L\\d+")) {
                        reader.throwAtPos("Expected label");
                    }
                    end.add(labels.computeIfAbsent(Integer.parseInt(endL.value.substring(1)), e -> new Label()));
                    reader.popNonCommentExpect(TokenType.TOKEN, "-");
                    Token indexL = reader.popNonCommentExpect(TokenType.TOKEN);
                    if (!indexL.value.matches("\\d+")) {
                        reader.throwAtPos("Expected index");
                    }
                    index.add(Integer.parseInt(indexL.value));
                    reader.popNonCommentExpect(TokenType.TOKEN, "]");
                } while (reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("[")) != null);
                Token invis = reader.popIf(t -> t.type == TokenType.COMMENT);
                av = visitor.visitLocalVariableAnnotation(typeRef, typePath, start.toArray(new Label[0]), end.toArray(new Label[0]), index.stream().mapToInt(e -> e).toArray(), type.getDescriptor(), invis);
            } else {
                // read to see if we have an "invisible" comment
                Token invis = reader.popIf(t -> t.type == TokenType.COMMENT);
                av = visitor.visitTypeAnnotation(typeRef, typePath, type.getDescriptor(), invis);
            }
        } else {
            // read to see if we have an "invisible" comment
            Token invis = reader.popIf(t -> t.type == TokenType.COMMENT);
            av = visitor.visitAnnotation(type.getDescriptor(), invis);
        }
        String content = readAnnotationContent(sb.toString().trim(), av);
        if (!content.isEmpty()) {
            reader.throwAtPos("Unexpected content after annotation )");
        }
        av.visitEnd();
    }

    protected int reverseTypeRef() throws IOException {
        Token tk = reader.popNonCommentExpect(TokenType.TOKEN);
        String sv = tk.value;
        if (sv.endsWith(",")) {
            sv = sv.substring(0, sv.length() - 1);
        }
        TypeReference tr;
        switch (sv) {
            case "CLASS_TYPE_PARAMETER" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeParameterReference(TypeReference.CLASS_TYPE_PARAMETER, Integer.parseInt(param));
            }
            case "METHOD_TYPE_PARAMETER" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeParameterReference(TypeReference.METHOD_TYPE_PARAMETER, Integer.parseInt(param));
            }
            case "CLASS_EXTENDS" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newSuperTypeReference(Integer.parseInt(param));
            }
            case "CLASS_TYPE_PARAMETER_BOUND" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                String param1 = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeParameterBoundReference(TypeReference.CLASS_TYPE_PARAMETER_BOUND, Integer.parseInt(param), Integer.parseInt(param1));
            }
            case "METHOD_TYPE_PARAMETER_BOUND" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                String param1 = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeParameterBoundReference(TypeReference.METHOD_TYPE_PARAMETER_BOUND, Integer.parseInt(param), Integer.parseInt(param1));
            }
            case "FIELD" -> {
                tr = TypeReference.newTypeReference(TypeReference.FIELD);
            }
            case "METHOD_RETURN" -> {
                tr = TypeReference.newTypeReference(TypeReference.METHOD_RETURN);
            }
            case "METHOD_RECEIVER" -> {
                tr = TypeReference.newTypeReference(TypeReference.METHOD_RECEIVER);
            }
            case "METHOD_FORMAL_PARAMETER" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newFormalParameterReference(Integer.parseInt(param));
            }
            case "THROWS" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newExceptionReference(Integer.parseInt(param));
            }
            case "LOCAL_VARIABLE" -> {
                tr = TypeReference.newTypeReference(TypeReference.LOCAL_VARIABLE);
            }
            case "RESOURCE_VARIABLE" -> {
                tr = TypeReference.newTypeReference(TypeReference.RESOURCE_VARIABLE);
            }
            case "EXCEPTION_PARAMETER" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTryCatchReference(Integer.parseInt(param));
            }
            case "INSTANCEOF" -> {
                tr = TypeReference.newTypeReference(TypeReference.INSTANCEOF);
            }
            case "NEW" -> {
                tr = TypeReference.newTypeReference(TypeReference.NEW);
            }
            case "CONSTRUCTOR_REFERENCE" -> {
                tr = TypeReference.newTypeReference(TypeReference.CONSTRUCTOR_REFERENCE);
            }
            case "METHOD_REFERENCE" -> {
                tr = TypeReference.newTypeReference(TypeReference.METHOD_REFERENCE);
            }
            case "CAST" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeArgumentReference(TypeReference.CAST, Integer.parseInt(param));
            }
            case "CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeArgumentReference(TypeReference.CONSTRUCTOR_INVOCATION_TYPE_ARGUMENT, Integer.parseInt(param));
            }
            case "METHOD_INVOCATION_TYPE_ARGUMENT" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeArgumentReference(TypeReference.METHOD_INVOCATION_TYPE_ARGUMENT, Integer.parseInt(param));
            }
            case "CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeArgumentReference(TypeReference.CONSTRUCTOR_REFERENCE_TYPE_ARGUMENT, Integer.parseInt(param));
            }
            case "METHOD_REFERENCE_TYPE_ARGUMENT" -> {
                String param = reader.popNonCommentExpect(TokenType.TOKEN).value;
                if (param.endsWith(",")) param = param.substring(0, param.length() - 1);
                tr = TypeReference.newTypeArgumentReference(TypeReference.METHOD_REFERENCE_TYPE_ARGUMENT, Integer.parseInt(param));
            }
            default -> {
                reader.throwAtPos("Unknown type reference: " + tk.value);
                // wont reach, previous function always throws
                tr = null;
            }
        }
        assert tr != null;
        return tr.getValue();
    }

    protected TypePath reverseTypePath() throws IOException {
        Token tk = reader.popNonCommentExpect(TokenType.TOKEN);
        if (tk.value.equals("null")) {
            return null;
        }
        return TypePath.fromString(tk.value);
    }

    protected String readAnnotationContent(String annotation, AnnotationVisitor visitor) throws IOException {
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

    protected String readAnnotationArray(String annotationContent, AnnotationVisitor visitor) throws IOException {
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

    protected String readAnnotationValue(String annotationContent, String name, AnnotationVisitor visitor) throws IOException {
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
            int end = indexOfFirst(annotationContent, ',', '}', ')');
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
                    int end2 = indexOfFirst(annotationContent.substring(end + 1), ')', ',', '}');
                    if (end2 == -1) {
                        reader.throwAtPos("Expected ',', '}' or ')' in annotation", -annotationContent.length());
                    }
                    value += annotationContent.substring(end, end + end2 + 1);
                    end = end + end2 + 1;
                }
                TokenType type = TokenType.TOKEN;
                if (value.startsWith("\"")) {
                    type = TokenType.STRING;
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'")) {
                    type = TokenType.CHAR;
                    value = value.substring(1, value.length() - 1);
                }
                visitor.visit(name, readPrimitive(new Token(value, type), -annotationContent.length()));
            }
            return annotationContent.substring(end);
        }
    }
}

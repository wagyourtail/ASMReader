package xyz.wagyourtail.asm.compiler.file;

import org.objectweb.asm.*;
import xyz.wagyourtail.asm.compiler.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ASMReader {
    private final TokenReader reader;

    public ASMReader(TokenReader reader) {
        this.reader = reader;
    }

    private static final Pattern CLASS_VERSION = Pattern.compile("^\\s*class\\s*version:?\\s*(?<major>\\d+)\\.(?<minor>\\d+).*");

    private static final Pattern CLASS_SIGNATURE = Pattern.compile("^\\s*signature:?\\s*(?<signature>.*)");

    public void accept(ClassVisitor visitor) throws IOException {
        readHeader(visitor);
        readContent(visitor);
        readFooter(visitor);
    }

    private void readHeader(ClassVisitor visitor) throws IOException {
        int version = 0;
        String signature = null;
        while (reader.peek().type == Token.TokenType.COMMENT) {
            Token tk = reader.pop();
            Matcher m = CLASS_VERSION.matcher(tk.value);
            if (m.find()) {
                version = Integer.parseInt(m.group("minor")) << 16 | Integer.parseInt(m.group("major"));
                continue;
            }
            m = CLASS_SIGNATURE.matcher(tk.value);
            if (m.find()) {
                signature = m.group("signature");
            }
        }

        int access = Util.getAccess(reader);

        String classTk = reader.popNonCommentExpect(Token.TokenType.TOKEN, Set.of("class", "interface", "enum"));
        if (classTk.equals("interface")) {
            access |= Opcodes.ACC_INTERFACE;
        } else if (classTk.equals("enum")) {
            access |= Opcodes.ACC_ENUM;
        }

        Token nameTk = reader.popNonCommentExpect(Token.TokenType.TOKEN);
        Type type = Type.getObjectType(nameTk.value);

        Token extendsTk = reader.popNonCommentIf(e -> e.type == Token.TokenType.TOKEN && e.value.equals("extends"));
        Type superType;
        if (extendsTk != null) {
            Token superTk = reader.popExpect(Token.TokenType.TOKEN);
            if (superTk.value.equals("java/lang/Record")) {
                access |= Opcodes.ACC_RECORD;
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
        visitor.visit(version, access, type.getInternalName(), signature, superType.getInternalName(), interfaces.stream().map(Type::getInternalName).toArray(String[]::new));
    }

    private void readAnnotation(Token beginning, BiFunction<String, Boolean, AnnotationVisitor> visitor) throws IOException {
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
        // read to see if we have an "invisible" comment
        Token invisibleComment = reader.popIf(t -> t.type == Token.TokenType.COMMENT && t.value.trim().equals("invisible"));
        AnnotationVisitor av = visitor.apply(type.getDescriptor(), invisibleComment == null);
        String content = readAnnotationContent(sb.toString().trim(), av);
        if (!content.isEmpty()) {
            reader.throwAtPos("Unexpected content after annotation )");
        }
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

    private int indexOfFirst(String val, char... c) {
        int min = -1;
        for (char ch : c) {
            int i = val.indexOf(ch);
            if (i != -1 && (min == -1 || i < min)) {
                min = i;
            }
        }
        return min;
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
            } else if (value.contains(".")) {
                // float/double
                if (value.endsWith("F")) {
                    visitor.visit(name, Float.parseFloat(value.substring(0, value.length() - 1)));
                } else if (value.endsWith("D")) {
                    visitor.visit(name, Double.parseDouble(value.substring(0, value.length() - 1)));
                } else {
                    visitor.visit(name, Double.parseDouble(value));
                }
            } else if (value.matches("\\d+L?")) {
                // int/long
                if (value.endsWith("L")) {
                    visitor.visit(name, Long.parseLong(value.substring(0, value.length() - 1)));
                } else {
                    visitor.visit(name, Integer.parseInt(value));
                }
            } else if (value.equals("true")) {
                // boolean
                visitor.visit(name, true);
            } else if (value.equals("false")) {
                // boolean
                visitor.visit(name, false);
            } else if (value.equals("null")) {
                // null
                visitor.visit(name, null);
            } else if (value.startsWith("'")) {
                // unescape char
                String val = value.substring(1, value.length() - 1);
                val = val.translateEscapes();
                if (val.length() != 1) {
                    reader.throwAtPos("Expected single char", -annotationContent.length());
                }
                visitor.visit(name, val.charAt(0));
            } else {
                reader.throwAtPos("Unknown annotation value: " + value, -annotationContent.length());
            }
            return annotationContent.substring(end);
        }
    }

    private void readContent(ClassVisitor visitor) throws IOException {
        String signature;
        FieldVisitor lastField = null;
        while (!reader.peekExpect(Token.TokenType.TOKEN, "}")) {
            Token annotation = reader.popNonCommentIf(t -> t.type == Token.TokenType.TOKEN && t.value.startsWith("@"));
            if (annotation != null) {
                if (lastField == null) {
                    readAnnotation(annotation, visitor::visitAnnotation);
                } else {
                    readAnnotation(annotation, lastField::visitAnnotation);
                }
            }

        }

        if (lastField != null) {
            lastField.visitEnd();
        }
    }

    private void readFooter(ClassVisitor visitor) throws IOException {
        reader.popNonCommentExpect(Token.TokenType.TOKEN, Set.of("}"));
        visitor.visitEnd();
    }
}

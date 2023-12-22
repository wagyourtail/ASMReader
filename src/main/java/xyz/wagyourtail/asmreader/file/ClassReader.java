package xyz.wagyourtail.asmreader.file;

import org.objectweb.asm.*;
import xyz.wagyourtail.asmreader.token.Token;
import xyz.wagyourtail.asmreader.token.TokenReader;
import xyz.wagyourtail.asmreader.token.TokenType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

import static org.objectweb.asm.Opcodes.*;

public class ClassReader extends AbstractReader {
    ClassVisitor visitor;

    public ClassReader(TokenReader reader) {
        super(reader);
    }

    public void accept(ClassVisitor visitor) throws IOException {
        if (this.visitor != null) throw new IllegalStateException("Already accepted");
        this.visitor = visitor;
        int access = readHeader();
        readContent(access);
        readFooter();
    }

    private int readHeader() throws IOException {
        int version = 0;
        String signature = null;
        int access = 0;
        while (reader.peek().type == TokenType.COMMENT) {
            Token tk = reader.pop();
            Matcher m = AbstractReader.CLASS_VERSION.matcher(tk.value);
            if (m.find()) {
                version = Integer.parseInt(m.group("minor")) << 16 | Integer.parseInt(m.group("major"));
                continue;
            }
            m = AbstractReader.SIGNATURE.matcher(tk.value);
            if (m.find()) {
                signature = m.group("signature");
            }
            if (tk.value.trim().equals("DEPRECATED")) {
                access |= ACC_DEPRECATED;
            }
        }

        access |= AbstractReader.getAccess(reader);

        String classTk = reader.popNonCommentExpect(TokenType.TOKEN, Set.of("class", "interface", "enum", "@interface"));
        switch (classTk) {
            case "interface" -> access |= ACC_INTERFACE;
            case "enum" -> access |= ACC_ENUM;
            case "@interface" -> access |= ACC_ANNOTATION | ACC_INTERFACE;
        }

        Token nameTk = reader.popNonCommentExpect(TokenType.TOKEN);
        Type type = Type.getObjectType(nameTk.value);

        Token extendsTk = reader.popNonCommentIf(e -> e.type == TokenType.TOKEN && e.value.equals("extends"));
        Type superType;
        if (extendsTk != null) {
            Token superTk = reader.popExpect(TokenType.TOKEN);
            if (superTk.value.equals("java/lang/Record")) {
                access |= ACC_RECORD;
            }
            superType = Type.getObjectType(superTk.value);
        } else {
            superType = Type.getObjectType("java/lang/Object");
        }

        Token implementsTk = reader.popNonCommentIf(e -> e.type == TokenType.TOKEN && e.value.equals("implements"));
        List<Type> interfaces = new ArrayList<>();
        if (implementsTk != null) {
            while (true) {
                Token tk = reader.popNonCommentIf(t -> t.type == TokenType.TOKEN && !t.value.equals("{"));
                if (tk == null) {
                    break;
                }
                interfaces.add(Type.getObjectType(tk.value));
            }
        }

        reader.popNonCommentExpect(TokenType.TOKEN, "{");
        // if not abstract or interface, | ACC_SUPER
        if ((access & ACC_ABSTRACT) == 0 && (access & ACC_INTERFACE) == 0) {
            access |= ACC_SUPER;
        }
        visitor.visit(version, access, type.getInternalName(), signature, superType.getInternalName(), interfaces.stream().map(Type::getInternalName).toArray(String[]::new));
        return access;
    }

    private void readContent(int classAccess) throws IOException {
        String signature = null;
        AnnotationVisitorSupplier lastAnnotationVisitor = this;
        int access = 0;
        Integer accessFlags = null;
        while (!reader.peekExpect(TokenType.TOKEN, "}")) {
            // comments
            Token sourceComment = reader.popIf(t -> t.type == TokenType.COMMENT && AbstractReader.COMPILED_FROM.matcher(t.value).find());
            if (sourceComment != null) {
                Matcher m = AbstractReader.COMPILED_FROM.matcher(sourceComment.value);
                if (m.find()) {
                    visitor.visitSource(m.group("compiledFrom"), null);
                }
                continue;
            }
            // deprecated comment
            Token deprecatedComment = reader.popIf(t -> t.type == TokenType.COMMENT && t.value.trim().equals("DEPRECATED"));
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
            Token accessComment = reader.popIf(t -> t.type == TokenType.COMMENT && AbstractReader.ACCESS_FLAGS.matcher(t.value).find());
            if (accessComment != null) {
                Matcher m = AbstractReader.ACCESS_FLAGS.matcher(accessComment.value);
                if (m.find()) {
                    accessFlags = Integer.parseInt(m.group("accessFlags"), 16);
                }
                continue;
            }
            // tokens
            reader.popNonCommentIf(e -> false);
            int a = AbstractReader.getAccess(reader);
            Token enumm = reader.popIf(e -> e.type == TokenType.TOKEN && e.value.equalsIgnoreCase("ENUM"));
            if (enumm != null) {
                access |= ACC_ENUM;
            }
            access |= a;
            if (a != 0) {
                continue;
            }
            Token annotation = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.startsWith("@"));
            if (annotation != null) {
                if (access != 0 || accessFlags != null) {
                    reader.throwAtPos("Didn't expect access modifier with annotation");
                }
                readAnnotation(annotation, null, lastAnnotationVisitor);
                continue;
            }
            Token nestMember = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("NESTMEMBER"));
            if (nestMember != null) {
                if (access != 0 || accessFlags != null) {
                    reader.throwAtPos("Didn't expect access modifier with NESTMEMBER");
                }
                Token tk = reader.popNonCommentExpect(TokenType.TOKEN);
                visitor.visitNestMember(tk.value);
                continue;
            }
            Token nestHost = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("NESTHOST"));
            if (nestHost != null) {
                if (access != 0) {
                    reader.throwAtPos("Didn't expect access modifier with NESTHOST");
                }
                Token tk = reader.popExpect(TokenType.TOKEN);
                visitor.visitNestHost(tk.value);
                continue;
            }
            Token recordComponent = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("RECORDCOMPONENT"));
            if (recordComponent != null) {
                lastAnnotationVisitor.visitEnd();
                if (access != 0 || accessFlags != null) {
                    reader.throwAtPos("Didn't expect access modifier with RECORDCOMPONENT");
                }
                String sig = null;
                while (reader.peekExpect(TokenType.COMMENT) != null) {
                    String s = readSignature();
                    if (s != null) {
                        sig = s;
                    }
                }
                Token type = reader.popExpect(TokenType.TOKEN);
                Token name = reader.popNonCommentExpect(TokenType.TOKEN);
                RecordComponentReader recordComponentReader = new RecordComponentReader(reader);
                recordComponentReader.accept(visitor.visitRecordComponent(name.value, type.value, sig));
                lastAnnotationVisitor = recordComponentReader;
                continue;
            }
            Token innerClass = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("INNERCLASS"));
            if (innerClass != null) {
                Token name = reader.popNonCommentExpect(TokenType.TOKEN);
                Token outerName = reader.popNonCommentExpect(TokenType.TOKEN);
                Token innerName = reader.popNonCommentExpect(TokenType.TOKEN);
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
            Token type = reader.popNonCommentIf(e -> e.type == TokenType.TOKEN);
            if (type != null) {
                if (type.value.equals("}")) {
                    if (access != 0 || accessFlags != null) {
                        reader.throwAtPos("Didn't expect access modifier with }");
                    }
                    break;
                }
                if (type.value.contains("(")) {
                    // method
                    lastAnnotationVisitor.visitEnd();
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
                    MethodReader methodReader = new MethodReader(reader);
                    methodReader.accept(visitor.visitMethod(accessFlags == null ? access : accessFlags, name, desc, signature, exceptions.stream().map(Type::getInternalName).toArray(String[]::new)), (access & ACC_ABSTRACT) != 0, (classAccess & ACC_ANNOTATION) != 0);
                    methodReader.visitEnd();
                    lastAnnotationVisitor = AnnotationVisitorSupplier.nullSupplier(() -> {
                        methodReader.reader.throwAtPos("Unexpected annotation after method");
                        return null;
                    });
                    signature = null;
                    access = 0;
                    accessFlags = null;
                } else {
                    // field
                    lastAnnotationVisitor.visitEnd();
                    // get name
                    Token name = reader.popNonCommentExpect(TokenType.TOKEN);
                    // check if has =
                    Token equals = reader.popIf(t -> t.type == TokenType.TOKEN && t.value.equals("="));
                    Object value = null;
                    if (equals != null) {
                        // read value
                        Token tk = reader.popNonComment();
                        if (type.value.equals("J")) {
                            if (!tk.value.endsWith("L")) tk = new Token(tk.value + "L", TokenType.TOKEN);
                        }
                        if (type.value.equals("F")) {
                            if (!tk.value.endsWith("F")) tk = new Token(tk.value + "F", TokenType.TOKEN);
                        }
                        if (type.value.equals("D")) {
                            if (!tk.value.endsWith("D")) tk = new Token(tk.value + "D", TokenType.TOKEN);
                        }
                        // handle condy if/when https://openjdk.org/jeps/8209964 is merged
                        value = readPrimitive(tk, 0);
                    }
                    FieldReader fieldReader = new FieldReader(reader);
                    fieldReader.accept(visitor.visitField(accessFlags == null ? access : accessFlags, name.value, type.value, signature, value));
                    lastAnnotationVisitor = fieldReader;
                    signature = null;
                    access = 0;
                    accessFlags = null;
                }
            }
        }
        lastAnnotationVisitor.visitEnd();
    }

    private void readFooter() throws IOException {
        reader.popNonCommentExpect(TokenType.TOKEN, Set.of("}"));
        visitor.visitEnd();
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
        return visitor.visitAnnotation(desc, token == null || !token.value.contains("invisible"));
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
        TypeReference ref = new TypeReference(typeRef);
        if (!AbstractReader.CLASS_TYPE_REF.contains(ref.getSort())) reader.throwAtPos("Unexpected type ref " + ref);
        return visitor.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
    }
}

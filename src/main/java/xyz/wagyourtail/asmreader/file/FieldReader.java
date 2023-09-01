package xyz.wagyourtail.asmreader.file;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.TypePath;
import xyz.wagyourtail.asmreader.token.Token;
import xyz.wagyourtail.asmreader.token.TokenReader;

import java.io.IOException;

public class FieldReader extends AbstractReader {
    FieldVisitor visitor;

    public FieldReader(TokenReader reader) {
        super(reader);
    }

    public void accept(FieldVisitor visitor) {
        if (this.visitor != null) throw new IllegalStateException("already accepted");
        this.visitor = visitor;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
        return visitor.visitAnnotation(desc, token == null || !token.value.contains("invisible"));
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
        return visitor.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
    }

    @Override
    public void visitEnd() {
        visitor.visitEnd();
    }
}

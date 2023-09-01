package xyz.wagyourtail.asmreader.file;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.RecordComponentVisitor;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.TypeReference;
import xyz.wagyourtail.asmreader.token.Token;
import xyz.wagyourtail.asmreader.token.TokenReader;

import java.io.IOException;

public class RecordComponentReader extends AbstractReader {
    RecordComponentVisitor visitor;

    public RecordComponentReader(TokenReader reader) {
        super(reader);
    }

    public void accept(RecordComponentVisitor visitor) {
        if (this.visitor != null) throw new IllegalStateException("already accepted");
        this.visitor = visitor;
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
        return visitor.visitAnnotation(desc, token == null || !token.value.contains("invisible"));
    }

    @Override
    public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
        TypeReference typeReference = new TypeReference(typeRef);
        if (!RECORD_COMPONENT_TYPE_REF.contains(typeReference.getSort())) {
            throw new IllegalArgumentException("Invalid type reference sort 0x" + Integer.toString(typeReference.getSort(), 16));
        }
        return visitor.visitTypeAnnotation(typeRef, typePath, desc, token == null || !token.value.contains("invisible"));
    }

    @Override
    public void visitEnd() {
        visitor.visitEnd();
    }
}

package xyz.wagyourtail.asmreader.file;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.TypePath;
import xyz.wagyourtail.asmreader.iofunction.IOSupplier;
import xyz.wagyourtail.asmreader.token.Token;

import java.io.IOException;

interface AnnotationVisitorSupplier {
    static AnnotationVisitorSupplier nullSupplier(IOSupplier<Void> exceptionConsumer) {
        return new AnnotationVisitorSupplier() {
            @Override
            public AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException {
                exceptionConsumer.get();
                return null;
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException {
                exceptionConsumer.get();
                return null;
            }
        };
    }

    AnnotationVisitor visitAnnotation(String desc, Token token) throws IOException;

    AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String desc, Token token) throws IOException;

    default AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String desc, Token token) throws IOException {
        throw new UnsupportedOperationException();
    }

    default void visitEnd() {
    }
}

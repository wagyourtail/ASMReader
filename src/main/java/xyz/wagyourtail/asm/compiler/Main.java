package xyz.wagyourtail.asm.compiler;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import xyz.wagyourtail.asm.compiler.file.ASMReader;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class Main {


    public static void compileJavasm(String asm, ClassVisitor visitor) throws IOException {
        TokenReader reader = new TokenReader(new StringReader(asm));
        ASMReader asmReader = new ASMReader(reader);
        asmReader.accept(visitor);
    }

    public static void main(String[] args) throws IOException {
        // args[0] = input.javasm
        // args[1] = output.class
        // args[2] = classpath
        ClassLoader loader;
        if (args.length == 3) {
            loader = new URLClassLoader(Arrays.stream(args[2].split(File.pathSeparator)).map(Path::of).map(Path::toUri).map(e -> {
                try {
                    return e.toURL();
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }).toArray(URL[]::new));
        } else {
            loader = Main.class.getClassLoader();
        }

        // read in input.javasm
        try (TokenReader reader = new TokenReader(Files.newBufferedReader(Path.of(args[0])))) {
            ASMReader asmReader = new ASMReader(reader);
            ClassWriter writer = new ClassWriter(0) {
                @Override
                protected ClassLoader getClassLoader() {
                    return loader;
                }
            };
            asmReader.accept(writer);
            Files.write(Path.of(args[1]), writer.toByteArray());
        }

    }

}

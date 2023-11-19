package xyz.wagyourtail.asmreader;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;
import xyz.wagyourtail.asmreader.file.ClassReader;
import xyz.wagyourtail.asmreader.file.MethodReader;
import xyz.wagyourtail.asmreader.iofunction.IOConsumer;
import xyz.wagyourtail.asmreader.token.TokenReader;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

public class Main {


    public static void compileJavasm(String asm, ClassVisitor visitor) throws IOException {
        TokenReader reader = new TokenReader(new StringReader(asm));
        ClassReader asmReader = new ClassReader(reader);
        asmReader.accept(visitor);
    }

    public static void compileJavasmMethod(String method, ClassVisitor visitor) throws IOException {
        TokenReader reader = new TokenReader(new StringReader(method));
        MethodReader asmReader = new MethodReader(reader);
        asmReader.acceptWithHeader(visitor::visitMethod);
    }

    public static void compileJavasmMethod(String method, MethodNode visitor) throws IOException {
        TokenReader reader = new TokenReader(new StringReader(method));
        MethodReader asmReader = new MethodReader(reader);
        asmReader.acceptWithHeader((access, name, descriptor, signature, exceptions) -> {
            visitor.access = access;
            visitor.name = name;
            visitor.desc = descriptor;
            visitor.signature = signature;
            visitor.exceptions = new ArrayList<>(Arrays.asList(exceptions));
            return visitor;
        });
    }

    public static void main(String[] args) throws IOException {
        ArgHandler argHandler = new ArgHandler();
        ArgHandler.Arg input = argHandler.arg("Input", "--input", "-i");
        ArgHandler.Arg output = argHandler.arg("Output", "--output", "-o");
        ArgHandler.Arg disassemble = argHandler.flag("Disassemble", "--disassemble", "-d");
        ArgHandler.Arg classpath = argHandler.arg("Classpath", "--classpath", "-cp");
        Map<ArgHandler.Arg, Integer> parsed = argHandler.parse(args);
        if (!parsed.containsKey(input)) {
            argHandler.printUsage();
            throw new IllegalArgumentException("Missing input");
        }
        Path inputPath = Path.of(input.value(args, parsed.get(input)));
        if (!Files.exists(inputPath)) {
            throw new IllegalArgumentException("Input file \"" + inputPath + "\" does not exist");
        }
        if (parsed.containsKey(disassemble)) {
            try (InputStream is = Files.newInputStream(inputPath)) {
                org.objectweb.asm.ClassReader reader = new org.objectweb.asm.ClassReader(is);
                String value = classToTextify(e -> reader.accept(e, 0));
                if (parsed.containsKey(output)) {
                    Path outputPath = Path.of(output.value(args, parsed.get(output)));
                    Files.writeString(outputPath, value, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    System.out.println(value);
                }
            }
        } else {
            ClassLoader loader;
            if (parsed.containsKey(classpath)) {
                loader = new URLClassLoader(Arrays.stream(classpath.value(args, parsed.get(classpath)).split(File.pathSeparator)).map(Path::of).map(Path::toUri).map(e -> {
                    try {
                        return e.toURL();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }).toArray(URL[]::new), Main.class.getClassLoader());
            } else {
                loader = Main.class.getClassLoader();
            }

            // read in input.javasm
            try (TokenReader reader = new TokenReader(Files.newBufferedReader(inputPath))) {
                ClassReader asmReader = new ClassReader(reader);
                ClassWriter writer = new ClassWriter(0) {
                    @Override
                    protected ClassLoader getClassLoader() {
                        return loader;
                    }
                };
                asmReader.accept(writer);
                Path outputPath = Path.of(output.value(args, parsed.get(output)));
                Files.write(outputPath, writer.toByteArray(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        }

    }

    public static String classToTextify(IOConsumer<ClassVisitor> visitor) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter pw = new PrintWriter(baos);
        TraceClassVisitor traceClassVisitor = new TraceClassVisitor(null, new DeterministicTextifier(), pw);
        visitor.accept(traceClassVisitor);
        return baos.toString();
    }

}

package xyz.wagyourtail.asmreader;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public class ArgHandler {
    public final Set<Arg> args = new LinkedHashSet<>();

    public ArgHandler() {
    }

    public void printUsage() {
        System.out.println("Usage:");
        for (Arg arg : args) {
            // first name
            System.out.print("  ");
            System.out.print(arg.names[0]);
            // desc
            System.out.print(" - ");
            System.out.println(arg.desc);
            // other names
            for (int i = 1; i < arg.names.length; i++) {
                System.out.print("    ");
                System.out.println(arg.names[i]);
            }
        }
    }

    public Arg flag(String desc, String... names) {
        Arg arg = new Arg(names, desc, 1);
        args.add(arg);
        return arg;
    }

    public Arg arg(String desc, String... names) {
        Arg arg = new Arg(names, desc, 2);
        args.add(arg);
        return arg;
    }

    public Map<Arg, Integer> parse(String[] args) {
        Map<Arg, Integer> parsed = new HashMap<>();
        for (int i = 0; i < args.length; ) {
            boolean flag = true;
            outer:
            for (Arg arg : this.args) {
                for (String name : arg.names) {
                    if (name.equals(args[i])) {
                        parsed.put(arg, i);
                        i += arg.len;
                        flag = false;
                        break outer;
                    }
                }
            }
            // else
            if (flag) {
                printUsage();
                throw new IllegalArgumentException("Unknown argument: " + args[i]);
            }
        }
        return parsed;
    }

    public record Arg(String[] names, String desc, int len) {

        public String[] values(String[] args, int index) {
            if (len > 2) {
                String[] values = new String[len - 1];
                System.arraycopy(args, index + 1, values, 0, len - 1);
                return values;
            } else if (len > 1) {
                return new String[]{args[index + 1]};
            } else {
                return new String[0];
            }
        }

        public String value(String[] args, int index) {
            if (len != 2) throw new IllegalStateException("len != 2");
            return args[index + 1];
        }

        public int intValue(String[] args, int index) {
            return Integer.parseInt(value(args, index));
        }

        public long longValue(String[] args, int index) {
            return Long.parseLong(value(args, index));
        }

        public float floatValue(String[] args, int index) {
            return Float.parseFloat(value(args, index));
        }

        public double doubleValue(String[] args, int index) {
            return Double.parseDouble(value(args, index));
        }

    }
}

package xyz.wagyourtail.asm.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.regex.Matcher;

public class TokenReader {
    private final BufferedReader in;
    private String current;

    public TokenReader(Reader in) {
        if (in instanceof BufferedReader) {
            this.in = (BufferedReader) in;
        } else {
            this.in = new BufferedReader(in);
        }
    }

    public void accept(TokenVisitor visitor) throws IOException {
        outer: while (true) {
            while (current == null || current.isBlank()) {
                current = in.readLine();
                if (current == null) {
                    break outer;
                }
            }
            current = current.stripLeading();
            if (current.startsWith("\"")) {
                // read string
                String ret = current.substring(1);
                Matcher m = STRING_CHAR.matcher(ret);
                if (!m.find()) {
                    throw new IOException("expected end of string");
                }
                current = ret.substring(m.end());
                return "\"" + ret.substring(0, m.end());
            }
            if (current.startsWith("//")) {
                if (skipComments) {
                    current = null;
                    return readCol();
                } else {
                    String ret = current;
                    current = null;
                    return ret;
                }
            }
            if (current.startsWith("/*")) {
                // find */
                int end = current.indexOf("*/");
                if (end == -1) {
                    throw new IOException("Currently don't support multiline comments!!!");
                } else {
                    if (skipComments) {
                        current = current.substring(end + 2);
                        return readCol();
                    } else {
                        String ret = current.substring(0, end + 2);
                        current = current.substring(end + 2);
                        return ret;
                    }
                }
            }
            int end = current.indexOf(' ');
            if (end == -1) {
                String ret = current;
                current = null;
                return ret;
            }
            String ret = current.substring(0, end);
            int hasQuote = ret.indexOf("\"");
            if (hasQuote != -1) {
                ret = ret.substring(0, hasQuote);
            }
            int hasComment2 = ret.indexOf("/*");
            if (hasComment2 != -1) {
                ret = ret.substring(0, hasComment2);
            }
            int hasComment = ret.indexOf("//");
            if (hasComment != -1) {
                ret = ret.substring(0, hasComment);
            }
            current = current.substring(ret.length());
            return ret;
        }
    }
}

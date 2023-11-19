package xyz.wagyourtail.asmreader.token;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenReader implements AutoCloseable {
    public static final Pattern STRING_CHAR = Pattern.compile("(?:(?<=[^\\\\])|^)((?:\\\\{2})*)\"");
    public static final Pattern CHAR_CHAR = Pattern.compile("(?:(?<=[^\\\\])|^)((?:\\\\{2})*)'");
    private final BufferedReader in;
    private int line = 0;
    private int column = 0;
    private String fullLine;
    private String current;
    private Token last;
    private Token next;

    public TokenReader(Reader in) {
        if (in instanceof BufferedReader) {
            this.in = (BufferedReader) in;
        } else {
            this.in = new BufferedReader(in);
        }
    }

    protected Token readNext() throws IOException {
        if (last != null) {
            if (last.type == TokenType.EOF) {
                throw new IllegalStateException("Cannot read next token after EOF");
            }
            column += last.value.length();
        }
        while (current == null || current.isBlank()) {
            current = in.readLine();
            fullLine = current;
            line++;
            column = 0;
            if (current == null) {
                return new Token(null, TokenType.EOF);
            }
        }
        int l = current.length();
        current = current.stripLeading();
        column += l - current.length();
        if (current.startsWith("\"")) {
            // read string
            String ret = current.substring(1);
            Matcher m = STRING_CHAR.matcher(ret);
            if (!m.find()) {
                throwAtPos("expected end of string");
            }
            current = ret.substring(m.end());
            return new Token(ret.substring(0, m.end() - 1), TokenType.STRING);
        }
        if (current.startsWith("'")) {
            // read char
            String ret = current.substring(1);
            Matcher m = CHAR_CHAR.matcher(ret);
            if (!m.find()) {
                throwAtPos("expected end of char");
            }
            current = ret.substring(m.end());
            return new Token(ret.substring(0, m.end() - 1), TokenType.CHAR);
        }
        if (current.startsWith("//")) {
            String ret = current;
            current = null;
            return new Token(ret.substring(2), TokenType.COMMENT);
        }
        if (current.startsWith("/*")) {
            // find */
            int end = current.indexOf("*/");
            if (end == -1) {
                throwAtPos("Currently don't support multiline comments!!!");
            } else {
                String ret = current.substring(0, end);
                current = current.substring(end + 2);
                return new Token(ret.substring(2), TokenType.COMMENT);
            }
        }
        int end = current.indexOf(' ');
        String ret;
        if (end == -1) {
            ret = current;
        } else {
            ret = current.substring(0, end);
        }
        int hasQuote = ret.indexOf("\"");
        if (hasQuote != -1) {
            ret = ret.substring(0, hasQuote);
        }
        int hasChar = ret.indexOf("'");
        if (hasChar != -1) {
            ret = ret.substring(0, hasChar);
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
        return new Token(ret, TokenType.TOKEN);
    }

    public Token peek() throws IOException {
        if (next == null) next = readNext();
        return next;
    }

    public String peekExpect(TokenType type) throws IOException {
        Token tk = peek();
        if (tk.type != type) {
            return null;
        }
        return tk.value;
    }

    public boolean peekExpect(TokenType type, String value) throws IOException {
        Token tk = peek();
        return tk.type == type && value.equals(tk.value);
    }

    public String peekExpect(TokenType type, Set<String> value) throws IOException {
        Token tk = peek();
        if (tk.type != type) {
            return null;
        }
        if (!value.contains(tk.value)) {
            return null;
        }
        return tk.value;
    }

    public MatchResult peekExpect(TokenType type, Pattern pattern) throws IOException {
        Token tk = peek();
        if (tk.type != type) {
            return null;
        }
        Matcher m = pattern.matcher(tk.value);
        if (!m.matches()) {
            return null;
        }
        return m;
    }

    public boolean peekIf(Predicate<Token> predicate) throws IOException {
        return predicate.test(peek());
    }

    public Token pop() throws IOException {
        if (next == null) next = readNext();
        Token ret = next;
        last = next;
        next = null;
        return ret;
    }

    public Token popNonComment() throws IOException {
        while (peek().type == TokenType.COMMENT) {
            pop();
        }
        return pop();
    }

    public Token popIf(Predicate<Token> predicate) throws IOException {
        if (predicate.test(peek())) {
            return pop();
        }
        return null;
    }

    public Token popNonCommentIf(Predicate<Token> predicate) throws IOException {
        while (peek().type == TokenType.COMMENT) {
            pop();
        }
        return popIf(predicate);
    }

    public Token popExpect(TokenType type) throws IOException {
        Token tk = pop();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        return tk;
    }

    public Token popNonCommentExpect(TokenType type) throws IOException {
        Token tk = popNonComment();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        return tk;
    }

    public void popExpect(TokenType type, String value) throws IOException {
        Token tk = pop();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        if (!value.equals(tk.value)) {
            throwAtPos("Expected one of " + value + " got " + tk.value);
        }
    }

    public void popNonCommentExpect(TokenType type, String value) throws IOException {
        Token tk = popNonComment();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        if (!value.equals(tk.value)) {
            throwAtPos("Expected one of " + value + " got " + tk.value);
        }
    }

    public String popExpect(TokenType type, Set<String> value) throws IOException {
        Token tk = pop();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        if (!value.contains(tk.value)) {
            throwAtPos("Expected one of " + value + " got " + tk.value);
        }
        return tk.value;
    }

    public String popNonCommentExpect(TokenType type, Set<String> value) throws IOException {
        Token tk = popNonComment();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        if (!value.contains(tk.value)) {
            throwAtPos("Expected one of " + value + " got " + tk.value);
        }
        return tk.value;
    }

    public MatchResult popExpect(TokenType type, Pattern pattern) throws IOException {
        Token tk = pop();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        Matcher m = pattern.matcher(tk.value);
        if (!m.matches()) {
            throwAtPos("Expected pattern " + pattern + " got " + tk.value);
        }
        return m;
    }

    public MatchResult popNonCommentExpect(TokenType type, Pattern pattern) throws IOException {
        Token tk = popNonComment();
        if (tk.type != type) {
            throwAtPos("Expected " + type + " got " + tk.type);
        }
        Matcher m = pattern.matcher(tk.value);
        if (!m.matches()) {
            throwAtPos("Expected pattern " + pattern + " got " + tk.value);
        }
        return m;
    }

    public void throwAtPos(String msg) throws IOException {
        throwAtPos(msg, 0);
    }

    public void throwAtPos(String msg, int offset) throws IOException {
        throw new UnexpectedTokenException(msg, line, column + offset, fullLine, peek().value);
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public static class UnexpectedTokenException extends IOException {
        public final String msg;
        public final int line;
        public final int column;
        public final String fullLine;
        public final String token;

        public UnexpectedTokenException(String msg, int line, int column, String fullLine, String token) {
            super("Error at line " + line + ", column " + column + ": " + msg + "\n" + fullLine + "\n" + " ".repeat(column) + "^");
            this.msg = msg;
            this.line = line;
            this.column = column;
            this.fullLine = fullLine;
            this.token = token;
        }
    }

}

package xyz.wagyourtail.asm.compiler;

public class Token {
    public final String value;
    public final TokenType type;

    public Token(String token, TokenType type) {
        this.value = token;
        this.type = type;
    }

    public enum TokenType {
        TOKEN,
        COMMENT,
        STRING,
        EOF
    }
}

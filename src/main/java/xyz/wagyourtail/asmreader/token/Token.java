package xyz.wagyourtail.asmreader.token;

public class Token {
    public final String value;
    public final TokenType type;

    public Token(String token, TokenType type) {
        this.value = token;
        this.type = type;
    }

}

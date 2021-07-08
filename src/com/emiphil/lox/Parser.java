package com.emiphil.lox;

import java.util.ArrayList;
import java.util.List;

import static com.emiphil.lox.TokenType.*;

interface LeftAssociativeBinary<R> {
    R apply();
}

public class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.addAll(declaration());
        }

        return statements;
    }

    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    private void synchronize() {
        advance();

        while (!isAtEnd()) {
            if (previous().type == SEMICOLON) return;

            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }

            advance();
        }
    }

    private Token previous() {
        return tokens.get(current - 1);
    }

    private Token peek() {
        return tokens.get(current);
    }

    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    private Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean check (TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean match (TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }

        return false;
    }

    private Token consume(TokenType type, String message) {
        if (check(type)) return advance();

        throw error(peek(), message);
    }

    private List<Stmt> declaration() {
        try {
            if (match(VAR)) return varDeclaration();

            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    private List<Stmt> varDeclaration() {
        List<Stmt> statements = new ArrayList<>();
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Expr initializer = null;
        if (match(EQUAL)) {
            initializer = expression();
        }

        while (match(COMMA)) {
            statements.add(new Stmt.Var(name, initializer));
            name = consume(IDENTIFIER, "Expect variable name.");
            initializer = null;
            if (match(EQUAL)) {
                initializer = expression();
            }
        }
        consume(SEMICOLON, "Expect ';' after variable declaration.");

        statements.add(new Stmt.Var(name, initializer));
        return statements;
    }

    private List<Stmt> statement() {
        if (match(PRINT)) return printStatement();
        if (match(LEFT_BRACE)) {
            List<Stmt> block = new ArrayList<>();
            block.add(new Stmt.Block(block()));
            return block;
        }
        return expressionStatement();
    }

    private List<Stmt> printStatement() {
        List<Stmt> statements = new ArrayList<>();
        Expr value = expression();
        while (match(COMMA)) {
            statements.add(new Stmt.Print(value));
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after value.");

        statements.add(new Stmt.Print(value));
        return statements;
    }

    private List<Stmt> expressionStatement() {
        List<Stmt> statements = new ArrayList<>();
        Expr expr = expression();
        while (match(COMMA)) {
            statements.add(new Stmt.Expression(expr));
            expr = expression();
        };
        consume(SEMICOLON, "Expect ';' after expression.");

        statements.add(new Stmt.Expression(expr));
        return statements;
    }

    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            List<Stmt> line = declaration();
            if (line != null) {
                statements.addAll(line);
            }
        }

        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    private Expr expression() {
        return assignment();
    }

    private Expr leftAssociativeBinary(LeftAssociativeBinary<Expr> method, TokenType... types) {
        Expr expr = method.apply();

        while (match(types)) {
            Token operator = previous();
            Expr right = method.apply();
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    private Expr assignment() {
        Expr expr = equality();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr equality() {
        return leftAssociativeBinary(this::comparison, BANG_EQUAL, EQUAL_EQUAL);
    }

    private Expr comparison() {
        return leftAssociativeBinary(this::term, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL);
    }

    private Expr term() {
        return leftAssociativeBinary(this::factor, MINUS, PLUS);
    }

    private Expr factor() {
        return leftAssociativeBinary(this::unary, SLASH, STAR);
    }

    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }

        return primary();
    }

    private Expr primary() {
        if (match(FALSE)) return new Expr.Literal(false);
        if (match(TRUE)) return new Expr.Literal(true);
        if (match(NIL)) return new Expr.Literal(null);

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        // Error productions

        // Binary operator with no left hand expression
        if (match(PLUS, STAR, SLASH, BANG_EQUAL, EQUAL_EQUAL, GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token token = previous();
            expression();
            throw error(token, "Expect left hand operand on binary expression.");
        }

        throw error(peek(), "Expect expression.");
    }
}

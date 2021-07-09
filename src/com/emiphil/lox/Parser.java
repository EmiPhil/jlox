package com.emiphil.lox;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static com.emiphil.lox.TokenType.*;

interface Func<R> {
    R apply();
}

public class Parser {
    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            List<Stmt> declarationStatements = declaration();
            if (declarationStatements != null) {
                statements.addAll(declarationStatements);
            }
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

    private boolean check(TokenType type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean match(TokenType... types) {
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
            if (match(FUN)) return function("function");
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
        if (match(FOR)) return forStatement();
        if (match(IF)) return ifStatement();
        if (match(PRINT)) return printStatement();
        if (match(RETURN)) return returnStatement();
        if (match(WHILE)) return whileStatement();
        if (match(LEFT_BRACE)) {
            List<Stmt> block = new ArrayList<>();
            block.add(new Stmt.Block(block()));
            return block;
        }
        return expressionStatement();
    }

    private List<Stmt> asList(Stmt statement) {
        List<Stmt> statements = new ArrayList<>();
        statements.add(statement);
        return statements;
    }

    private List<Stmt> forStatement() {
        // we caramelize for loops to their equivalent while loops
        consume(LEFT_PAREN, "Expect '(' after 'for'.");

        List<Stmt> initializer;
        if (match(SEMICOLON)) initializer = null;
        else if (match(VAR)) initializer = varDeclaration();
        else initializer = expressionStatement();

        Expr condition = null;
        if (!check(SEMICOLON)) {
            condition = expression();
        }
        consume(SEMICOLON, "Expect ';' after loop condition");

        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");

        Stmt body = statement().get(0);

        if (increment != null) {
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }

        if (condition == null) condition = new Expr.Literal(true);
        body = new Stmt.While(condition, body);

        if (initializer != null) {
            initializer.add(body);
            body = new Stmt.Block(initializer);
        }

        return asList(body);
    }

    private List<Stmt> ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");

        Stmt thenBranch = statement().get(0);
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement().get(0);
        }

        return asList(new Stmt.If(condition, thenBranch, elseBranch));
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

    private List<Stmt> returnStatement() {
        Token keyword = previous();
        Expr value = null;
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return asList(new Stmt.Return(keyword, value));
    }

    private List<Stmt> whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after while condition.");
        Stmt body = statement().get(0);
        return asList(new Stmt.While(condition, body));
    }

    private List<Stmt> expressionStatement() {
        List<Stmt> statements = new ArrayList<>();
        Expr expr = expression();
        while (match(COMMA)) {
            statements.add(new Stmt.Expression(expr));
            expr = expression();
        }

        consume(SEMICOLON, "Expect ';' after expression.");

        statements.add(new Stmt.Expression(expr));
        return statements;
    }

    private List<Stmt> function(String kind) {
        Token name = null;
        if (kind.equals("method")) {
            // methods must provide a name
            name = consume(IDENTIFIER, "Expect " + kind + " name.");
        } else {
            if (match(IDENTIFIER)) {
                name = previous();
            }
            // otherwise this is an anonymous function so we leave the name null
            // later, we can set the "name" of the anonymous function to a hash of the params and body
        }

        consume(LEFT_PAREN, "Expect '(' after " + kind + " declaration.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters");
                }

                parameters.add(consume(IDENTIFIER, "Expect parameter name"));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters");

        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();

        if (name == null) {
            // This is an anonymous function, so set the name to be a hash of the parameters and body
            name = new Token(TokenType.IDENTIFIER, "lox_anon_" + parameters.hashCode() + "_" + body.hashCode(), null, previous().line);
        }

        return asList(new Stmt.Function(name, parameters, body));
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

    private Expr leftAssociativeBinary(Func<Expr> production, TokenType... types) {
        Expr expr = production.apply();
        while (match(types)) {
            Token operator = previous();
            Expr right = production.apply();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    private Expr leftAssociativeLogical(Func<Expr> production, TokenType... types) {
        Expr expr = production.apply();
        while (match(types)) {
            Token operator = previous();
            Expr right = production.apply();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    private Expr assignment() {
        Expr expr = or();

        if (match(EQUAL)) {
            Token equals = previous();
            Expr value = assignment();

            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            }

            //noinspection ThrowableNotThrown
            error(equals, "Invalid assignment target.");
        }

        return expr;
    }

    private Expr or() {
        return leftAssociativeLogical(this::and, OR);
    }

    private Expr and() {
        return leftAssociativeLogical(this::equality, AND);
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

        return call();
    }

    private Expr call() {
        Expr expr = primary();

        while (true) {
           if (match(LEFT_PAREN)) {
               expr = finishCall(expr);
           } else {
               break;
           }
        }

        return expr;
    }

    private Expr finishCall(Expr callee) {
      List<Expr> arguments = new ArrayList<>();
      if (!check(RIGHT_PAREN)) {
          do {
              // max argument limit in a given function
              if (arguments.size() >= 255) {
                  //noinspection ThrowableNotThrown
                  error(peek(), "Can't have more than 255 arguments.");
              }

              arguments.add(expression());
          } while (match(COMMA));
      }

      Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");

      return new Expr.Call(callee, paren, arguments);
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

        if (match(FUN)) {
            return new Expr.Statement(function("function").get(0));
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

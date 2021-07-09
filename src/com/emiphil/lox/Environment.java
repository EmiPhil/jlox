package com.emiphil.lox;

import java.util.HashMap;
import java.util.Map;

public class Environment {
    final Environment enclosing;
    private final Map<String, Object> values = new HashMap<>();
    private String latest = "";

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    void define(String name, Object value) {
        values.put(name, value);
        latest = name;
    }

    void assign(Token name, Object value) {
        // design decision: we do not allow implicit variable declarations
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }

        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    Object get(String lexeme) {
        if (values.containsKey(lexeme)) {
            return values.get(lexeme);
        }

        if (enclosing != null) return enclosing.get(lexeme);

        throw new RuntimeError(null, "Undefined variable '" + lexeme + "'.");
    }

    Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }

        if (enclosing != null) return enclosing.get(name);

        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    Object getLatest() {
        return get(latest);
    }
}

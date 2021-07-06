package com.emiphil.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        /*
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];
         */
        String outputDir = "src\\com\\emiphil\\lox";
        defineAst(outputDir, "Expr", Arrays.asList(
                "Binary   : Expr left, Token operator, Expr right",
                "Grouping : Expr expression",
                "Literal  : Object value",
                "Unary    : Token operator, Expr right"
        ));

        defineAst(outputDir, "Stmt", Arrays.asList(
                "Expression : Expr expression",
                "Print      : Expr expression"
        ));
    }

    private static String indent(int level) {
        return "    ".repeat(Math.max(0, level));
    }

    private static void defineAst(
            String outputDir, String baseName, List<String> types)
            throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("// file automatically generated by com.emiphil.tool > GenerateAst");
        writer.println();
        writer.println("package com.emiphil.lox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("abstract class " + baseName + " {");

        defineVisitor(writer, baseName, types);

        // The base accept() method, part of the visitor pattern
        writer.println(indent(1) + "abstract <R> R accept(Visitor<R> visitor);");
        writer.println();

        // The AST classes
        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }

        writer.println("}");
        writer.close();
    }

    private static void defineVisitor(
            PrintWriter writer, String baseName, List<String> types) {
        writer.println(indent(1) + "interface Visitor<R> {");

        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println(indent(2) + "R visit" + typeName + baseName + "(" + typeName + " " + baseName.toLowerCase() + ");");
        }

        writer.println(indent(1) + "}");
        writer.println();
    }

    private static void defineType(
            PrintWriter writer, String baseName,
            String className, String fieldList) {
        writer.println(indent(1) + "static class " + className + " extends " + baseName + " {");

        // Constructor
        writer.println(indent(2) + className + "(" + fieldList + ") {");

        // Store parameters in fields.
        String[] fields = fieldList.split(", ");
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println(indent(3) + "this." + name + " = " + name + ";");
        }

        writer.println(indent(2) + "}");

        // Visitor pattern
        writer.println();
        writer.println(indent(2) + "@Override");
        writer.println(indent(2) + "<R> R accept(Visitor<R> visitor) {");
        writer.println(indent(3) + "return visitor.visit" + className + baseName + "(this);");
        writer.println(indent(2) + "}");

        // Fields
        writer.println();
        for (String field : fields) {
            writer.println(indent(2) + "final " + field + ";");
        }

        writer.println(indent(1) + "}");
        writer.println();
    }
}
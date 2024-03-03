package pt.up.fe.comp2024.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2024.ast.Kind.*;

public class JmmSymbolTableBuilder {


    public static JmmSymbolTable build(JmmNode root) {

        var classDecl = root.getJmmChild(0);

        // prevent classDecl variable from being ImportDecl instead of ClassDecl
        for(JmmNode node: root.getChildren())
            if (Objects.equals(node.getKind(), "ClassDecl"))
                classDecl = node;

        JmmNode finalClassDecl = classDecl;
        SpecsCheck.checkArgument(CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + finalClassDecl);

        String className = classDecl.get("className");
        String superClass = null;

        if (classDecl.hasAttribute("extendClassName")) {
            superClass = classDecl.get("extendClassName");
        }

        var methods = buildMethods(classDecl);
        var fields = buildFields(classDecl);
        var returnTypes = buildReturnTypes(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl);
        var imports = buildImports(classDecl);

        return new JmmSymbolTable(className, superClass, methods, fields, imports, returnTypes, params, locals);
    }

    private static List<Symbol> buildFields(JmmNode classDecl) {
        return null;
    }

    private static List<String> buildImports(JmmNode classDecl) {
        JmmNode root = classDecl.getParent();
        List<String> result = new ArrayList<>();
        for(JmmNode node : root.getChildren()){
            if(Objects.equals(node.getKind(), "ImportDecl")){
                result.add(node.get("value"));
            }
        }
        return result;
    }

    private static Map<String, Type> buildReturnTypes(JmmNode classDecl) {
        Map<String, Type> map = new HashMap<>();
        String type;

        for (JmmNode methodDecl: classDecl.getChildren(METHOD_DECL)){

            // check if return type is void
            if(methodDecl.getNumChildren() == 0){
                map.put(methodDecl.get("name"), new Type("void", false));
                continue;
            }

            JmmNode returnDecl = methodDecl.getChild(0);
            if (Objects.equals(returnDecl.getChild(0).getKind(), "Array")) {

                type = switch (returnDecl.getChild(0).getChild(0).getKind()) {
                    case "IntegerType" -> "int";
                    case "StringType" -> "String";
                    case "BooleanType" -> "boolean";
                    case "EllipsisType" -> "int...";
                    case "ClassType" -> returnDecl.getChild(0).getChild(0).get("name");
                    default -> null;
                };

                map.put(methodDecl.get("name"), new Type(type, true));

            } else {

                type = switch (returnDecl.getChild(0).getKind()) {
                    case "IntegerType" -> "int";
                    case "StringType" -> "String";
                    case "BooleanType" -> "boolean";
                    case "EllipsisType" -> "int...";
                    case "ClassType" -> returnDecl.getChild(0).get("name");
                    default -> null;
                };

                map.put(methodDecl.get("name"), new Type(type, false));

            }
        }

        return map;
    }

    private static Map<String, List<Symbol>> buildParams(JmmNode classDecl) {
        Map<String, List<Symbol>> map = new HashMap<>();
        String type;
        for(JmmNode methodDecl: classDecl.getChildren(METHOD_DECL)){
            List<Symbol> params = new ArrayList<>();
            for (JmmNode paramDecl : methodDecl.getChildren(PARAM)) {
                JmmNode typeNode = paramDecl.getChild(0);
                if(Objects.equals(typeNode.getKind(), "Array")){
                    type = switch (typeNode.getChild(0).getKind()) {
                        case "IntegerType" -> "int";
                        case "StringType" -> "String";
                        case "BooleanType" -> "boolean";
                        case "EllipsisType" -> "int...";
                        case "ClassType" -> typeNode.getChild(0).get("name");
                        default -> null;
                    };

                    params.add(new Symbol(new Type(type, true), paramDecl.get("name")));
                } else {
                    type = switch (typeNode.getKind()) {
                        case "IntegerType" -> "int";
                        case "StringType" -> "String";
                        case "BooleanType" -> "boolean";
                        case "EllipsisType" -> "int...";
                        case "ClassType" -> typeNode.get("name");
                        default -> null;
                    };

                    params.add(new Symbol(new Type(type, false), paramDecl.get("name")));
                }
            }
            map.put(methodDecl.get("name"), params);
        }

        return map;
    }

    private static Map<String, List<Symbol>> buildLocals(JmmNode classDecl) {
        // TODO: Simple implementation that needs to be expanded

        Map<String, List<Symbol>> map = new HashMap<>();


        classDecl.getChildren(METHOD_DECL).stream()
                .forEach(method -> map.put(method.get("name"), getLocalsList(method)));

        return map;
    }

    private static List<String> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(method -> method.get("name"))
                .toList();
    }


    private static List<Symbol> getLocalsList(JmmNode methodDecl) {
        // TODO: Simple implementation that needs to be expanded

        var intType = new Type(TypeUtils.getIntTypeName(), false);

        return methodDecl.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(intType, varDecl.get("name")))
                .toList();
    }

}

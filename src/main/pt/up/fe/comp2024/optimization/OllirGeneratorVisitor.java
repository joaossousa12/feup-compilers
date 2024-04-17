package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Objects;
import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
        this.buildVisitor();
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit("returnStmt", this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(INTEGER, this::visitInt);
        addVisit(BOOLEAN, this::visitBool);
        addVisit("String", this::visitString);
        addVisit("Void",this::visitVoid);
        addVisit("import",this::visitImport);

        setDefaultVisit(this::defaultVisit);
    }
    private String visitVoid(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();

        var voidType = new Type(TypeUtils.getVoidTypeName(),false);
        code.append(OptUtils.toOllirType(voidType));
        return code.toString();


    }
    private String visitInt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        code.append(OptUtils.toOllirType(intType));


        return code.toString();
    }
    private String visitBool(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        var boolType = new Type(TypeUtils.getBoolTypeName(), false);
        code.append(OptUtils.toOllirType(boolType));

        return code.toString();

    }

    private String visitString(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        var stringType = new Type(TypeUtils.getStringTypeName(), false);
        code.append(OptUtils.toOllirType(stringType));

        return code.toString();

    }
    private String visitImport(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        var importType = new Type(TypeUtils.getImportTypeName(),false);
        code.append(OptUtils.toOllirType(importType));

        code.append(node.get("value")).append(END_STMT);

        int size = code.length();

        for (int i = 0; i < size; i++) {
            char currentChar = code.charAt(i);
            if (currentChar != '[' && currentChar != ']') {
                code.append(currentChar);
            }
        }

        return code.toString();

    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        for (int i = 0; i < table.getLocalVariables(methodName).size(); i++) {
            if (table.getLocalVariables(methodName).get(i).getName().equals(node.get("var"))) {
                code.append(table.getLocalVariables(methodName).get(i).getName());
                code.append(OptUtils.toOllirType(table.getLocalVariables(methodName).get(i).getType()));
            }
        }

        var rhs = exprVisitor.visit(node.getJmmChild(0));

        code.append(rhs.getComputation());

        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);


        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");
        boolean isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        if(isStatic)
            code.append("static ");

        // name
        var name = node.get("name");
        code.append(name);

        // params
        code.append("(");
        var params  = node.getChildren(PARAM);
        if(params.isEmpty() && Objects.equals(node.get("name"), "main")) {
            code.append("args.array.String");
        }
        for (int i = 0; i < params.size(); i++) {
            var paramCode = visit(params.get(i));
            code.append(paramCode);

            var after = i == params.size() - 1 ? "" : ", ";
            code.append(after);
        }
        code.append(")");

        // type


        if(node.getNumChildren() == 0) // void on main
            code.append(".V ");

        else
            code.append(OptUtils.toOllirType(node.getChild(0).getChild(0)));

        code.append(L_BRACKET);

        var numParams = table.getParameters(name).size();
        var numLocals = table.getLocalVariables(name).size();
        // rest of its children stmts
        for (int i = (1 + numParams + numLocals); i < node.getChildren().size(); i++) {
            var childStmt = node.getChild(i);
            var childCode = visit(childStmt);
            code.append(childCode);
        }

        if(params.isEmpty() && Objects.equals(node.get("name"), "main"))
            code.append("ret.V ;").append(NL);
        else{
            JmmNode returnStmt = node.getChild(node.getNumChildren() - 1);
            if(Objects.equals(returnStmt.getChild(0).getKind(), "IntegerLiteral"))
                code.append("ret").append(OptUtils.toOllirType(node.getChild(0).getChild(0))).append(" ").append(returnStmt.getChild(0).get("value")).append(".i32;").append(NL);
            else
                code.append("ret").append(OptUtils.toOllirType(node.getChild(0).getChild(0))).append(" ").append(returnStmt.getChild(0).get("value")).append(OptUtils.toOllirType(returnStmt.getChild(0))).append(";").append(NL);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }



    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        String teste = "null";
        var imports = table.getImports();
        if (!imports.isEmpty()) {
            for (var import1 : imports) {
                code.append("import ");
                String[] parts = import1.substring(1, import1.length() - 1).split(",");

                for (int i = 0; i < parts.length; i++) {
                    String trimmedPart = parts[i].trim();
                    code.append(trimmedPart);
                    if (i < parts.length - 1) {
                        code.append(".");
                    }
                }

                code.append(";").append(NL);
            }
        }
        code.append(table.getClassName());
        code.append(" extends ");
        if(node.hasAttribute("extendClassName")) {
            code.append(table.getSuper());
        }
        else{
            code.append("Object");
        }

        code.append(L_BRACKET);

        code.append(NL);
        for(JmmNode varDecl : node.getChildren(VAR_DECL)) {
            String name = varDecl.get("name");
            String type = OptUtils.toOllirType(varDecl.getChild(0));

            code.append(".field").append(SPACE).append("public").append(SPACE).append(name).append(type).append(";").append(NL);
        }

        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }


    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}

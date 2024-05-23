package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.ClassType;
import org.specs.comp.ollir.ElementType;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.security.PrivateKey;
import java.util.Objects;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(BOOLEAN_LITERAL, this::visitBool);
        addVisit("FunctionCall", this::visitFCall);
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit("BinaryOp", this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        //addVisit("NewClass", this::visitNewClass);

        setDefaultVisit(this::defaultVisit);
    }


    private OllirExprResult visitFCall(JmmNode node,Void unused){
        // static a esquerda do ponto é o nome de uma class se n for estatico é virtual
        // varrefexpr
        boolean isStatic = true;

        JmmNode left = node.getChild(0);
        if(Objects.equals(left.getKind(), "VarRefExpr")){
            if(Objects.equals(getActualTypeVarRef(left).getKind(), "ClassType")){
                isStatic = false;
            }
        }

        if(Objects.equals(left.getKind(), "Object")){
            isStatic = false;
        }

        StringBuilder code = new StringBuilder();

        String name = null;
        if(left.hasAttribute("name"))
            name = left.get("name");

        if(isStatic && name != null && OptUtils.checkIfInImports(name, table)){
            code.append("invokestatic");
            code.append("(");
            code.append((node.getChild(0).get("name")));
            code.append(", ");
            code.append("\"" + node.get("name") + "\"");
            for(int i = 1; i < node.getNumChildren(); i++){
                code.append(", ");
                code.append(node.getChild(i).get("name"));
                code.append(OptUtils.toOllirType(getActualTypeVarRef(node.getChild(i))));
            }
            if(node.getNumChildren() > 2)
                code.append(visit(node.getChild(1)).getCode());
            code.append(")");
            String varName = node.getParent().get("var"); // assume type of variable

            JmmNode methodNode = node;
            JmmNode n = methodNode;
            while(!Objects.equals(methodNode.getKind(), "MethodDecl")){
                if(Objects.equals(n.getKind(), "MethodDecl"))
                    methodNode = n;
                else
                    n = n.getParent();
            }
            JmmNode type = null;
            for(JmmNode l : methodNode.getDescendants()){
                if(l.getKind().equals("VarDecl") && l.get("name").equals(varName)){
                    type = l.getChild(0);
                    break;
                }
            }
            code.append(OptUtils.toOllirType(type));
            //code.append(END_STMT);
            String stringCode = code.toString();
            return new OllirExprResult(stringCode);
        }
        else if(isStatic) {
            code.append("invokestatic");
            code.append("(");
            if(Objects.equals(node.getChild(0).getKind(), "Paren")){
                code.append((node.getChild(0).getChild(0).get("name")));
            }
            else
                code.append((node.getChild(0).get("name")));
            code.append(", ");
            code.append("\"" + node.get("name") + "\"");
            for(int i = 1; i < node.getNumChildren(); i++){
                JmmNode nodeHelp = node;
                if(Objects.equals(node.getChild(i).getKind(), "Paren"))
                    nodeHelp = node.getChild(0);
                code.append(", ");
                code.append(nodeHelp.getChild(i).get("name"));
                code.append(OptUtils.toOllirType(getActualTypeVarRef(nodeHelp.getChild(i))));
            }
            if(Objects.equals(node.getChild(0).getKind(), "Paren")){
                JmmNode nodeHelp = node.getChild(0);

                while(Objects.equals(nodeHelp.getKind(), "Paren"))
                    nodeHelp = nodeHelp.getChild(0);

                code.append(visit(nodeHelp).getCode());
            }
            else
                code.append(visit(node.getChild(1)).getCode());
            code.append(")");
            code.append(".V");
            //code.append(END_STMT);
            String stringCode = code.toString();
            return new OllirExprResult(stringCode);
        }
        else{
            code.append("invokevirtual");
            code.append("(");
            if(Objects.equals(node.getChild(0).getKind(), "Object")) {
                code.append(node.getChild(0).get("value"));
                code.append(".");
                if(Objects.equals(node.getChild(0).get("value"), "this")){
                    code.append(table.getClassName());
                }
            }
            else{
                code.append(node.getChild(0).get("name"));
                code.append(".");
                code.append(getActualTypeVarRef(node.getChild(0)).get("name"));
            }
            code.append(", ");
            code.append("\"" + node.get("name") + "\"");
            for(int i = 1; i < node.getNumChildren(); i++){
                code.append(", ");
                if(node.getChild(i).getKind().equals("IntegerLiteral")){
                    code.append(node.getChild(i).get("value"));
                }
                else{
                    code.append(node.getChild(i).get("name"));

                }
                if(node.getChild(i).getKind().equals("IntegerLiteral")){
                    code.append(".i32");
                }
                else{
                    code.append(OptUtils.toOllirType(getActualTypeVarRef(node.getChild(i))));

                }
            }
            code.append(")");

            JmmNode classDecl = node;
            while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
                classDecl = classDecl.getParent();
            }
            JmmNode retNode = null;
            for(JmmNode method : classDecl.getChildren()) {
                if(Objects.equals(method.get("name"), node.get("name"))){
                    retNode = method.getChild(0).getChild(0);
                }
            }
            code.append(OptUtils.toOllirType(retNode));


            //code.append(END_STMT);
            String stringCode = code.toString();
            return new OllirExprResult(stringCode);
        }
    }
    private OllirExprResult visitBool(JmmNode node, Void unused){
        var bool = new Type(TypeUtils.getBoolTypeName(), false);
        String ollirBoolType = OptUtils.toOllirType(bool);
        String code = node.get("value") + ollirBoolType;
        return new OllirExprResult(code);

    }
    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        Type resType = new Type("boolean", false);
        if(Objects.equals(node.get("op"), "+") || Objects.equals(node.get("op"), "-") || Objects.equals(node.get("op"), "*") || Objects.equals(node.get("op"), "/")){
            resType = new Type("int", false);
        }

        var lhs = visit(node.getJmmChild(0));
        if(node.getChild(0).getKind().equals("BooleanLiteral")){
            if(lhs.getCode().equals("true.bool") ){
                lhs.setCode("1.bool");

            }
            else{
                lhs.setCode("0.bool");
            }
        }
        var rhs = visit(node.getJmmChild(1));

        if(Objects.equals(node.getChild(1).getKind(), "FunctionCall")){
            String resOllirType = OptUtils.toOllirType(resType);
            String code = OptUtils.getTemp() + resOllirType;
            rhs = new OllirExprResult(code, code + " :=" + resOllirType + " " + rhs.getCode() + ";\n");
        }

        if(Objects.equals(node.getChild(0).getKind(), "FunctionCall")){
            String resOllirType = OptUtils.toOllirType(resType);
            String code = OptUtils.getTemp() + resOllirType;
            lhs = new OllirExprResult(code, code + " :=" + resOllirType + " " + lhs.getCode() + ";\n");
        }
        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        computation.append(node.get("op")).append(OptUtils.toOllirType(resType)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

    private JmmNode getActualTypeVarRef(JmmNode varRefExpr){
        JmmNode ret = varRefExpr;
        JmmNode classDecl = varRefExpr;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }

        for(JmmNode node : classDecl.getDescendants()) {
            if(Objects.equals(node.getKind(), "Param") || Objects.equals(node.getKind(), "VarDecl")) {
                if(Objects.equals(node.get("name"), varRefExpr.get("name"))) {
                    ret = node.getChild(0);
                    break;
                }
            }
        }


        return ret;
    }
//    private OllirExprResult visitNewClass(JmmNode node, Void unused){
//        StringBuilder computation = new StringBuilder();
//        String nodeType = OptUtils.toOllirType(node);
//        String tempVar = OptUtils.getTemp();
//
//        computation.append(String.format("%s%s :=%s new(%s)%s;\n", tempVar, nodeType, nodeType, node.get("name"), nodeType));
//        computation.append(String.format("invokespecial(%s%s, \"<init>\").V;\n", tempVar, nodeType));
//
//        String code = String.format("%s%s", tempVar, nodeType);
//
//        return new OllirExprResult(code, computation.toString());
//    }


}

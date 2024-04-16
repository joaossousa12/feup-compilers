package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.Objects;


public class DifferentTypeOp extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("BinaryOp", this::visitBinaryOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitBinaryOp(JmmNode binaryOp, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        JmmNode left = binaryOp.getChild(0);
        JmmNode right = binaryOp.getChild(1);
        String operator = binaryOp.get("op");
        //TODO pode ficar mais clean se mudarmos e usarmos os metodos no typeUtils (ver para a frente)

        if(Objects.equals(left.getKind(), "VarRefExpr"))
            left = getActualTypeVarRef(left);

        else if(Objects.equals(left.getKind(), "FunctionCall"))
            left = getActualTypeFunctionCall(left);

        if(Objects.equals(right.getKind(), "VarRefExpr"))
            right = getActualTypeVarRef(right);

        else if(Objects.equals(right.getKind(), "FunctionCall")){
            right = getActualTypeFunctionCall(right);
        }


        // arithmetic operators
        if(Objects.equals(operator, "+") || Objects.equals(operator, "-") || Objects.equals(operator, "/") || Objects.equals(operator, "*") || Objects.equals(operator, ">") || Objects.equals(operator, "<")){
            if(!(Objects.equals(left.getKind(), "IntegerLiteral") || Objects.equals(left.getKind(), "IntegerType")) || !(Objects.equals(right.getKind(), "IntegerLiteral") || Objects.equals(right.getKind(), "IntegerType"))){
                var message = String.format("Either '%s' or '%s' is not integer type (arithmetic).", left.getKind(), right.getKind());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryOp),
                        NodeUtils.getColumn(binaryOp),
                        message,
                        null)
                );
            }
        }
        // boolean operators
        else{
            if(!Objects.equals(left.getKind(), "BooleanLiteral") || !Objects.equals(right.getKind(), "BooleanLiteral")){
                var message = String.format("Either '%s' or '%s' is not boolean type.", left.getKind(), right.getKind());
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(binaryOp),
                        NodeUtils.getColumn(binaryOp),
                        message,
                        null)
                );
            }
        }

        return null;
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

    private JmmNode getActualTypeFunctionCall(JmmNode functionCallExpr){
        String methodName = functionCallExpr.get("name");
        JmmNode ret = functionCallExpr;
        JmmNode classDecl = functionCallExpr;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }

        for(JmmNode node : classDecl.getDescendants()) {
            if(Objects.equals(node.getKind(), "MethodDecl") && Objects.equals(node.get("name"), methodName)) {
                ret = node.getChild(0).getChild(0);
                break;
            }
        }


        return ret;
    }

}


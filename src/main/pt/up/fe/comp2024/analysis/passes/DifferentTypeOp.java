package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
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

        // arithmetic operators
        if(Objects.equals(operator, "+") || Objects.equals(operator, "-") || Objects.equals(operator, "/") || Objects.equals(operator, "*") || Objects.equals(operator, ">") || Objects.equals(operator, "<")){
            if(!Objects.equals(left.getKind(), "IntegerLiteral") || !Objects.equals(right.getKind(), "IntegerLiteral")){
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

}


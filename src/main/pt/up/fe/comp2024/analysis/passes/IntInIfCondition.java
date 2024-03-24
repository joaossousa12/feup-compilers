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
public class IntInIfCondition extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("IfElseStmt", this::visitIfElseStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitIfElseStmt(JmmNode ifElseStmt, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        JmmNode condition = ifElseStmt.getChild(0);

        if((Objects.equals(condition.getKind(), "BinaryOp") &&
        (Objects.equals(condition.get("op"), "+") || Objects.equals(condition.get("op"), "-") || Objects.equals(condition.get("op"), "/") || Objects.equals(condition.get("op"), "*")))
        || (Objects.equals(condition.getKind(), "IntegerLiteral")) || (Objects.equals(condition.getKind(), "IntegerType"))){
            var message = "Condition is not a boolean type.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(ifElseStmt),
                    NodeUtils.getColumn(ifElseStmt),
                    message,
                    null)
            );
        }

        return null;
    }
}

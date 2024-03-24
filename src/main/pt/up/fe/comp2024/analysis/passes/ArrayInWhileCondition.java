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
public class ArrayInWhileCondition extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("WhileStmt", this::visitWhile);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitWhile(JmmNode whileNode, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        JmmNode condition = whileNode.getChild(0);
        boolean array = false;
        if(Objects.equals(condition.getKind(), "VarRefExpr")){
            String variable = condition.get("name");
            for(JmmNode decl : condition.getParent().getParent().getChildren()){
                if(Objects.equals(decl.getKind(), "VarDecl") && decl.get("name").equals(variable)){
                    array = Objects.equals(decl.getChild(0).getKind(), "Array");
                }
            }
        }

        if(array){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(whileNode),
                    NodeUtils.getColumn(whileNode),
                    "Array as while condition",
                    null)
            );
        }
        return null;
    }
}

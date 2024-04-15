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

public class MemberAccessOnInt extends  AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("FunctionCall", this::visitMethodAccess);
        addVisit("Length", this::visitMethodAccess);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitMethodAccess(JmmNode methodAccess, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        if(!Objects.equals(methodAccess.getChild(0).getKind(), "VarRefExpr"))
            return null;

        String variable = methodAccess.getChild(0).get("name");

        JmmNode classDecl = methodAccess;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }

        for(JmmNode methodDecl : classDecl.getChildren()) {
            for(JmmNode node: methodDecl.getChildren()) {
                if(Objects.equals(node.getKind(), "VarDecl") && Objects.equals(node.get("name"), variable)){
                    if(Objects.equals(node.getChild(0).getKind(), "IntegerType") && node.getChild(0).getNumChildren() < 1){
                        var message = "Trying to do a member access on an int.";
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodAccess),
                                NodeUtils.getColumn(methodAccess),
                                message,
                                null)
                        );
                    }
                }
            }
        }

        return null;
    }
}

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

public class ArrayIndexNotInt extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("ArrayAccess", this::visitArrayIndex);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitArrayIndex(JmmNode arrayIndex, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        JmmNode index = arrayIndex.getChild(1);

        while(Objects.equals(index.getKind(), "Paren"))
            index = index.getChild(0);

        if(Objects.equals(index.getKind(), "VarRefExpr"))
            index = getActualTypeVarRef(index);

        if(Objects.equals(index.getKind(), "BinaryOp")){
            if(!(Objects.equals(index.get("op"), "+") || Objects.equals(index.get("op"), "-") || Objects.equals(index.get("op"), "*") || Objects.equals(index.get("op"), "/")))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arrayIndex),
                        NodeUtils.getColumn(arrayIndex),
                        "Array index is not integer type.",
                        null)
                );
            return null;
        }
        else if(!Objects.equals(index.getKind(), "IntegerLiteral") && !Objects.equals(index.getKind(), "IntegerType")){
            var message = "Array index is not integer type.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayIndex),
                    NodeUtils.getColumn(arrayIndex),
                    message,
                    null)
            );
            return null;
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
}

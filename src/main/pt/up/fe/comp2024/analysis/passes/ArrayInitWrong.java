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
public class ArrayInitWrong extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("ArrayInit", this::visitArrayInit);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitArrayInit(JmmNode arrayInit, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String variable = arrayInit.getParent().get("var");
        String type = null;
        boolean array = false;

        for(JmmNode decl : arrayInit.getParent().getParent().getChildren()){
            if(Objects.equals(decl.getKind(), "VarDecl") && decl.get("name").equals(variable)){
                if(Objects.equals(decl.getChild(0).getKind(), "Array")) {
                    array = true;
                    type = decl.getChild(0).getChild(0).getKind();
                } else {
                    array = false;
                    type = decl.getChild(0).getKind();
                }
            }
        }

        if(!array){ // arrayInitWrong2
            var message = "Array initialization on a non-array variable.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayInit),
                    NodeUtils.getColumn(arrayInit),
                    message,
                    null)
            );
        }

        //TODO might need change for future cases
        if(Objects.equals(type, "IntegerType")) type = "IntegerLiteral";
        else if(Objects.equals(type, "BooleanType")) type = "BooleanLiteral";

        for(JmmNode child : arrayInit.getChildren()){ // arrayInitWrong1
            if(!Objects.equals(child.getKind(), type)){
                var message = "Array initialization with wrong type";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(child),
                        NodeUtils.getColumn(child),
                        message,
                        null)
                );
            }
        }


        return null;
    }
}

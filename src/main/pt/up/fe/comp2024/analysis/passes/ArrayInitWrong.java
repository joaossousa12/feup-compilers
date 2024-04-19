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
        addVisit("NewArray", this::visitNewArray);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitArrayInit(JmmNode arrayInit, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        if(Objects.equals(arrayInit.getParent().getKind(), "FunctionCall") || Objects.equals(arrayInit.getParent().getKind(), "Length"))
            return null;

        if(Objects.equals(arrayInit.getParent().getKind(), "ReturnStmt")){
            if(!Objects.equals(arrayInit.getParent().getParent().getChild(0).getChild(0).getKind(), "Array")) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(arrayInit),
                        NodeUtils.getColumn(arrayInit),
                        "Returning an array on a non array returning method!",
                        null)
                );
            }
            return null;
        }

        if(Objects.equals(arrayInit.getParent().getKind(), "ArrayAccess"))
            return null;

        if(Objects.equals(arrayInit.getParent().getKind(), "WhileStmt   "))
            return null;

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

    private Void visitNewArray(JmmNode newArray, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        JmmNode newArraySize = newArray.getChild(0);
        if(Objects.equals(newArraySize.getKind(), "VarRefExpr")){
            newArraySize = getActualTypeVarRef(newArraySize, currentMethod);
            if(Objects.equals(newArraySize.getKind(), "VarRefExpr"))
                newArraySize = getActualTypeVarRef(newArraySize);
        }
        else if(Objects.equals(newArraySize.getKind(), "FunctionCall")){
            newArraySize = getActualTypeFunctionCall(newArraySize);
        }

        if(!(Objects.equals(newArraySize.getKind(), "IntegerLiteral") || Objects.equals(newArraySize.getKind(), "IntegerType")) ){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(newArray),
                    NodeUtils.getColumn(newArray),
                    "New array error",
                    null)
            );
        }
        if(Objects.equals(newArray.getParent().getKind(), "Length") || Objects.equals(newArray.getParent().getKind(), "FunctionCall"))
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(newArray),
                    NodeUtils.getColumn(newArray),
                    "New array error",
                    null)
            );

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

    private JmmNode getActualTypeVarRef(JmmNode varRefExpr, String methodName){
        JmmNode ret = varRefExpr;
        JmmNode classDecl = varRefExpr;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }

        for(JmmNode node1 : classDecl.getChildren()) {
            if(node1.get("name").equals(methodName)) {
                for (JmmNode node : node1.getDescendants()) {
                    if (Objects.equals(node.getKind(), "Param") || Objects.equals(node.getKind(), "VarDecl")) {
                        if (Objects.equals(node.get("name"), varRefExpr.get("name"))) {
                            ret = node.getChild(0);
                            break;
                        }
                    }
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

        for(JmmNode node : classDecl.getChildren(Kind.METHOD_DECL)) {
            if(Objects.equals(node.get("name"), methodName)) {
                ret = node.getChild(0).getChild(0);
                break;
            }
        }


        return ret;
    }
}

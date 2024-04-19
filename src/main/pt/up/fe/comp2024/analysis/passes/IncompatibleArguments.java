package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IncompatibleArguments extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("FunctionCall", this::visitMethodCall);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {
        if(Objects.equals(methodCall.getChild(0).getKind(), "Object"))
            return null;
        JmmNode classDecl = methodCall;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }
        int numParams1 = methodCall.getNumChildren() - 1;
        int numParams2 = 0;
        List<String> paramTypes1 = new ArrayList<>();
        for(int i = 1; i <= numParams1; i++) {
            if(Objects.equals(methodCall.getChild(i).getKind(), "VarRefExpr")){
                paramTypes1.add(getActualTypeVarRef(methodCall.getChild(i)).getKind());
            }
            else if(Objects.equals(methodCall.getChild(i).getKind(), "FunctionCall")){
                paramTypes1.add(getActualTypeFunctionCall(methodCall.getChild(i)).getKind());
            }
            else {
                if(Objects.equals(methodCall.getChild(i).getKind(), "IntegerLiteral"))
                    paramTypes1.add("IntegerType");
                else if(Objects.equals(methodCall.getChild(i).getKind(), "BooleanLiteral"))
                    paramTypes1.add("BooleanType");
                else
                    paramTypes1.add(methodCall.getChild(i).getKind());
            }
        }
        List<String> paramTypes = new ArrayList<>();
        for(JmmNode method : classDecl.getChildren()) {
            if(Objects.equals(method.get("name"), methodCall.get("name"))){
                for(JmmNode params : method.getChildren()) {
                    if(Objects.equals(params.getKind(), "Param")) {
                        numParams2++;
                        paramTypes.add(params.getChild(0).getKind());
                    }
                }

                if(numParams1 == numParams2 && numParams1 == 0){
                    return null;
                }

                else if(numParams1 == numParams2){
                    if(paramTypes.equals(paramTypes1))
                        return null;
                }

                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall),
                        NodeUtils.getColumn(methodCall),
                        "Incompatible arguments",
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

        for(JmmNode node : classDecl.getChildren(Kind.METHOD_DECL)) {
            if(Objects.equals(node.get("name"), methodName)) {
                ret = node.getChild(0).getChild(0);
                break;
            }
        }


        return ret;
    }
}

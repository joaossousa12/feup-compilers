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

import static pt.up.fe.comp2024.ast.Kind.METHOD_DECL;

public class VarArgs extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit("FunctionCall", this::visitEllipsisMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitEllipsisMethod(JmmNode methodCall, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String methodName = methodCall.get("name");

        JmmNode classDecl = methodCall;
        while(!Objects.equals(classDecl.getKind(), "ClassDecl")){
            classDecl = classDecl.getParent();
        }

        // check if varargs is defined as a field
        for(JmmNode varDecl : classDecl.getChildren(Kind.VAR_DECL)){
            if(Objects.equals(varDecl.getChild(0).getKind(), "EllipsisType"))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDecl.getChild(0)),
                        NodeUtils.getColumn(varDecl.getChild(0)),
                        "Varargs defined as a field",
                        null)
                );
        }

        // check if varargs is defined as a local
        for(JmmNode methodDecl : classDecl.getChildren(METHOD_DECL)){
            for(JmmNode varDecl : methodDecl.getChildren(Kind.VAR_DECL)){
                if(Objects.equals(varDecl.getChild(0).getKind(), "EllipsisType"))
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(varDecl.getChild(0)),
                            NodeUtils.getColumn(varDecl.getChild(0)),
                            "Varargs defined as a field",
                            null)
                    );
            }
        }

        boolean ellipsis = false;
        int numberOfParams = 0;

        for(JmmNode methodDecl : classDecl.getChildren(METHOD_DECL)){
            if(Objects.equals(methodDecl.get("name"), methodName)){
                for(JmmNode param : methodDecl.getChildren("Param")){
                    numberOfParams++;
                    if(Objects.equals(param.getChild(0).getKind(), "EllipsisType")){
                        ellipsis = true;
                    }
                }
            }
        }

        if(ellipsis && numberOfParams > 1){
            int index = 0;

            //TODO dont know if this is correct because of teacher message on slack about varargs tests
            if(Objects.equals(methodCall.getChild(0).getKind(), "Object") && Objects.equals(methodCall.getChild(0).get("value"), "this") && methodCall.getChild(0).getNumChildren() == 0){
                index = 1;
            }

            if(Objects.equals(methodCall.getChild(index).getKind(), "IntegerLiteral")){
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall),
                        NodeUtils.getColumn(methodCall),
                        "Cannot pass an integer to a varargs method",
                        null)
                );
            }

        }

        else if(ellipsis && numberOfParams == 1){
            String type = methodCall.getChild(1).getKind();
            for(JmmNode node : methodCall.getChildren()){
                if(Objects.equals(node.getKind(), "Object")){
                    continue;
                }
                else if(!Objects.equals(node.getKind(), type)){
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(methodCall),
                            NodeUtils.getColumn(methodCall),
                            "More than one type on varargs method",
                            null)
                    );
                }
            }
        }

        return null;
    }
}

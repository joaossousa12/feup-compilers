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

public class AssignIntToBool extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("AssignStmt", this::visitAssign);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitAssign(JmmNode assign, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");
        if(Objects.equals(assign.getChild(0).getKind(), "ArrayAccess")){
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(assign.getChild(0)),
                    NodeUtils.getColumn(assign.getChild(0)),
                    "Assign to array access",
                    null)
            );

            return null;
        }


        String variable = assign.get("var");
        String type = null;
        for(JmmNode decl : assign.getParent().getChildren()){
            if(Objects.equals(decl.getKind(), "VarDecl") && decl.get("name").equals(variable)){
                type=decl.getChild(0).getKind();
            }
        }

        JmmNode classDecl = assign;
        while(!Objects.equals(classDecl.getKind(), "ClassDecl")){
            classDecl = classDecl.getParent();
        }

        if(Objects.equals(type, "BooleanType")){
            if(Objects.equals(assign.getChild(0).getKind(), "IntegerLiteral") || Objects.equals(assign.getChild(0).getKind(), "NewClass")){
                var message = "Assigning an integer or an object to a boolean.";
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(assign),
                        NodeUtils.getColumn(assign),
                        message,
                        null)
                );
            }
        }
        else if(Objects.equals(assign.getChild(0).getKind(), "FunctionCall")){
            for(JmmNode methodDecl : classDecl.getChildren(Kind.METHOD_DECL)){
                if(Objects.equals(methodDecl.get("name"), assign.getChild(0).get("name"))){
                    if(!Objects.equals(methodDecl.getChild(0).getChild(0).getKind(), type)){
                        var message = "method Signature Wrong Literal Type Invalid";
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(assign),
                                NodeUtils.getColumn(assign),
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

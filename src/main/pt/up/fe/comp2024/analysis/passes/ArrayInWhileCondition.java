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

        if(Objects.equals(condition.getKind(), "VarRefExpr"))
            condition = getActualTypeVarRef(condition, currentMethod);

        if(Objects.equals(condition.getKind(), "BinaryOp")){
            JmmNode left = condition.getChild(0);
            JmmNode right = condition.getChild(1);

            if(Objects.equals(left.getKind(), "VarRefExpr"))
                left = getActualTypeVarRef(left, currentMethod);

            if(Objects.equals(right.getKind(), "VarRefExpr"))
                right = getActualTypeVarRef(right, currentMethod);

            if((Objects.equals(condition.get("op"), "||") || Objects.equals(condition.get("op"), "&&"))){
                if(!checkIfBoolean(left) || !checkIfBoolean(right)) {
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(condition),
                            NodeUtils.getColumn(condition),
                            "Condition is not a boolean",
                            null)
                    );

                    return null;
                }
                else
                    return null;
            }
            if((Objects.equals(condition.get("op"), ">") || Objects.equals(condition.get("op"), "<"))){
                if(!checkIfInteger(left) || !checkIfInteger(right)){
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(condition),
                            NodeUtils.getColumn(condition),
                            "Condition is not a boolean",
                            null)
                    );

                    return null;
                }
                else
                    return null;
            }
            else {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(condition),
                        NodeUtils.getColumn(condition),
                        "Condition is not a boolean",
                        null)
                );

                return null;
            }

        }




        if(!checkIfBoolean(condition))
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(condition),
                    NodeUtils.getColumn(condition),
                    "Condition is not a boolean",
                    null)
            );

        return null;
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

    private boolean checkIfBoolean(JmmNode node){
        if(Objects.equals(node.getKind(), "FunctionCall")){
            JmmNode classDecl = node;
            while(!Objects.equals(classDecl.getKind(), "ClassDecl")){
                classDecl = classDecl.getParent();
            }

            JmmNode retType = null;
            for(JmmNode method : classDecl.getChildren(Kind.METHOD_DECL)){
                if(Objects.equals(method.get("name"), node.get("name"))){
                    retType = method.getChild(0).getChild(0);
                }
            }

            return  checkIfBoolean(retType);
        }
        return Objects.equals(node.getKind(), "BooleanLiteral") || Objects.equals(node.getKind(), "BooleanType");
    }

    private boolean checkIfInteger(JmmNode node){
        if(Objects.equals(node.getKind(), "FunctionCall")){
            JmmNode classDecl = node;
            while(!Objects.equals(classDecl.getKind(), "ClassDecl")){
                classDecl = classDecl.getParent();
            }

            JmmNode retType = null;
            for(JmmNode method : classDecl.getChildren(Kind.METHOD_DECL)){
                if(Objects.equals(method.get("name"), node.get("name"))){
                    retType = method.getChild(0).getChild(0);
                }
            }

            return  checkIfInteger(retType);
        }
        return Objects.equals(node.getKind(), "IntegerLiteral") || Objects.equals(node.getKind(), "IntegerType") || Objects.equals(node.getKind(), "ArrayAccess");
    }
}

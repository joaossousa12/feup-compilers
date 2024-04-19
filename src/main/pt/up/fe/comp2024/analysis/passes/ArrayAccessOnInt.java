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

public class ArrayAccessOnInt extends AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("ArrayAccess", this::visitArrayAccess);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitArrayAccess(JmmNode arrayAccess, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        JmmNode methodDecl = arrayAccess;
        while (!Objects.equals(methodDecl.getKind(), "MethodDecl")) {
            methodDecl = methodDecl.getParent();
        }

        if(Objects.equals(arrayAccess.getChild(0).getKind(), "ArrayInit")){
            if(Objects.equals(arrayAccess.getParent().getKind(), "WhileStmt"))
                return null;
            if(Objects.equals(arrayAccess.getParent().getKind(), "BinaryOp"))
                return null;
            if(Objects.equals(arrayAccess.getChild(0).getKind(), "ArrayInit"))
                return null;
            String assignedTo = arrayAccess.getParent().get("var");
            String assignedToType = null;
            for(JmmNode varDecl : methodDecl.getChildren(Kind.VAR_DECL)){
                if(Objects.equals(varDecl.get("name"), assignedTo)){
                    assignedToType = varDecl.getChild(0).getKind();
                }
            }

            if(assignedToType == null){
                for(JmmNode varDecl: methodDecl.getParent().getChildren(Kind.VAR_DECL)){
                    if(Objects.equals(varDecl.get("name"), assignedTo)){
                        assignedToType = varDecl.getChild(0).getKind();
                    }
                }
            }

            for(JmmNode child : arrayAccess.getChild(0).getChildren()){
                String type = child.getKind();
                switch (assignedToType){
                    case "IntegerType":
                        if(!(Objects.equals(type, "IntegerType") || Objects.equals(type, "IntegerLiteral"))) {
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(methodDecl),
                                    NodeUtils.getColumn(methodDecl),
                                    "wrong argument ellipsis method",
                                    null));

                            return null;
                        }

                        else
                            break;
                    case "BooleanType":
                        if(!(Objects.equals(type, "BooleanType") || Objects.equals(type, "BooleanLiteral"))){
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(methodDecl),
                                    NodeUtils.getColumn(methodDecl),
                                    "wrong argument ellipsis method",
                                    null));

                            return null;
                        }

                        else
                            break;
                    case "StringType":
                        if(!(Objects.equals(type, "StringType"))){
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(methodDecl),
                                    NodeUtils.getColumn(methodDecl),
                                    "wrong argument ellipsis method",
                                    null));

                            return null;
                        }

                        else
                            break;
                    case "Array":
                        if(!(Objects.equals(type, "Array")))
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(methodDecl),
                                    NodeUtils.getColumn(methodDecl),
                                    "wrong argument ellipsis method",
                                    null));
                        else
                            break;
                    case "ClassType":
                        if(!(Objects.equals(type, "ClassType") && Objects.equals(child.get("name"), child.get("name")))){
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(methodDecl),
                                    NodeUtils.getColumn(methodDecl),
                                    "wrong argument ellipsis method",
                                    null));

                            return null;
                        }

                        else
                            break;
                    default:
                        break;
                }
            }
            return null;
        }
        String variable = arrayAccess.getChild(0).get("name");

        for(JmmNode param : methodDecl.getChildren("Param")){
            if(Objects.equals(param.get("name"), variable)){
                if(Objects.equals(param.getChild(0).getKind(), "EllipsisType")) return null; // made this so it passes the varargs tests
            }
        }

        JmmNode array = arrayAccess.getChild(0);

        if(Objects.equals(array.getKind(), "VarRefExpr")){
            array = getActualTypeVarRef(array, currentMethod);
        }

        if(array.getNumChildren() < 1 || !Objects.equals(array.getKind(), "Array")){
            var message = "Trying to access an integer instead of an array.";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(arrayAccess),
                    NodeUtils.getColumn(arrayAccess),
                    message,
                    null)
            );
        }

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
}

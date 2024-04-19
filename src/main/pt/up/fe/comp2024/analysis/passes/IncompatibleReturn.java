package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;

import java.util.Objects;

public class IncompatibleReturn extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit("ReturnStmt", this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) { //TODO probably needs a lot of changes
        JmmNode child = returnStmt.getChild(0);
        String methodType = returnStmt.getParent().getChild(0).getChild(0).getKind();

        if(Objects.equals(child.getKind(), "Length") && (Objects.equals(child.getChild(0).getKind(), "ArrayInit") || (Objects.equals(child.getChild(0).getKind(), "VarRefExpr") && Objects.equals(getActualTypeVarRef(child.getChild(0),currentMethod).getKind(), "Array"))))
            return null;

        JmmNode classDecl = returnStmt;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }

        if(Objects.equals(child.getKind(), "BinaryOp")){
            if(Objects.equals(child.get("op"), "+") || Objects.equals(child.get("op"), "-") || Objects.equals(child.get("op"), "*") || Objects.equals(child.get("op"), "/")){
                if(Objects.equals(methodType, "IntegerType")){
                    return null;
                }
            }
            else if(Objects.equals(child.get("op"), ">") || Objects.equals(child.get("op"), "<"))
                if(Objects.equals(methodType, "BooleanType")){
                    return null;
                }
        }

        if(Objects.equals(child.getKind(), "IntegerLiteral")){
            if(Objects.equals(methodType, "IntegerType")){
                return null;
            }
        }

        if(Objects.equals(child.getKind(), "BooleanLiteral")){
            if(Objects.equals(methodType, "BooleanType")){
                return null;
            }
        }

        if(Objects.equals(child.getKind(), "FunctionCall")){
            JmmNode node = getActualTypeFunctionCall(child);
            if(Objects.equals(methodType, getActualTypeFunctionCall(child).getKind())){
                return null;
            }

            if(Objects.equals(node.getKind(), "FunctionCall")){ // imported method
                if(node.getNumChildren() > 0){
                    if(Objects.equals(node.getChild(0).getKind(), "VarRefExpr")){
                        JmmNode node2 = getActualTypeVarRef(node.getChild(0));
                        if(Objects.equals("VarRefExpr", getActualTypeVarRef(node2).getKind()))
                            return null;
                    }
                }
            }

        }

        if(Objects.equals(child.getKind(), "VarRefExpr")){
            JmmNode node = getActualTypeVarRef(child, currentMethod);
            if(Objects.equals(node.getKind(), "VarRefExpr")){
                node = getActualTypeVarRef(node);
            }
            if(Objects.equals(methodType, node.getKind()))
                return null;
        }

        if(Objects.equals(child.getKind(), "ArrayAccess")){
            JmmNode node1 = getActualTypeVarRef(child.getChild(0), currentMethod);
            while(!Objects.equals("MethodDecl", node1.getKind())){
                node1 = node1.getParent();
            }
            if(Objects.equals(methodType, node1.getChild(0).getChild(0).getKind())){
                return null;
            }
        }

        if(Objects.equals(child.getKind(), "Object")){
            if(Objects.equals(methodType, "ClassType"))
                return null;
        }

        if(Objects.equals(child.getKind(), "ArrayInit")){
            if(Objects.equals(methodType, "Array"))
                return null;
        }

        if(Objects.equals(child.getKind(), "Length")){
            if(Objects.equals(methodType, "IntegerType"))
                return null;
        }

        addReport(Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(returnStmt),
                NodeUtils.getColumn(returnStmt),
                "Incompatible return",
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

        for(JmmNode node : classDecl.getDescendants()) {
            if(Objects.equals(node.getKind(), "MethodDecl") && Objects.equals(node.get("name"), methodName)) {
                ret = node.getChild(0).getChild(0);
                break;
            }
        }


        return ret;
    }

}

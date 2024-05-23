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

public class VarArgs extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CLASS_DECL, this::visitClassDecl);
        addVisit("FunctionCall", this::visitEllipsisMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");

        for(JmmNode paramDecl : method.getChildren(Kind.PARAM)){
            if(Objects.equals(paramDecl.getChild(0).getKind(), "EllipsisType"))
                checkVarargs(method);
        }

        return null;
    }

    private Void visitClassDecl(JmmNode classDecl, SymbolTable table) {
        // check if varargs is defined as a field/local
        for(JmmNode varDecl : classDecl.getDescendants(Kind.VAR_DECL)){
            if(Objects.equals(varDecl.getChild(0).getKind(), "EllipsisType"))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(varDecl.getChild(0)),
                        NodeUtils.getColumn(varDecl.getChild(0)),
                        "Varargs defined as a field/local",
                        null)
                );
        }

        return null;
    }

    private Void visitEllipsisMethod(JmmNode methodCall, SymbolTable table) {
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String methodName = methodCall.get("name");

        JmmNode classDecl = methodCall;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }

        for (JmmNode methodDecl : classDecl.getChildren(Kind.METHOD_DECL)) {
            int numParamsBefore = 0;
            for(JmmNode paramDecl : methodDecl.getChildren(Kind.PARAM)){
                if(Objects.equals(paramDecl.getChild(0).getKind(), "EllipsisType")){
                    int temp = numParamsBefore;
                    while(temp > 0){
                        int currIndex = numParamsBefore - temp;
                        String methodParamType = methodDecl.getChild(currIndex + 1).getChild(0).getKind();
                        String methodCallArgType;

                        if(currIndex + 1 < methodCall.getNumChildren()){
                            methodCallArgType = methodCall.getChild(currIndex + 1).getKind();
                            if(Objects.equals(methodCallArgType, "VarRefExpr"))
                                methodCallArgType = getActualTypeVarRef(methodCall.getChild(currIndex + 1)).getKind();
                        }
                        else{
                            methodCallArgType = methodCall.getChild(0).getKind();
                            if(Objects.equals(methodCallArgType, "VarRefExpr"))
                                methodCallArgType = getActualTypeVarRef(methodCall.getChild(0)).getKind();
                        }



                        switch(methodParamType){
                            case "IntegerType":
                                if(!(Objects.equals(methodCallArgType, "IntegerType") || Objects.equals(methodCallArgType, "IntegerLiteral"))) {
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
                                if(!(Objects.equals(methodCallArgType, "BooleanType") || Objects.equals(methodCallArgType, "BooleanLiteral"))){
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
                                if(!(Objects.equals(methodCallArgType, "StringType"))){
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
                                if(!(Objects.equals(methodCallArgType, "Array")) && !Objects.equals(methodCallArgType, "ArrayInit") && /*TESTE*/ !Objects.equals(methodCallArgType, "FunctionCall") && !Objects.equals(methodCallArgType, "ClassType")){
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
                            case "ClassType":
                                if(!(Objects.equals(methodCallArgType, "ClassType") && Objects.equals(methodDecl.getChild(currIndex + 1).getChild(0).get("name"), methodCall.getChild(currIndex + 1).get("name")))){
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

//                        if(!Objects.equals(methodCall.getChild(currIndex + 1).getKind(), methodParamType))
//                            addReport(Report.newError(
//                                    Stage.SEMANTIC,
//                                    NodeUtils.getLine(methodDecl),
//                                    NodeUtils.getColumn(methodDecl),
//                                    "wrong argument ellipsis method",
//                                    null));
                        temp--;
                    }

                    for(int i = 1 + numParamsBefore; i < methodCall.getNumChildren(); i++){
                        JmmNode node = methodCall.getChild(i);
                        if(Objects.equals(methodCall.getChild(i).getKind(), "VarRefExpr")){
                            node = getActualTypeVarRef(methodCall.getChild(i));
                        }

                        if(!(Objects.equals(node.getKind(), "IntegerType") || Objects.equals(node.getKind(), "IntegerLiteral")))
                            addReport(Report.newError(
                                    Stage.SEMANTIC,
                                    NodeUtils.getLine(methodDecl),
                                    NodeUtils.getColumn(methodDecl),
                                    "wrong argument ellipsis method",
                                    null));
                    }


                }
                numParamsBefore++;
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

    private void checkVarargs(JmmNode methodDecl) {
        JmmNode lastParam = null;
        int varargsCount = 0;
        int totalParams = 0;

        for (JmmNode param : methodDecl.getChildren("Param")) {
            totalParams++;
            if (Objects.equals(param.getChild(0).getKind(), "EllipsisType")) {
                varargsCount++;
                lastParam = param;
            }
        }

        if (varargsCount > 1) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(lastParam),
                    NodeUtils.getColumn(lastParam),
                    "Multiple varargs parameters are not allowed in a method",
                    null));
        } else if (varargsCount == 1 && lastParam != methodDecl.getChildren("Param").get(totalParams - 1)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(lastParam),
                    NodeUtils.getColumn(lastParam),
                    "Varargs parameter must be the last parameter in the method",
                    null));
        }

    }
}

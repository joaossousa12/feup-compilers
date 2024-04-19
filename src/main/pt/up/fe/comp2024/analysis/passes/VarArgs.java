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
        addVisit("FunctionCall", this::visitEllipsisMethod);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
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
            if (Objects.equals(methodDecl.get("name"), methodName)) {
                checkVarargs(methodDecl, methodCall);
            }
        }

        return null;
    }

    private void checkVarargs(JmmNode methodDecl, JmmNode methodCall) {
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

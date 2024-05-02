package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
public class CallToUndeclaredMethod extends AnalysisVisitor {
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

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table){
        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        String methodName = methodCall.get("name");
        int imports_size = table.getImports().size();

        List<String> imports = new ArrayList<>();
        imports  = table.getImports();

        List<String> modifiedImports  = new ArrayList<>();

        for(int i = 0; i < imports_size; i++){
            String[] parts = imports.get(i).substring(1, imports.get(i).length() - 1).split(", ");
            String lastString = parts[parts.length - 1];
            modifiedImports.add(lastString);
        }


        boolean found = false;

        JmmNode classDecl = methodCall;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }

        for(JmmNode node : classDecl.getChildren()){
            if((Objects.equals(node.getKind(), "MethodDecl") || Objects.equals(node.getKind(), "FunctionCall")) && Objects.equals(node.get("name"), methodName)){
                found = true;
                break;
            }
        }

        if(!found){
            JmmNode variable = methodCall.getChild(0);

            if(Objects.equals(variable.getKind(), "VarRefExpr")){
                variable = getActualTypeVarRef(variable, currentMethod);
                if(Objects.equals(variable.getKind(), "VarRefExpr"))
                    variable = getActualTypeVarRef(variable);
            }

            String superr = table.getSuper();

            if(Objects.equals(variable.getKind(), "ClassType")){
                if(!modifiedImports.contains(variable.get("name")))
                    if(superr == null)
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodCall),
                                NodeUtils.getColumn(methodCall),
                                "Call to undeclared method '" + methodName + "'",
                                null)
                        );
            }



            else if(imports_size == 0)
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall),
                        NodeUtils.getColumn(methodCall),
                        "Call to undeclared method '" + methodName + "'",
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
}

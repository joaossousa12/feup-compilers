package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FieldInStaticInvalid extends  AnalysisVisitor{
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.METHOD_DECL, this::visitStaticInvalid);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitStaticInvalid(JmmNode methodDecl, SymbolTable table) {
        if(Objects.equals(methodDecl.get("isStatic"), "true")) {
            List<Symbol> fields = table.getFields();
            List<String> fieldNames = new ArrayList<>();
            for (Symbol symbol : fields) {
                fieldNames.add(symbol.getName());
            }
            for(JmmNode node : methodDecl.getDescendants()){
                if(Objects.equals(node.getKind(), "VarRefExpr")){
                    if(fieldNames.contains(node.get("name")))
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodDecl),
                                NodeUtils.getColumn(methodDecl),
                                "Field in static method",
                                null)
                        );
                }
                else if(Objects.equals(node.getKind(), "AssignStmt")){
                    if(fieldNames.contains(node.get("var")))
                        addReport(Report.newError(
                                Stage.SEMANTIC,
                                NodeUtils.getLine(methodDecl),
                                NodeUtils.getColumn(methodDecl),
                                "Field in static method",
                                null)
                        );
                    else{
                        for(JmmNode assignDescendant : node.getDescendants()){
                            if(Objects.equals(assignDescendant.getKind(), "VarRefExpr")){
                                if(fieldNames.contains(assignDescendant.get("name")))
                                    addReport(Report.newError(
                                            Stage.SEMANTIC,
                                            NodeUtils.getLine(methodDecl),
                                            NodeUtils.getColumn(methodDecl),
                                            "Field in static method",
                                            null)
                                    );
                            }
                        }
                    }
                }
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
}

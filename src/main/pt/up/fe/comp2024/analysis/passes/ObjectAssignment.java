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

public class ObjectAssignment extends AnalysisVisitor {
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

        if(Objects.equals(assign.getChild(0).getKind(), "VarRefExpr")){
            String type1 = null; // type of the variable being assigned
            String type2 = null; // type of the variable being assigned to

            for(JmmNode decl : assign.getParent().getChildren()){
                if(Objects.equals(decl.getKind(), "VarDecl") && decl.get("name").equals(assign.get("var"))){
                    type1=decl.getChild(0).get("name");
                }
                if(Objects.equals(decl.getKind(), "VarDecl") && decl.get("name").equals(assign.getChild(0).get("name"))){
                    type2=decl.getChild(0).get("name");
                }
            }

            if(!Objects.equals(type1, type2)){
                // check if type1 extends type2 or if both types are imported
                String extendClass = table.getSuper();
                List<String> imports = table.getImports();
                List<String> modifiedImports = new ArrayList<>();

                for (String importString : imports) {
                    String[] elements = importString.substring(1, importString.length() - 1).split(",");
                    for (String element : elements) {
                        modifiedImports.add(element.trim());
                    }
                }

                if(!(modifiedImports.contains(type1) && modifiedImports.contains(type2))){
                    if(Objects.equals(extendClass, null) || !Objects.equals(extendClass, type1)){
                        var message = "Assigning a variable of type "+type2+" to a variable of type "+type1+".";
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

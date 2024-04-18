package pt.up.fe.comp2024.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2024.analysis.AnalysisVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

public class MissDuplicated extends AnalysisVisitor {
    private String currentMethod;

    @Override
    public void buildVisitor(){
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.CLASS_DECL, this::visitWholeClass);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitWholeClass(JmmNode classDecl, SymbolTable table) {
        // repeated imports
        List<String> imports = table.getImports();
        List<String[]> modifiedImports = new ArrayList<>();
        for(var import1 : imports){
            String[] parts = import1.substring(1, import1.length() - 1).split(",");
            modifiedImports.add(parts);
        }
        if(hasRepeatedStringArray(modifiedImports))
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDecl),
                    NodeUtils.getColumn(classDecl),
                    "repeated import",
                    null)
            );

        // repeated fields
        List<Symbol> fields = table.getFields();
        for(int i = 0; i < fields.size(); i++){
            for(int j = i + 1; j < fields.size(); j++){
                if(fields.get(i).getName().equals(fields.get(j).getName()))
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            NodeUtils.getLine(classDecl),
                            NodeUtils.getColumn(classDecl),
                            "repeated field",
                            null)
                    );
            }
        }

        // repeated locals
        for(JmmNode methodDecl : classDecl.getChildren()){
            List<String []> vardecls = new ArrayList<>();
            for(JmmNode node : methodDecl.getChildren()){
                if(Objects.equals(node.getKind(), "VarDecl")){
                    String[] h = new String[1];
                    h[0] = node.get("name");
                    vardecls.add(h);
                }
            }

            if(hasRepeatedStringArray(vardecls))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(classDecl),
                        NodeUtils.getColumn(classDecl),
                        "repeated locals",
                        null)
                );
        }

        // repeated method
        List<String []> methodDecls = new ArrayList<>();
        for(JmmNode methodDecl : classDecl.getChildren(Kind.METHOD_DECL)){
            String[] h = new String[1];
            h[0] = methodDecl.get("name");
            methodDecls.add(h);
        }

        if(hasRepeatedStringArray(methodDecls))
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    NodeUtils.getLine(classDecl),
                    NodeUtils.getColumn(classDecl),
                    "repeated methods",
                    null)
            );

        // repeated params
        for(JmmNode methodDecl : classDecl.getChildren()){
            List<String []> paramDecls = new ArrayList<>();
            for(JmmNode node : methodDecl.getChildren(Kind.PARAM)){
                String[] h = new String[1];
                h[0] = node.get("name");
                paramDecls.add(h);
            }

            if(hasRepeatedStringArray(paramDecls))
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        NodeUtils.getLine(classDecl),
                        NodeUtils.getColumn(classDecl),
                        "repeated params",
                        null)
                );
        }


        return null;
    }
    public boolean hasRepeatedStringArray(List<String[]> list) {
        Set<String> set = new HashSet<>();
        for (String[] array : list) {
            String arrayAsString = Arrays.toString(array);
            if (set.contains(arrayAsString)) {
                return true;
            } else {
                set.add(arrayAsString);
            }
        }
        return false;
    }
}

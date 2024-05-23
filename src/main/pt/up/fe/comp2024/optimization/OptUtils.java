package pt.up.fe.comp2024.optimization;

import org.specs.comp.ollir.Instruction;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static pt.up.fe.comp2024.ast.Kind.TYPE;

public class OptUtils {
    private static int tempNumber = -1;

    public static String getTemp() {

        return getTemp("tmp");
    }

    public static String getTemp(String prefix) {

        return prefix + getNextTempNum();
    }

    public static int getNextTempNum() {

        tempNumber += 1;
        return tempNumber;
    }

    public static String toOllirType(JmmNode typeNode) {
        if(Objects.equals(typeNode.getKind(), "VarRefExpr"))
            typeNode = getActualTypeVarRef(typeNode);

        if(Objects.equals(typeNode.getKind(),"Array")){
            return ".array.i32";
        }
        if(Objects.equals(typeNode.getKind(),"IntegerLiteral")){
            return ".i32";
        }
        String typeName = typeNode.get("name");

        String isArray = Objects.equals(typeNode.getKind(), "Array") ? ".array" : "";

        String ollirType = Objects.equals(typeNode.getKind(), "ClassType") ? "." + typeName : toOllirType(typeName);

        return isArray + ollirType;
    }

    public static String toOllirType(Type type) {
        String typeName = type.getName();

        if (typeName.equals("int") || typeName.equals("boolean") || typeName.equals("String") || typeName.equals("void"))
            return (type.isArray() ? ".array" : "") + toOllirType(typeName);

        else if(typeName.equals("true")||typeName.equals("false"))
            return toOllirType("boolean");

        else
            return (type.isArray() ? ".array" : "") + "." + typeName;

    }

    private static String toOllirType(String typeName) {

        String type = "." + switch (typeName) {
            case "int" -> "i32";
            case "String" -> "String";
            case "boolean" -> "bool";
            case "void" -> "V";
            case "import " -> "import ";
            default -> typeName;
        };

        return type;
    }

    public static JmmNode getActualTypeVarRef(JmmNode varRefExpr){
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

package pt.up.fe.comp2024.ast;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;

import java.util.Objects;

public class TypeUtils {

    private static final String INT_TYPE_NAME = "int";
    private static final String BOOL_TYPE_NAME = "boolean";

    private static final String STRING_TYPE_NAME = "String";

    private static final String VOID_TYPE_NAME = "void";

    private static final String IMPORT_TYPE_NAME = "import ";







    public static String getIntTypeName() {
        return INT_TYPE_NAME;
    }
    public static String getBoolTypeName() {return BOOL_TYPE_NAME;}

    public static String getStringTypeName() {return STRING_TYPE_NAME;}
    public static String getImportTypeName() {return IMPORT_TYPE_NAME;}


    public static String getVoidTypeName() {return VOID_TYPE_NAME;}



    /**
     * Gets the {@link Type} of an arbitrary expression.
     *
     * @param expr
     * @param table
     * @return
     */
    public static Type getExprType(JmmNode expr, SymbolTable table) {

        var kind = Kind.fromString(expr.getKind());

        Type type = switch (kind) {
            case BINARY_EXPR -> getBinExprType(expr);
            case BINARY_OP -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr, table);
            case INTEGER_LITERAL -> new Type(INT_TYPE_NAME, false);
            case NEW_CLASS -> new Type(expr.get("name"), false);
            case FUNCTION_CALL -> getFuncCallType(expr);
            case NEW_ARRAY -> new Type(expr.getChild(0).getKind(),true);
            case NEGATION -> new Type(expr.getChild(0).get("value"),false);
            case ARRAY_INIT -> new Type(expr.getChild(0).getKind(),true);
            default -> throw new UnsupportedOperationException("Can't compute type for expression kind '" + kind + "'");
        };

        return type;
    }


    private static Type getBinExprType(JmmNode binaryExpr) {
        // TODO: Simple implementation that needs to be expanded

        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*", "-", "/" -> new Type(INT_TYPE_NAME, false);
            case ">", "<", "&&", "||" -> new Type(BOOL_TYPE_NAME, false);
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }


    private static Type getVarExprType(JmmNode varRefExpr, SymbolTable table) {
        // TODO: Simple implementation that needs to be expanded
        return new Type(INT_TYPE_NAME, false);
    }

    private static Type getFuncCallType(JmmNode callExpr) {
        String methodName = callExpr.get("name");

        JmmNode classDecl = callExpr;
        while (!Objects.equals(classDecl.getKind(), "ClassDecl")) {
            classDecl = classDecl.getParent();
        }
        String retType = null;
        for(JmmNode method : classDecl.getChildren()){
            if(Objects.equals(method.get("name"), methodName)){
                retType = method.getChild(0).getChild(0).getKind();
            }
        }


        return new Type(retType, false);
    }


    /**
     * @param sourceType
     * @param destinationType
     * @return true if sourceType can be assigned to destinationType
     */
    public static boolean areTypesAssignable(Type sourceType, Type destinationType) {
        // TODO: Simple implementation that needs to be expanded
        return sourceType.getName().equals(destinationType.getName());
    }
}

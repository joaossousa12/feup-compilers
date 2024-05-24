package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.Objects;
import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;
    private int counter = 0;
    private int helper = 0;
    private int labelNum = 0;
    private int varArgsCount = 0;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
        this.buildVisitor();
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit("returnStmt", this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit("ExprStmt",this::visitExprStmt);
        addVisit("IfElseStmt",this::visitIfElse);
        addVisit("WhileStmt", this::visitWhile);
        addVisit("ArrayAssign", this::visitArrayAssign);
        addVisit("ArrayInit", this::visitArrayInit);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitWhile(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        JmmNode binOpExpr = node.getChild(0);

        var assigns = exprVisitor.visit(binOpExpr);
        code.append(assigns.getComputation());

        //same que o if basicamente so q com o while assigns
        code.append("if(").append(assigns.getCode()).append(") goto ").append(OptUtils.getWhile()).append(END_STMT).append(NL);

        code.append("goto ").append("end").append(OptUtils.getWhile()).append(END_STMT).append(NL);

        code.append(OptUtils.getWhile()).append(":").append(NL); // hard coded disto tudo ?

        for (var aux : node.getChild(1).getChildren())  // ver o que esta no nested while
            code.append(visit(aux));


        code.append("if(").append(assigns.getCode()).append(") goto ").append(OptUtils.getWhile()).append(END_STMT).append(NL);
        code.append("end").append(OptUtils.getWhile()).append(":").append(NL);

        return code.toString();
    }
    private String visitIfElse(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var assigns = exprVisitor.visit(node.getChild(0)); // sacar os .bools e assigns todos (a expressao basicamente)
        code.append(assigns.getComputation()).append("if(");

        if(assigns.getCode().equals("a.i32"))
            assigns.setCode("a.bool");

        code.append(assigns.getCode()).append(") goto ").append(OptUtils.getIf()).append(END_STMT); // fim do inicio da avaliação dos ifs (debug)

        for (var fields : node.getChild(2).getChildren()) // correr
            code.append(visit(fields));

        code.append("goto ").append("end" + OptUtils.getIf()).append(END_STMT).append(NL).append(OptUtils.getIf()).append(":").append(NL);

        for (var i : node.getChild(1).getChildren())
            code.append(visit(i));

        code.append("end").append(OptUtils.getIf()).append(":").append(NL);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused){
        StringBuilder code = new StringBuilder();
        StringBuilder arrayAccessCode = new StringBuilder();
        StringBuilder funcCallCode = new StringBuilder();

        boolean isStatic = node.getParent().hasAttribute("isStatic") && node.getParent().get("isStatic").equals("true");

        if(node.getChild(0).getNumChildren()>1) {
            if (node.getChild(0).getChild(1).getKind().equals("Length")) {
                String resOllirType = OptUtils.toOllirType(node.getChild(0).getChild(1));
                String type = OptUtils.getTemp() + resOllirType;
                var a = node.getParent().getChild(0).get("name");
                code.append(type).append(ASSIGN).append(".i32 ").append("arraylength(").append(a).append(".array.i32).i32").append(";");
                code.append("\n");
            }
        }

        for(int i = 1; i < node.getChild(0).getNumChildren(); i++) {
            if(node.getChild(0).getChild(i).getKind().equals("BinaryOp")){
                code.append(helperBinary(node.getChild(0).getChild(i)));
            }
        }

        boolean methodFound = false;
        boolean isStatic2 = false;
        String virtualRetType = ".V";

        for(var h : table.getMethods()){
            if(h.equals(node.getChild(0).get("name"))){
                methodFound = true;

                JmmNode classDecl = node;
                while (!classDecl.getKind().equals("ClassDecl"))
                    classDecl = classDecl.getParent();

                for(JmmNode methodDecl : classDecl.getChildren(METHOD_DECL)){
                    if(methodDecl.get("name").equals(node.getChild(0).get("name"))){
                        for(JmmNode ret : methodDecl.getChildren(RETURN_STMT)){
                            virtualRetType = OptUtils.toOllirType(ret.getChild(0));
                        }
                        if(methodDecl.get("isStatic").equals("true")){
                             isStatic2 = true;
                             break;
                         }

                    }
                }

                break;
            }
        }

        if(!methodFound || isStatic2)
            code.append("invokestatic(").append((node.getChild(0).getChild(0).get("name")));
        else
            code.append("invokevirtual(").append((node.getChild(0).getChild(0).get("name"))).append(".").append(table.getClassName());

        code.append(", ");
        code.append("\"" + node.getChild(0).get("name") + "\"");
        for (int i = 1; i < node.getChild(0).getNumChildren(); i++) {
            code.append(", ");
            if (node.getChild(0).getChild(i).getKind().equals("IntegerLiteral")) {
                code.append(node.getChild(0).getChild(i).get("value"));
                code.append(OptUtils.toOllirType(node.getChild(0).getChild(i)));
            }
            else if(node.getChild(0).getChild(i).getKind().equals("BinaryOp")) {
                String typ = getBinaryOpType(node.getChild(0).getChild(i));
                code.append("tmp").append(this.counter-1+this.helper).append(typ);
            }
            else if (node.getChild(0).getChild(i).getKind().equals("Length")) {
                String resOllirType = OptUtils.toOllirType(node.getChild(0).getChild(1));
                String type = OptUtils.getTemp() + resOllirType;
                this.counter++;
                code.append(type);
            } else if(node.getChild(0).getChild(i).getKind().equals("ArrayAccess")) {
                boolean createdAtLeastOneTemp = false;
                if(node.getChild(0).getChild(i).getChild(1).getKind().equals("FunctionCall")) {
                    arrayAccessCode.append(OptUtils.getTemp()).append(".i32 ").append(ASSIGN).append(".i32 ");
                    this.counter++;

                    if(isStatic){
                        arrayAccessCode.append("invokevirtual(").append(node.getChild(0).getChild(i).getChild(1).getChild(0).get("name"));
                        JmmNode typeNode = node.getChild(0).getChild(i).getChild(1).getChild(0);
                        if(typeNode.getKind().equals("VarRefExpr"))
                            typeNode = getActualTypeVarRef(typeNode);
                        arrayAccessCode.append(OptUtils.toOllirType(typeNode)).append(", ");
                        arrayAccessCode.append("\"");
                        arrayAccessCode.append(node.getChild(0).getChild(i).getChild(1).get("name")).append("\"");
                        JmmNode func = node.getChild(0).getChild(i).getChild(1);
                        for(int k = 1; k < func.getNumChildren(); k++){
                            arrayAccessCode.append(", ");
                            if(func.getChild(k).getKind().equals("IntegerLiteral")){
                                arrayAccessCode.append(func.getChild(k).get("value"));
                            }
                            else{
                                arrayAccessCode.append(func.getChild(k).get("name"));

                            }
                            if(func.getChild(k).getKind().equals("IntegerLiteral")){
                                arrayAccessCode.append(".i32");
                            }
                            else{
                                arrayAccessCode.append(OptUtils.toOllirType(getActualTypeVarRef(func.getChild(k))));

                            }
                            arrayAccessCode.append(").i32").append(END_STMT);
                            createdAtLeastOneTemp = true;
                        }
                    }

                } else if(node.getChild(0).getChild(1).getChild(1).getKind().equals("BinaryOp")) {
                    arrayAccessCode.append(NL);
                    arrayAccessCode.append(OptUtils.getTemp()).append(".i32 ").append(ASSIGN);
                    this.counter++;
                    if(node.getChild(0).getChild(1).getChild(1).getChild(0).hasAttribute("value"))
                        arrayAccessCode.append(".i32 ").append(node.getChild(0).getChild(1).getChild(1).getChild(0).get("value"));
                    else
                        arrayAccessCode.append(".i32 ").append("tmp").append(this.counter-1);
                    arrayAccessCode.append(".i32 ").append(node.getChild(0).getChild(1).getChild(1).get("op")).append(".i32 ");
                    arrayAccessCode.append(node.getChild(0).getChild(1).getChild(1).getChild(1).get("value")).append(".i32").append(END_STMT);
                } else if(node.getChild(0).getChild(1).getChild(1).getKind().equals("ArrayAccess")){ //arrayaccess
                    arrayAccessCode.append(NL);
                    arrayAccessCode.append(OptUtils.getTemp()).append(".i32 ").append(ASSIGN).append(".i32 ");
                    this.counter++;
                    arrayAccessCode.append(node.getChild(0).getChild(1).getChild(0).get("name"));
                    arrayAccessCode.append("[");
                    arrayAccessCode.append(node.getChild(0).getChild(1).getChild(1).getChild(1).get("value")).append(".i32");
                    arrayAccessCode.append("].i32");
                    arrayAccessCode.append(END_STMT);
                }
                arrayAccessCode.append(OptUtils.getTemp()).append(".i32 ").append(ASSIGN);
                arrayAccessCode.append(".i32 ").append(node.getChild(0).getChild(1).getChild(0).get("name"));
                arrayAccessCode.append("[");
                if(createdAtLeastOneTemp)
                    arrayAccessCode.append("tmp").append(this.counter-1).append(".i32");
                else{
                    if(node.getChild(0).getChild(1).getChild(1).getKind().equals("VarRefExpr")) {
                        arrayAccessCode.append(node.getChild(0).getChild(1).getChild(1).get("name")).append(".i32");
                    } else if(node.getChild(0).getChild(1).getChild(1).getKind().equals("BinaryOp") || node.getChild(0).getChild(1).getChild(1).getKind().equals("ArrayAccess")) {
                        arrayAccessCode.append("tmp").append(this.counter-1).append(".i32");
                    }
                }
                arrayAccessCode.append("].i32").append(END_STMT);
                this.counter++;
                code.append("tmp").append(this.counter-1).append(".i32");
            }
            else if(node.getChild(0).getChild(i).getKind().equals("FunctionCall")){
                var hello = exprVisitor.visit(node.getChild(0).getChild(i));
                funcCallCode.append(OptUtils.getTemp()).append(".i32 :=.i32 ");
                funcCallCode.append(hello.getCode()).append(END_STMT);
                this.counter++;
                code.append("tmp").append(this.counter).append(".i32");
            }
            else {
                if(exprVisitor.getFlag())
                    code.append("$1.");
                code.append(node.getChild(0).getChild(i).get("name"));
                code.append(OptUtils.toOllirType(getActualTypeVarRef(node.getChild(0).getChild(i))));


            }
        }

        code.append(")");
        code.append(virtualRetType);
        code.append(END_STMT).append(NL);

        arrayAccessCode.append(funcCallCode).append(code);

        return arrayAccessCode.toString();

    }

    private String getBinaryOpType(JmmNode node) {
        Type resType = new Type("boolean", false);
        if(Objects.equals(node.get("op"), "+") || Objects.equals(node.get("op"), "-") || Objects.equals(node.get("op"), "*") || Objects.equals(node.get("op"), "/")){
            resType = new Type("int", false);
        }

        return OptUtils.toOllirType(resType);
    }

    private String helperBinary(JmmNode binaryOp){
        StringBuilder code = new StringBuilder();
        JmmNode help = binaryOp;

        while(help.getChild(1).getKind().equals("BinaryOp") || help.getChild(0).getKind().equals("BinaryOp")){
            if(help.getChild(1).getKind().equals("BinaryOp"))
                help = help.getChild(1);
            else if(help.getChild(0).getKind().equals("BinaryOp"))
                help = help.getChild(0);
        }

        if(help.getParent().getChild(1).getKind().equals("BinaryOp") && help.getParent().getChild(0).getKind().equals("BinaryOp")){
            //explore left first
            JmmNode left = binaryOp.getChild(0);
            while(left.getChild(1).getKind().equals("BinaryOp") || left.getChild(0).getKind().equals("BinaryOp")){
                if(left.getChild(1).getKind().equals("BinaryOp"))
                    left = left.getChild(1);
                else if(left.getChild(0).getKind().equals("BinaryOp"))
                    left = left.getChild(0);
            }


            //explore right
            JmmNode right = binaryOp.getChild(1);
            while(right.getChild(1).getKind().equals("BinaryOp") || right.getChild(0).getKind().equals("BinaryOp")){
                if(right.getChild(1).getKind().equals("BinaryOp"))
                    right = right.getChild(1);
                else if(right.getChild(0).getKind().equals("BinaryOp"))
                    right = right.getChild(0);
            }

            code.append(helper(left));
            code.append(helper(right));


            String op = binaryOp.getChild(0).get("op");
            String type = getBinaryOpType(help);

            code.append(OptUtils.getTemp()).append(type).append(" ");
            code.append(ASSIGN).append(type).append(" ");
            code.append("tmp").append(this.counter-2).append(type).append(" ");
            code.append(op).append(type).append(" ").append("tmp").append(this.counter-1);
            code.append(type).append(END_STMT);

            this.helper = 1;
        }
        else {
            this.helper = 0;
            code.append(helper(help));
        }

        return code.toString();
    }

    private String helper(JmmNode help){
        StringBuilder code = new StringBuilder();
        while (help.getParent().getKind().equals("BinaryOp") || help.getKind().equals("BinaryOp")) {

            if(help.getChild(0).getKind().equals("BinaryOp") && help.getChild(1).getKind().equals("BinaryOp"))
                break;

            String op = help.get("op");
            String type = getBinaryOpType(help);

            String left = help.getChild(0).getKind().equals("BinaryOp") ? "tmp" + (this.counter - 1) : help.getChild(0).get("value");
            String right = help.getChild(1).getKind().equals("BinaryOp") ? "tmp" + (this.counter - 1) : help.getChild(1).get("value");

            code.append(OptUtils.getTemp()).append(type).append(" ");
            code.append(ASSIGN).append(type).append(" ");
            code.append(left).append(type).append(" ");
            code.append(op).append(type).append(" ").append(right).append(type).append(END_STMT);

            this.counter++;

            help = help.getParent();
        }
        return code.toString();
    }

    private String visitArrayInit(JmmNode arrayInit, Void unused){
        StringBuilder code = new StringBuilder();

        //initialize variable
        code.append(OptUtils.getTemp()).append(".array.i32 ");
        code.append(ASSIGN).append(".array.i32 ");
        code.append("new(array, ");
        code.append(arrayInit.getNumChildren()).append(".i32).array.i32").append(END_STMT);
        this.counter++;

        // assign __varargs_array_(this.varArgsCount).array.i32 the temp
        code.append("__varargs_array_").append(this.varArgsCount).append(".array.i32 :=.array.i32 tmp").append(this.counter - 1).append(".array.i32;\n");

        // actual array init now
        int arrayIndex = 0;
        for(JmmNode arrayElement : arrayInit.getChildren()){
            code.append("__varargs_array_").append(this.varArgsCount).append(".array.i32[");
            code.append(arrayIndex++);
            code.append(".i32].i32 :=.i32 ");
            code.append(arrayElement.get("value")).append(".i32").append(END_STMT);
        }

        // attribute varargs to array
        code.append(arrayInit.getParent().get("var"));
        code.append(".array.i32 :=.array.i32 __varargs_array_").append(this.varArgsCount).append(".array.i32").append(END_STMT);

        this.varArgsCount++;

        return code.toString();
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        if(node.getChild(0).getKind().equals("ArrayInit")){
            code.append(visit(node.getChild(0)));
            return code.toString();
        }
        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type type10 = null;
        for (int i = 0; i < table.getLocalVariables(methodName).size(); i++) {
            if(!Objects.equals(node.getChild(0).getKind(), "BinaryOp")) {
                if (table.getLocalVariables(methodName).get(i).getName().equals(node.get("var"))) {
                    code.append(table.getLocalVariables(methodName).get(i).getName());
                    code.append(OptUtils.toOllirType(table.getLocalVariables(methodName).get(i).getType()));
                    type10 = table.getLocalVariables(methodName).get(i).getType();
                }
            }
        }
        //StringBuilder aux = new StringBuilder();
        StringBuilder aux = new StringBuilder();
        var rhs = exprVisitor.visit(node.getJmmChild(0)); // this one



        if(Objects.equals(node.getChild(0).getKind(), "NewClass") && !OptUtils.checkIfInImports(node.getChild(0).get("name"),table)){
            aux = code;
            code = new StringBuilder();
            String resOllirType = OptUtils.toOllirType(node.getChild(0));
            rhs = new OllirExprResult(aux.toString(), aux.toString() + " :=" + resOllirType + " new(" + node.getChild(0).get("name") + ")" + resOllirType + ";\n");
        } else if(Objects.equals(node.getChild(0).getKind(), "NewClass") && OptUtils.checkIfInImports(node.getChild(0).get("name"),table)){
            aux = code;
            code = new StringBuilder();
            String resOllirType = OptUtils.toOllirType(node.getChild(0));
            rhs = new OllirExprResult(aux.toString(), aux.toString() + " :=" + resOllirType + " new(" + node.getChild(0).get("name") + ")" + resOllirType + ";\n");
        }

        code.append(rhs.getComputation());
        boolean t = true;
        if(Objects.equals(node.getChild(0).getKind(), "NewClass") && !OptUtils.checkIfInImports(node.getChild(0).get("name"),table)){
            t = false;
            code.append("invokespecial(").append(rhs.getCode()).append(", \"<init>\").V;\n");
        }
        else if(Objects.equals(node.getChild(0).getKind(), "NewClass") && OptUtils.checkIfInImports(node.getChild(0).get("name"),table)) {
            code.append("invokespecial(").append(rhs.getCode()).append(", \"<init>\").V;\n");
            return code.toString();
        }

        if(Objects.equals(node.getChild(0).getKind(), "NewClass") && t)
            code.append(aux);

        if(node.getJmmChild(0).getKind().equals("FunctionCall")){
            OllirExprResult ollirExprResult = exprVisitor.visit(node.getJmmChild(0));
            code.append(" :=").append(OptUtils.toOllirType(type10)).append(" ").append(ollirExprResult.getCode()).append(END_STMT);
            return code.toString();
        }

        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);

        if(typeString.equals(".IntegerType"))
            typeString = ".i32";
        if(typeString.equals(".array.IntegerLiteral")) typeString = ".array.i32";

        if(Objects.equals(node.getChild(0).getKind(), "BinaryOp")){
            for (int i = 0; i < table.getLocalVariables(methodName).size(); i++) {
                if (table.getLocalVariables(methodName).get(i).getName().equals(node.get("var"))) {
                    code.append(table.getLocalVariables(methodName).get(i).getName());
                    code.append(OptUtils.toOllirType(table.getLocalVariables(methodName).get(i).getType()));
                }
            }
        }
        if(t){
            code.append(SPACE);
            code.append(ASSIGN);
            code.append(typeString);
            code.append(SPACE);
            code.append(rhs.getCode());
        }

        if(Objects.equals(rhs.getCode(), "") && Objects.equals(node.getChild(0).getKind(), "NewClass")){
            code.append("new(").append(node.getChild(0).get("name")).append(")").append(typeString);
        }
        if(Objects.equals(rhs.getCode(), "") && Objects.equals(node.getChild(0).getKind(), "NewArray")) {
            code.append("new(").append("array, "+ node.getChild(0).getChild(0).get("value")).append(".i32").append(")").append(typeString);
        }
        if(Objects.equals(rhs.getCode(), "") && Objects.equals(node.getChild(0).getKind(), "Negation")) {
            code.append("!.bool").append(" "+ node.getChild(0).getChild(0).get("value")).append(typeString);
        }

        if(t)
            code.append(END_STMT);

        if(node.getChild(0).getKind().equals("BinaryOp") && node.getChild(0).get("op").equals("&&")){
            code = new StringBuilder();
            code.append("c.Arithmetic_and :=.Arithmetic_and c.Arithmetic_and").append(END_STMT);
            code.append("if(");

            if(node.getChild(0).getChild(0).getKind().equals("BooleanLiteral")){
                if(node.getChild(0).getChild(0).get("value").equals("true"))
                    code.append("1.bool");
                else
                    code.append("0.bool");
            } else {
                code.append(node.getChild(0).getChild(0).getKind());
            }

            code.append(") goto ");
            code.append("true_").append(this.labelNum).append(END_STMT);
            code.append(OptUtils.getTemp()).append(".bool ");

            this.counter++;
            code.append(ASSIGN).append(".bool 0.bool").append(END_STMT);

            code.append("goto end_").append(this.labelNum).append(END_STMT);
            code.append("true_").append(this.labelNum).append(":").append(NL);

            rhs = exprVisitor.visit(node.getJmmChild(0));
            code.append(rhs.getComputation());
            this.counter++;

            code.append("tmp").append(this.counter-1).append(".bool ").append(ASSIGN).append(".bool tmp").append(this.counter).append(".bool").append(END_STMT);


            code.append("end_").append(this.labelNum).append(":").append(NL);

            code.append(node.get("var")).append(".bool");

            code.append(" ").append(ASSIGN).append(".bool ").append("tmp").append(this.counter - 1).append(".bool").append(END_STMT);

            this.labelNum++;
        }

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");
        boolean isStatic = NodeUtils.getBooleanAttribute(node, "isStatic", "false");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        if(isStatic)
            code.append("static ");

        // name
        var name = node.get("name");
        code.append(name);

        // params
        code.append("(");
        var params  = node.getChildren(PARAM);
        if(params.isEmpty() && Objects.equals(node.get("name"), "main")) {
            code.append("args.array.String");
        }
        for (int i = 0; i < params.size(); i++) {
            if(!params.get(i).getChild(0).getKind().equals("EllipsisType")){
                var paramCode = visit(params.get(i));
                code.append(paramCode);
            } else {
                code.append(params.get(i).get("name")).append(".array.i32");
            }


            var after = i == params.size() - 1 ? "" : ", ";
            code.append(after);
        }
        code.append(")");

        // type


        if(params.isEmpty() && Objects.equals(node.get("name"), "main")) // void on main
            code.append(".V ");

        else
            code.append(OptUtils.toOllirType(node.getChild(0).getChild(0)));

        code.append(L_BRACKET);

        var numParams = table.getParameters(name).size();
        var numLocals = table.getLocalVariables(name).size();
        // rest of its children stmts
        var numReturnStmts = 0;
        for(JmmNode node1 : node.getChildren()) {
            if(Objects.equals(node1.getKind(), "ReturnStmt")){
                numReturnStmts++;
                break;
            }
        }
        for (int i = (numParams + numLocals + numReturnStmts); i < node.getChildren().size(); i++) {
            var childStmt = node.getChild(i);
            var childCode = visit(childStmt);
            code.append(childCode);
        }

        if(params.isEmpty() && Objects.equals(node.get("name"), "main"))
            code.append("ret.V;").append(NL);

        else{
            JmmNode returnStmt = node.getChild(node.getNumChildren() - 1);
            if(Objects.equals(returnStmt.getChild(0).getKind(), "IntegerLiteral"))
                code.append("ret").append(OptUtils.toOllirType(node.getChild(0).getChild(0))).append(" ").append(returnStmt.getChild(0).get("value")).append(".i32;").append(NL);
            else if(Objects.equals(returnStmt.getChild(0).getKind(), "BinaryOp")) {
                var rhs = exprVisitor.visit(returnStmt.getJmmChild(0));
                code.append(rhs.getComputation());
                code.append("ret");
                Type retType = TypeUtils.getExprType(returnStmt.getChild(0), table);
                code.append(OptUtils.toOllirType(retType)).append(" ");
                code.append(rhs.getCode()).append(";\n");
            }
            else if(Objects.equals(returnStmt.getChild(0).getKind(),"VarRefExpr")){
                String type = OptUtils.toOllirType(node.getChild(0).getChild(0));
                code.append("ret").append(type).append(" ").append(returnStmt.getChild(0).get("name")).append(type).append(";").append(NL);
            }
            else if(Objects.equals(returnStmt.getChild(0).getKind(),"BooleanLiteral")) {
                String returnValue = returnStmt.getChild(0).get("value");
                if(returnValue.equals("true")){
                    returnValue = "1";
                }
                else{
                    returnValue = "0";
                }
                code.append("ret").append(OptUtils.toOllirType(node.getChild(0).getChild(0))).append(" ").append(returnValue).append(".bool").append(";").append(NL);
            }
            else if(Objects.equals(returnStmt.getChild(0).getKind(),"ArrayAccess")) {
                var test = exprVisitor.visit(returnStmt.getChild(0)).getCode();
                int idLength = returnStmt.getChild(0).getChild(0).get("name").length();
                String firstPart = test.substring(0, idLength + 3);
                String secondPart = test.substring(idLength + 3);
                code.append("ret.i32 ").append(firstPart).append(".array.i32").append(secondPart).append(END_STMT);
            }

            else
                code.append("ret").append(OptUtils.toOllirType(node.getChild(0).getChild(0))).append(" ").append(returnStmt.getChild(0).get("name")).append(OptUtils.toOllirType(returnStmt.getChild(0))).append(";").append(NL);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }



    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();
        String teste = "null";
        var imports = table.getImports();
        if (!imports.isEmpty()) {
            for (var import1 : imports) {
                code.append("import ");
                String[] parts = import1.substring(1, import1.length() - 1).split(",");

                for (int i = 0; i < parts.length; i++) {
                    String trimmedPart = parts[i].trim();
                    code.append(trimmedPart);
                    if (i < parts.length - 1) {
                        code.append(".");
                    }
                }

                code.append(";").append(NL);
            }
        }
        code.append(table.getClassName());
        code.append(" extends ");
        if(node.hasAttribute("extendClassName")) {
            code.append(table.getSuper());
        }
        else{
            code.append("Object");
        }

        code.append(L_BRACKET);

        code.append(NL);
        for(JmmNode varDecl : node.getChildren(VAR_DECL)) {
            String name = varDecl.get("name");
            String type = OptUtils.toOllirType(varDecl.getChild(0));

            code.append(".field").append(SPACE).append("public").append(SPACE).append(name).append(type).append(";").append(NL);
        }

        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }


    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        //code.append(helpImports()).append(NL);

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private String visitArrayAssign(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        code.append("$1."); // test
        code.append(node.get("var"));
        code.append("[");
        code.append(node.getChild(0).get("value")).append(".i32].i32 ");
        code.append(ASSIGN).append(".i32 ");
        code.append(node.getChild(1).get("value")).append(".i32").append(END_STMT);



        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
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

package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";
    private static final String STACK_PLACEHOLDER = "STACK_LIMIT_PLACEHOLDER";

    private final OllirResult ollirResult;

    int stack_size;
    int current_stack_size;
    List<Report> reports;

    String code;

    Method currentMethod;

    int stackNum;
    int currStackNum;
    int callArgumentsNum;

    Field field;
    ClassUnit classUnit;
    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(Field.class, this::generateField);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(CondBranchInstruction.class, this::generateBranch);
        generators.put(GotoInstruction.class, this::generateGoto);
    }
    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();
        this.classUnit = classUnit;

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class public ").append(className).append(NL);

        if (ollirResult.getOllirClass().getSuperClass() != null && !Objects.equals(classUnit.getSuperClass(), "Object")) {
            var superClassName = ollirResult.getOllirClass().getSuperClass();
            code.append(".super ").append(superClassName).append(NL).append(NL);
        }

        else
            code.append(".super java/lang/Object").append(NL).append(NL);


        for (var field : ollirResult.getOllirClass().getFields()) // get fields
            code.append(generators.apply(field));

        code.append(NL);

        // generate a single constructor method
        var buildConstructor = """
                .method public <init>()V
                    aload_0
                """;

        var finishConstruct ="""
                    return
                .end method
                """;
        code.append(buildConstructor); // Nl deleted

        code.append(TAB).append(" ").append("invokespecial "); //continuar a partir do invokeespecial

        if (ollirResult.getOllirClass().getSuperClass() != null && !Objects.equals(classUnit.getSuperClass(), "Object")) {
            var superName = ollirResult.getOllirClass().getSuperClass();
            if (!superName.isEmpty()) code.append(superName);

            else {
                for (var imports : ollirResult.getOllirClass().getImports()) {
                    if (imports.endsWith(className)) {
                        var test = imports.replace(".", "/");
                        code.append(test);
                    }
                }

            }
        }
        else
            code.append("java/lang/Object");




        code.append("/<init>()V").append(NL);

        code.append(finishConstruct);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }
        this.classUnit = null;
        return code.toString();
    }

    private String generateField(Field field) {
        var code = new StringBuilder();
        this.field = field;

        var fieldName = field.getFieldName();
        String jasminType = getJasminType(field.getFieldType());

        var modifier = field.getFieldAccessModifier();

        String modifierName;
        modifierName = switch (modifier) {
            case PUBLIC -> "public";
            case PRIVATE -> "private";
            case PROTECTED -> "protected";
            case DEFAULT -> "";
        };

        code.append(".field ").append(modifierName).append(" ");

        if (field.isStaticField())
            code.append("static ");

        if (field.isFinalField())
            code.append("final ");

        this.field = null;
        code.append(fieldName).append(" ").append(jasminType);

        if (field.isInitialized())
            code.append(" = ").append(field.getInitialValue());

        code.append(NL);

        return code.toString();
    }

    private String getJasminType(Type type){
        var code = new StringBuilder();
        ElementType elementType = type.getTypeOfElement();

        return switch (elementType) {
            case INT32 -> code.append("I").toString();
            case BOOLEAN -> code.append("Z").toString();
            case VOID -> code.append("V").toString();
            case STRING -> code.append("Ljava/lang/String;").toString();
            case OBJECTREF -> {
                String className = ((ClassType) type).getName();
                yield code.append("L").append(className).append(";").toString();
            }
            case ARRAYREF -> code.append("[I").toString();
            case CLASS -> code.append("L").append(getQualifiedImports(elementType.toString())).append(";").toString();
            default -> "Type" + elementType + "not implemented";
        };
    }


    private String generateMethod(Method method) {

        this.resetStack();
        // set method
        currentMethod = method;

        var code = new StringBuilder();

        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();

        code.append("\n.method ").append(modifier);

        if(method.isFinalMethod())
            code.append("final ");

        else if(method.isStaticMethod())
            code.append("static ");

        code.append(methodName).append("(");

        if (methodName.equals("main"))
            code.append("[Ljava/lang/String;");

        else
            for(Element elem : method.getParams())
                code.append(getJasminType(elem.getType()));

        code.append(")");

        code.append(getJasminType(method.getReturnType())).append(NL);

        // calculate locals
        Set<Integer> vRegs = new TreeSet<>();
        vRegs.add(0);

        for(Descriptor var : method.getVarTable().values())
            vRegs.add(var.getVirtualReg());

        this.currStackNum = 0;
        this.stackNum = 0;

        code.append(TAB).append(".limit stack ").append(STACK_PLACEHOLDER).append(NL);
        code.append(TAB).append(".limit locals ").append(vRegs.size()).append(NL);

        for (var inst : method.getInstructions()) {
            for(Map.Entry<String,Instruction> label : method.getLabels().entrySet()){
                if(label.getValue().equals(inst)) code.append(label.getKey()).append(":\n");
            }
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            code.append(instCode);

            if (inst.getInstType() == InstructionType.CALL && ((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID) {
                code.append(TAB).append("pop").append(NL);
                this.currStackNum--;

            }
        }

        code.append(".end method\n");

        String finalCode = code.toString().replace(STACK_PLACEHOLDER, String.valueOf(this.stackNum));

        // unset method
        currentMethod = null;

        return finalCode;
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();
        String extracode = "";
        if(assign.getDest() instanceof ArrayOperand op){
            code.append("aload");
            this.pushStack(1);

            if(Objects.equals(op.getName(), "this"))
                code.append("_0").append(NL);
            else{
                var reg = currentMethod.getVarTable().get(op.getName()).getVirtualReg();
                if(reg < 4)
                    code.append("_");
                else
                    code.append(" ");
                code.append(reg).append(NL);
            }

            code.append(generators.apply(op.getIndexOperands().get(0)));
        }

        boolean flag2 = true;

        if(assign.getRhs() instanceof SingleOpInstruction){
            var l = ((SingleOpInstruction) assign.getRhs()).getSingleOperand();

            if(l instanceof ArrayOperand op){ //Array Access
                code.append("aload");
                this.pushStack(1);

                if(Objects.equals(op.getName(), "this"))
                    code.append("_0").append(NL);
                else{
                    var reg = currentMethod.getVarTable().get(op.getName()).getVirtualReg();
                    if(reg < 4)
                        code.append("_");
                    else
                        code.append(" ");
                    code.append(reg).append(NL);
                }

                code.append(generators.apply(((ArrayOperand) l).getIndexOperands().get(0)));

                code.append("iaload ").append(NL);
                this.popStack(1);
                flag2 = false;
            }

        }

        code.append(extracode);
        if(flag2)
            code.append(generators.apply(assign.getRhs()));


        var lhs = assign.getDest();


        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        if(operand instanceof ArrayOperand){
            this.popStack(3);
            return code.append("iastore").append(NL).toString();
        }


        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        ElementType elemType = operand.getType().getTypeOfElement();

        if(elemType == ElementType.INT32 || elemType == ElementType.BOOLEAN){
            //this.pushStack(1);
            return code.append("istore ").append(reg).append(NL).toString();
        }
        else if (elemType == ElementType.OBJECTREF || elemType == ElementType.ARRAYREF || elemType == ElementType.STRING || elemType == ElementType.THIS){
            this.popStack(1);
            return code.append("astore ").append(reg).append(NL).toString();
        }
        else
            return "Error in generate assign!";

    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();

        Element op1 = putFieldInstruction.getOperands().get(0);
        Element op2 = putFieldInstruction.getOperands().get(1);
        Element op3 = putFieldInstruction.getOperands().get(2);

        code.append(generators.apply(op1));
        code.append(generators.apply(op3));

        code.append("\tputfield ");
        this.popStack(2);
        code.append(getQualifiedImports(ollirResult.getOllirClass().getClassName())).append("/");
        code.append(((Operand) op2).getName()).append(" ").append(getJasminType(op2.getType())).append(NL);

        return code.toString();
    }

    private String generateGetFieldInstruction(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();

        Element op1 = getFieldInstruction.getOperands().get(0);
        Element aux = getFieldInstruction.getOperands().get(1);

        code.append(generators.apply(op1));
        code.append("\tgetfield ");

        String className = getQualifiedImports(getFieldInstruction.getObject().getName());

        code.append(className).append("/");
        code.append(((Operand) aux).getName()).append(" ").append(getJasminType(aux.getType())).append(NL);

        return code.toString();
    }


    private String generateCall(CallInstruction callInstruction){
        this.callArgumentsNum = 0;
        CallType type = callInstruction.getInvocationType();
        boolean flag = true;
        String result = "";

        if (type == CallType.invokevirtual) {
            var className = getQualifiedImports(((ClassType) callInstruction.getCaller().getType()).getName());
            var code = new StringBuilder();
            this.callArgumentsNum = 0;
            code.append(generators.apply(callInstruction.getOperands().get(0)));

            if(!(callInstruction.getReturnType().getTypeOfElement() == ElementType.VOID))
                this.callArgumentsNum--;

            for(Element ignored : callInstruction.getOperands())
                this.callArgumentsNum++;

            for (var argument : callInstruction.getArguments())
                code.append(generators.apply(argument));


            code.append("invokevirtual ");

            var firstOp = (Operand) callInstruction.getOperands().get(0);
            var name = ((ClassType) firstOp.getType()).getName();


            if(name.equals("this"))
                code.append(className);

            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(className)) {
                        name.replace(".", "/");
                    }
                }
            }

            code.append(name).append("/");
            result = getString(callInstruction, code);


        } else if (type == CallType.invokespecial) {
            var code = new StringBuilder();
            String className;

            this.callArgumentsNum = 1; // not sure

            if(!(callInstruction.getReturnType().getTypeOfElement() == ElementType.VOID))
                this.callArgumentsNum--;

            for(Element ignored : callInstruction.getOperands())
                this.callArgumentsNum++;

            if (callInstruction.getCaller().getType().getTypeOfElement() == ElementType.THIS)
                className = ((ClassType) callInstruction.getCaller().getType()).getName();

            else
                className = getQualifiedImports(((ClassType) callInstruction.getCaller().getType()).getName());

            code.append(generators.apply(callInstruction.getOperands().get(0))).append("invokespecial ");

            if (callInstruction.getArguments().isEmpty() || callInstruction.getArguments().get(0).getType().getTypeOfElement().equals(ElementType.THIS))
                code.append(getQualifiedImports(className));

            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(className)) {
                        className.replace(".", "/");
                    }
                }
                code.append(getQualifiedImports(className));
            }

            code.append("/<init>(");
            for (var argument :callInstruction.getArguments()){
                code.append(generators.apply(argument));
            }

            code.append(")");
            var retType = getJasminType(callInstruction.getReturnType());
            code.append(retType).append(NL);

            result = code.toString();
        }

        else if (type == CallType.invokestatic) {
            this.callArgumentsNum = 0;
            var code = new StringBuilder();

            if(!(callInstruction.getReturnType().getTypeOfElement() == ElementType.VOID))
                this.callArgumentsNum--;

            for(Element ignored : callInstruction.getOperands())
                this.callArgumentsNum++;

            String className;

            if (callInstruction.getCaller().getType().getTypeOfElement() == ElementType.THIS)
                className = ((ClassType) callInstruction.getCaller().getType()).getName();

            else
                className = getQualifiedImports(((Operand) callInstruction.getCaller()).getName());


            for (var argument : callInstruction.getArguments())
                code.append(generators.apply(argument));


            code.append("invokestatic ");
            var firstOp = (Operand) callInstruction.getOperands().get(0);
            var name = firstOp.getName();


            if (name.equals("this"))
                code.append(getQualifiedImports(className));

            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(name)) {
                        name.replace(".", "/");
                    }
                }
                code.append(getQualifiedImports(className));
            }

            code.append("/");
            result = getString(callInstruction, code);

        }

        else if (type == CallType.NEW) {
            var code = new StringBuilder();
            String className = "";
            this.callArgumentsNum = 0;

            if (callInstruction.getCaller().getType().getTypeOfElement() == ElementType.THIS)
                className = ((ClassType) callInstruction.getCaller().getType()).getName();

            else if(callInstruction.getCaller().getType().getTypeOfElement() == ElementType.ARRAYREF){
                for (var argument : callInstruction.getArguments())
                    code.append(generators.apply(argument));

                code.append("newarray int\n");
                this.pushStack(1);
                flag = false;
            }

            else
                className = getQualifiedImports(((ClassType) callInstruction.getCaller().getType()).getName());

            if(flag){
                for (var argument : callInstruction.getArguments())
                    code.append(generators.apply(argument));
            }

            if (Objects.equals(callInstruction.getReturnType().getTypeOfElement().name(), "OBJECTREF")){
                this.pushStack(2); // new and dup
                code.append("new ").append(getQualifiedImports(className)).append(NL).append("dup").append(NL);
            }


            result = code.toString();
        }

        else if(Objects.equals(type.toString(), "arraylength")){
            result = generators.apply(callInstruction.getOperands().get(0)) + "arraylength" + NL;
        }


        return result;
    }

    private String getString(CallInstruction callInstruction, StringBuilder code) {
        var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
        code.append(methodName);
        code.append("(");

        for (var element :callInstruction.getArguments()){
            code.append(getJasminType(element.getType()));
        }

        code.append(")");
        var retType = getJasminType(callInstruction.getReturnType());
        code.append(retType).append(NL);

        return code.toString();
    }


    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        var code = new StringBuilder();
        if (literal.getType().getTypeOfElement() != ElementType.INT32 && literal.getType().getTypeOfElement() != ElementType.BOOLEAN)
            return "ldc " + literal.getLiteral() + NL;

        else {
            var value = Integer.parseInt(literal.getLiteral());

            if (value >= -1 && value <= 5)
                code.append("iconst_"); // already treated on the end of the method

            else if (value >= -128 && value <= 127)
                code.append("bipush "); // already treated on the end of the method

            else if (value >= -32768 && value <= 32767)
                code.append("sipush "); // already treated on the end of the method

            else code.append("ldc "); // already treated on the end of the method



            if (value == -1)
                code.append("m1"); // already treated on the end of the method

            else
                code.append(value);

            code.append(NL);

        }
        this.pushStack(1);
        return code.toString();
    }

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        if(operand.getType().getTypeOfElement() == ElementType.INT32 || operand.getType().getTypeOfElement() == ElementType.BOOLEAN){
            String temp = " ";
            if(reg < 4)
                temp = "_";
            this.pushStack(1);
            return "iload" + temp + reg + NL;
        }

        else if(operand.getType().getTypeOfElement() == ElementType.OBJECTREF || operand.getType().getTypeOfElement() == ElementType.ARRAYREF || operand.getType().getTypeOfElement() == ElementType.STRING || operand.getType().getTypeOfElement() == ElementType.CLASS){
            String temp = " ";
            if(reg < 4)
                temp = "_";
            this.pushStack(1);
            return "aload" + temp + reg + NL;
        }

        else if (operand.getType().getTypeOfElement() == ElementType.THIS){
            //this.pushStack(1);
            return "aload_" + reg + NL;
        }

        else
            return "Error generate operand!";
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd\n"; //treated
            case MUL -> "imul\n"; // treated
            case DIV -> "idiv\n"; // treated
            case SUB -> "isub\n"; // treated
            case LTH -> helperBinaryOpLTH(binaryOp); // treated
            case GTE -> helperGTE(binaryOp); // treated
            case ANDB -> helperAndB(binaryOp); // treated
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        if(binaryOp.getOperation().getOpType() == OperationType.ADD || binaryOp.getOperation().getOpType() == OperationType.MUL || binaryOp.getOperation().getOpType() == OperationType.DIV || binaryOp.getOperation().getOpType() == OperationType.SUB)
            this.popStack(2);

        code.append(op);
        return code.toString();
    }

    public String generateUnaryOp(UnaryOpInstruction unaryOp){
        var code = new StringBuilder();
        code.append(generators.apply(unaryOp.getOperand()));
        code.append("iconst_1\n");
        if(unaryOp.getOperation().getOpType() == OperationType.NOT || unaryOp.getOperation().getOpType() == OperationType.NOTB) {
            this.popStack(1);
            code.append("ixor\n");
        }
        return code.toString();
    }


    private String helperGTE(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        if(binaryOp.getLeftOperand().isLiteral() && binaryOp.getRightOperand().isLiteral()){
            this.popStack(2);
            code.append("if_icmpge ");
        }
        else if(binaryOp.getLeftOperand().isLiteral()){
            this.popStack(1);
            code.append("ifle ");
        }
        else if(binaryOp.getRightOperand().isLiteral()){
            this.popStack(1);
            code.append("ifge ");
        }
        else if(!binaryOp.getLeftOperand().isLiteral() && !binaryOp.getRightOperand().isLiteral()){
            this.popStack(2);
            code.append("if_icmpge ");
        }

        return code.toString();
    }

    private String helperAndB(BinaryOpInstruction binaryOp){
        var code = new StringBuilder();
        this.popStack(1);
        code.append("ifne");
        code.append(generators.apply(binaryOp));

        return code.toString();
    }
    private String helperBinaryOpLTH(BinaryOpInstruction binaryOp){
        var code = new StringBuilder();



        if(binaryOp.getLeftOperand().isLiteral() && binaryOp.getRightOperand().isLiteral()){
            this.popStack(2);
            code.append("if_icmplt ");
        }
        else if(binaryOp.getLeftOperand().isLiteral()){
            this.popStack(1);
            code.append("ifgt ");
        }
        else if(binaryOp.getRightOperand().isLiteral()){
            this.popStack(2);
            code.append("if_icmplt ");
        }
        else if(!binaryOp.getLeftOperand().isLiteral() && !binaryOp.getRightOperand().isLiteral()){
            this.popStack(2);
            code.append("if_icmplt ");
        }



        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        if(returnInst.getReturnType().getTypeOfElement() == ElementType.VOID)
            return code.append("return").append(NL).toString();

        code.append(generators.apply(returnInst.getOperand()));

        ElementType elemType = returnInst.getReturnType().getTypeOfElement();

        if(elemType == ElementType.INT32 || elemType == ElementType.BOOLEAN)
            return code.append("ireturn").append(NL).toString();

        else if(elemType == ElementType.OBJECTREF)
            return code.append("areturn").append(NL).toString();

        else
            return "Error generate return!";
    }

    private String getQualifiedImports(String className){

        if(Objects.equals(className, "this"))
            return ollirResult.getOllirClass().getClassName();

        for(var importedClassName:ollirResult.getOllirClass().getImports())
            if(importedClassName.endsWith("." + className))
                return importedClassName.replace(".", "/");

        return className;
    }

    private String generateGoto(GotoInstruction gotoInstruction){
        var code = new StringBuilder();

        code.append("goto ").append(gotoInstruction.getLabel()).append("\n");

        return code.toString();
    }
    private String generateBranch(CondBranchInstruction condBranchInstruction){
        var code = new StringBuilder();
        if(condBranchInstruction instanceof OpCondInstruction instruction) code.append(generateOpCond(instruction));
        if(condBranchInstruction instanceof SingleOpCondInstruction instruction) code.append(generateSingleOpCond(instruction));



        return code.toString();
    }

    private String generateOpCond(OpCondInstruction OpCond) {
        var code = new StringBuilder();

        InstructionType opType = OpCond.getCondition().getInstType();
        String label = OpCond.getLabel(); // label penso q é preciso pq debug

        if (opType == InstructionType.BINARYOPER) code.append(generateBinaryOp((BinaryOpInstruction) OpCond.getCondition()));
        else if (opType == InstructionType.UNARYOPER)code.append(generateUnaryOp((UnaryOpInstruction) OpCond.getCondition()));
        else {
            this.popStack(1);
            code.append("ifne").append(generators.apply(OpCond.getCondition()));
        }

        code.append(label).append("\n");
        return code.toString();
    }
    private String generateSingleOpCond(SingleOpCondInstruction singleOpCond){
        var code = new StringBuilder();
        this.popStack(1);
        code.append(generators.apply(singleOpCond.getCondition())).append("ifne ").append(singleOpCond.getLabel());


        return code.toString();
    }

    private void popStack(int amount){
        this.currStackNum -= amount;
    }

    private void pushStack(int amount){
        this.currStackNum += amount;

        this.stackNum = Math.max(this.currStackNum, this.stackNum);
    }

    private void resetStack(){
        this.currStackNum = 0;
        this.stackNum = 0;
    }

}

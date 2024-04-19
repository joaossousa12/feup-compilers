package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

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
        generators.put(ReturnInstruction.class, this::generateReturn);
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
        code.append(".class ").append(className).append(NL);

        if (ollirResult.getOllirClass().getSuperClass() != null && !Objects.equals(classUnit.getSuperClass(), "Object")) {
            var superClassName = ollirResult.getOllirClass().getSuperClass();
            code.append(".super ").append(superClassName).append(NL).append(NL);
        } else {
            code.append(".super java/lang/Object").append(NL).append(NL);
        }

        for (var field : ollirResult.getOllirClass().getFields()){ // get fields
            code.append(generators.apply(field));
        }
        code.append(NL);

        // generate a single constructor method
        var buildConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    """;

        var finishConstruct ="""
                    return
                .end method
                """;
        code.append(buildConstructor); // Nl deleted
        code.append(TAB).append(" ").append("invokespecial ");
        if (ollirResult.getOllirClass().getSuperClass() != null && !Objects.equals(classUnit.getSuperClass(), "Object")) {
            var superName = ollirResult.getOllirClass().getSuperClass();
            code.append(superName);
        } else {
            code.append("java/lang/Object");
        }
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

        if (field.isStaticField()) {
            code.append("static ");
        }

        if (field.isFinalField()) {
            code.append("final ");
        }

        this.field = null;
        code.append(fieldName).append(" ").append(jasminType);

        if (field.isInitialized()) {
            code.append(" = ").append(field.getInitialValue());
        }
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
            default -> "Type" + elementType + "not implemented";
        };
    }

    private String getObjectType(Type type) {
        var code = new StringBuilder();
        var name = ((ClassType) type).getName();
        var className = ollirResult.getOllirClass().getClassName();
        code.append("L");
        if (name.equals("this")){
            code.append(className);
            return code.toString();
        }

        for (var imports : ollirResult.getOllirClass().getImports()) {
            if (imports.endsWith(className)) {
                imports.replaceAll("\\.", "/");
                code.append(imports);
                return code.toString();
            }
        }

        code.append(name);
        return code.toString();
    }


    private String generateMethod(Method method) {

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

        if (methodName.equals("main")) {
            code.append("[Ljava/lang/String;");
        } else {
            for(Element elem : method.getParams()) {
                code.append(getJasminType(elem.getType()));
            }
        }

        code.append(")");

        code.append(getJasminType(method.getReturnType())).append(NL);




        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            code.append(instCode);
            if (inst.getInstType() == InstructionType.CALL // pop case TUDO O QUE VAI PARA A STACK TEM Q SAIR (OLLIR STACK NEUTRAL)
                    && ((CallInstruction) inst).getReturnType().getTypeOfElement() != ElementType.VOID) {
                code.append(TAB).append("pop").append(NL);
            }
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();


        code.append(generators.apply(assign.getRhs()));


        var lhs = assign.getDest();


        if (!(lhs instanceof Operand operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        if(operand instanceof ArrayOperand)
            return code.append("iastore").append(NL).toString();

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        ElementType elemType = operand.getType().getTypeOfElement();

        if(elemType == ElementType.INT32 || elemType == ElementType.BOOLEAN)
            return code.append("istore ").append(reg).append(NL).toString();

        else if(elemType == ElementType.OBJECTREF)
            return code.append("astore ").append(reg).append(NL).toString();

        else
            return "Error in generate assign!";

    }

    private String generatePutFieldInstruction(PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();

        Element first = putFieldInstruction.getOperands().get(0);
        Element second = putFieldInstruction.getOperands().get(1);
        Element third = putFieldInstruction.getOperands().get(2);

        code.append(generators.apply(first));
        code.append(generators.apply(third));

        code.append("\tputfield ");

        code.append(ollirResult.getOllirClass().getClassName()).append("/");
        code.append(((Operand) second).getName()).append(" ").append(getJasminType(second.getType())).append(NL);

        return code.toString();
    }

    private String generateGetFieldInstruction(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();

        Element first = getFieldInstruction.getOperands().get(0);
        Element second = getFieldInstruction.getOperands().get(1);

        code.append(generators.apply(first));
        code.append("\tgetfield ");

        code.append(ollirResult.getOllirClass().getClassName()).append("/");
        code.append(((Operand) second).getName()).append(" ").append(getJasminType(second.getType())).append(NL);

        return code.toString();
    }


    private String generateCall(CallInstruction callInstruction){
        CallType type = callInstruction.getInvocationType();

        if (type == CallType.invokevirtual) {
            var code = new StringBuilder();
            var className = ollirResult.getOllirClass().getClassName();
            code.append(generators.apply(callInstruction.getOperands().get(0)));

            for (var argument : callInstruction.getArguments()) {
                code.append(generators.apply(argument));
            }

            code.append("invokevirtual ");
            var first = (Operand) callInstruction.getOperands().get(0);
            var firstName = ((ClassType) first.getType()).getName();


            if(firstName.equals("this")){
                code.append(className);
            }
            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(className)) {
                        firstName.replaceAll("\\.", "/");
                    }
                }
            }
            code.append(firstName).append("/");

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



        } else if (type == CallType.invokeinterface) {
            var code = new StringBuilder();
            code.append("invokeinterface ");

            return code.toString();
        } else if (type == CallType.invokespecial) {
            var code = new StringBuilder();

            code.append(generators.apply(callInstruction.getOperands().get(0)));

            code.append("invokespecial ");
            var className = ollirResult.getOllirClass().getClassName();
            if (callInstruction.getArguments().isEmpty()
                    || callInstruction.getArguments().get(0).getType().getTypeOfElement().equals(ElementType.THIS)){
                code.append(className);
            }
            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(className)) {
                        className.replaceAll("\\.", "/");
                    }
                }
                code.append(className);
            }
            code.append("/<init>(");
            for (var agr :callInstruction.getArguments()){
                code.append(generators.apply(agr));
            }
            code.append(")");
            var retType = getJasminType(callInstruction.getReturnType());
            code.append(retType).append(NL);

            return code.toString();
        } else if (type == CallType.invokestatic) {
            var code = new StringBuilder();
            var className = ollirResult.getOllirClass().getClassName();

            for (var argument : callInstruction.getArguments()) {
                code.append(generators.apply(argument));
            }

            code.append("invokestatic ");
            var first = (Operand) callInstruction.getOperands().get(0);
            var firstName = first.getName();


            if (firstName.equals("this")){
                code.append(className);
            }
            else {
                for (var importClass : ollirResult.getOllirClass().getImports()) {
                    if (importClass.endsWith(firstName)) {
                        firstName.replaceAll("\\.", "/");
                    }
                }
                code.append(firstName);
            }

            code.append("/");
            var methodName = ((LiteralElement) callInstruction.getMethodName()).getLiteral().replace("\"", "");
            code.append(methodName);
            code.append("(");
            for (var agr :callInstruction.getArguments()){
                code.append(getJasminType(agr.getType()));
            }
            code.append(")");
            var retType = getJasminType(callInstruction.getReturnType());
            code.append(retType).append(NL);

            return code.toString();

        } else if (type == CallType.NEW) {
            var code = new StringBuilder();
            for (var agr : callInstruction.getArguments()){
                code.append(generators.apply(agr));
            }
            if (callInstruction.getReturnType().getTypeOfElement().name().equals("OBJECTREF")) {
                var className = ollirResult.getOllirClass().getClassName();
                code.append("new ").append(className).append(NL);
                code.append("dup").append(NL);
            }

            return code.toString();
        } else if (type == CallType.arraylength) {
            var code = new StringBuilder();
            code.append(generators.apply(callInstruction.getArguments().get(0)));
            code.append("arraylenght").append(NL);

            return code.toString();
        } else if (type == CallType.ldc) {
            var code = new StringBuilder();
            code.append(generators.apply(callInstruction.getArguments().get(0))).append(NL);

            return code.toString();
        } else {
            code = null; // ERRO
        }

        return code;
    }




    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        var code = new StringBuilder();
        if (literal.getType().getTypeOfElement() != ElementType.INT32 && literal.getType().getTypeOfElement() != ElementType.BOOLEAN) {
            return "ldc " + literal.getLiteral() + NL;
        } else {
            var value = Integer.parseInt(literal.getLiteral());
            if (this.between(value, -1, 5)) code.append("iconst_");
            else if (this.between(value, -128, 127)) code.append("bipush ");
            else if (this.between(value, -32768, 32767)) code.append("sipush ");
            else code.append("ldc ");

            if (value == -1) code.append("m1");
            else code.append(value);

            code.append(NL);

        }
        return code.toString();
    }

    private String generateOperand(Operand operand) {
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        if(operand.getType().getTypeOfElement() == ElementType.INT32 || operand.getType().getTypeOfElement() == ElementType.BOOLEAN)
            return "iload " + reg + NL;

        else if(operand.getType().getTypeOfElement() == ElementType.OBJECTREF || operand.getType().getTypeOfElement() == ElementType.ARRAYREF || operand.getType().getTypeOfElement() == ElementType.STRING || operand.getType().getTypeOfElement() == ElementType.CLASS)
            return "aload " + reg + NL;

        else if (operand.getType().getTypeOfElement() == ElementType.THIS)
            return "aload_" + reg + NL;

        else
            return "Error generate operand!";
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case DIV -> "idiv";
            case SUB -> "isub";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

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

    private boolean between(int value, int lower, int upper) {
        return value <= upper && value >= lower;
    }

}

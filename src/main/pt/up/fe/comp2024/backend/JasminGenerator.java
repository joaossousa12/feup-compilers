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

    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(PutFieldInstruction.class, this::generatePutFieldInstruction);
        generators.put(GetFieldInstruction.class, this::generateGetFieldInstruction);
        generators.put(CallInstruction.class, this::generateCallInstruction);
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

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class public ").append(className).append(NL);

        // superclass
        String superClass = (classUnit.getSuperClass() == null || Objects.equals(classUnit.getSuperClass(), "Object")) ? "java/lang/Object" : classUnit.getSuperClass();
        code.append(".super ").append(superClass).append(NL);

        // fields
        classUnit.getFields().forEach(field -> {
            code.append(".field public ");
            code.append(field.getFieldName()).append(" ").append(getJasminType(field.getFieldType())).append(NL);
        });

        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(superClass);

        code.append(defaultConstructor).append(NL);
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

        if(Objects.equals(methodName, "main"))
            code.append("[Ljava/lang/String;");

        else{
            // method params
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
        }

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        // generate code for loading what's on the right
        code.append(generators.apply(assign.getRhs()));

        // store value in the stack in destination
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

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        // get register
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

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB -> "isub";
            case DIV -> "idiv";
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

    private String generateCallInstruction(CallInstruction callInstruction) {
        var code = new StringBuilder();

        String invocationType = callInstruction.getInvocationType().toString();
        String[] operands = callInstruction.getOperands().toString().split(" ");

        String name = operands[1].split("\\.")[0];
        name = name.substring(0, 1).toUpperCase() + name.substring(1);

        if (Objects.equals(invocationType, "NEW"))
            code.append("new ").append(ollirResult.getOllirClass().getClassName()).append(NL).append("dup").append(NL);

        // invokeSpecial
        else if(Objects.equals(invocationType, "invokestatic")){

            String methodName = callInstruction.getMethodName().toString();
            int startIndex = methodName.indexOf('"') + 1;
            int endIndex = methodName.indexOf('"', startIndex);
            methodName = methodName.substring(startIndex, endIndex);
            String firstLetterLowercase = name.substring(0, 1).toLowerCase();
            name = firstLetterLowercase + name.substring(1);

            code.append(invocationType).append(" ").append(name).append("/").append(methodName).append("()V").append(NL);

        }
        else if(Objects.equals(invocationType, "invokevirtual")){

            String methodName = callInstruction.getMethodName().toString();
            int startIndex = methodName.indexOf('"') + 1;
            int endIndex = methodName.indexOf('"', startIndex);
            methodName = methodName.substring(startIndex, endIndex);
            String returnType=null;
            for(Method method :  ollirResult.getOllirClass().getMethods()){
                if(Objects.equals(method.getMethodName(), methodName)){
                    returnType = getJasminType(method.getReturnType());
                }
            }
            code.append(invocationType).append(" ").append(ollirResult.getOllirClass().getClassName()).append("/").append(methodName).append("()").append(returnType).append(NL);
        }
        else
            code.append(invocationType).append(" ").append(name).append("/<init>()V").append(NL);

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
}

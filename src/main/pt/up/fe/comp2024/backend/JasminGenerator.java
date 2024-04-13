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

        System.out.println(code);
        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        //var className = currentMethod.getOllirClass().getClassName();

        var className = ollirResult.getOllirClass().getClassName();
        //code.append(".class ").append(className).append(NL).append(NL);

        String superClassName = classUnit.getSuperClass() == null ? "java/lang/Object" : classUnit.getSuperClass();
        code.append(".class public ").append(className).append(NL);

        code.append(".super ").append(superClassName).append(NL);
        //class name
        (ollirResult.getOllirClass().getFields()).forEach(field -> {
            code.append(".field ");
            if (field.getFieldAccessModifier() == AccessModifier.PUBLIC) {
                code.append("public ");
            }
            code.append(field.getFieldName()).append(" ").append(getJasminType(field.getFieldType())).append(NL);
        });

        // generate a single constructor method
        var defaultConstructor = new StringBuilder();
        defaultConstructor.append("; Default constructor").append(NL);
        defaultConstructor.append(".method public <init>()V").append(NL);
        defaultConstructor.append("    aload_0").append(NL);

        if (classUnit.getSuperClass() != null) {
            // Call superclass constructor
            defaultConstructor.append("    invokespecial ").append(classUnit.getSuperClass()).append("/<init>()V").append(NL);
        } else {
            // Call java/lang/Object constructor
            defaultConstructor.append("    invokespecial java/lang/Object/<init>()V").append(NL);
        }

        defaultConstructor.append("    return").append(NL);
        defaultConstructor.append(".end method").append(NL);
        code.append(defaultConstructor);

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

        // TODO: Hardcoded param types and return type, needs to be expanded

        var returnType = method.getReturnType().toString();
        var params = new StringBuilder();

       for (Element param : method.getParams()) {
           //check param type from getJasminType()
           params.append(getJasminType(param.getType()));

            //params.append(param.getType().toString());
        }

        ElementType type = method.getReturnType().getTypeOfElement();
    /*
        if (type == ElementType.INT32 || type == ElementType.BOOLEAN) {
            code.append("i");
        }
        else {
            code.append("a");
        }
*/      if (methodName.equals("main")) {
            code.append(".method public static main([Ljava/lang/String;)V").append(NL);
        } else {
            code.append(".method ").append(modifier).append(methodName).append("(").append(params).append(")").append(getJasminType(method.getReturnType())).append(NL);
        }
        //code.append(".method ").append(modifier).append(methodName).append("(").append(params).append(")").append(getJasminType(method.getReturnType())).append(NL);
        //code.append("\n.method ").append(modifier).append(methodName).append("(I)I").append(NL);

        // Add limits
        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);

        for (var inst : method.getInstructions()) {
            var instCode = StringLines.getLines(generators.apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            code.append(instCode);
        }

        if (method.getInstructions().size() == 0) {
            code.append(TAB).append("return").append(NL);
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

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        var operand = (Operand) lhs;

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();

        // TODO: Hardcoded for int type, needs to be expanded
        code.append("istore_").append(reg).append(NL);

        return code.toString();
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
        return "iload_" + reg + NL;

        //TODO verify
    }

    private String getJasminType(Type type) {
        var code = new StringBuilder();

        ElementType elemType = type.getTypeOfElement();
        /*if (type.getTypeOfElement() == ElementType.ARRAYREF) {
            code.append("[");
            elemType = ((ArrayType) type).getArrayType();
        }
        */
        switch (elemType) {
            case INT32 -> code.append("I");
            case BOOLEAN -> code.append("Z");
            case VOID -> code.append("V");
            case STRING -> code.append("Ljava/lang/String;");
            case OBJECTREF -> {
                assert type instanceof ClassType;
                String className = ((ClassType) type).getName();
                code.append("L").append(className).append(";");
            }
            case ARRAYREF -> code.append("[").append(getJasminType(((ArrayType) type).getElementType()));
            default -> throw new NotImplementedException("Type" + elemType + "not implemented.");
        }
        return code.toString();
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
            case AND -> "iand";
            case OR -> "ior";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        code.append(op).append(NL);

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // TODO: Hardcoded to int return type, needs to be expanded

        if (returnInst.hasReturnValue()) {
            code.append(generators.apply(returnInst.getOperand()));
        }
        //getJasminType()
        if (returnInst.getOperand() != null) {
            ElementType type = returnInst.getOperand().getType().getTypeOfElement();
            if  (type == ElementType.INT32 || type == ElementType.BOOLEAN) {
                code.append("i");
            } else {
                code.append("a");
            }
        }
        //System.out.println("return");
        code.append("return").append(NL);

        return code.toString();
    }

}

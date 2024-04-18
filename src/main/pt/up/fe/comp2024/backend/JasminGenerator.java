package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.nio.file.attribute.AclEntry;
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
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(Field.class, this::generateField);
        generators.put(GetFieldInstruction.class, this::generateGet);
        generators.put(PutFieldInstruction.class, this::generatePut);
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


   private String transformType(String type) {
        return switch (type) {
            case "STRING" -> "Ljava/lang/String;";
            case "STRING[]" -> "[Ljava/lang/String;";
            case "INT32" -> "I";
            case "INT32[]" -> "[I";
            case "BOOLEAN" -> "Z";
            case "DOUBLE" -> "D";
            case "LONG" -> "L";
            case "FLOAT" -> "F";
            case "VOID" -> "V";
            default -> "A";
        };
   }


    private String generateField(Field field) {
        var code = new StringBuilder();

        var modifier = field.getFieldAccessModifier() != AccessModifier.DEFAULT ?
                field.getFieldAccessModifier().name().toLowerCase() + " " :
                "";

        var fieldName = field.getFieldName();

        code.append(".field ").append(modifier);
        if (field.isStaticField()) code.append("static ");
        if (field.isStaticField()) code.append("final ");
        code.append(fieldName).append(" ");

        code.append(transformType(field.getFieldType().toString())).append(NL);
        return code.toString();
    }


    private String generateGet(GetFieldInstruction getFieldInstruction) {
        var code = new StringBuilder();

        var name = getFieldInstruction.getField().getName();
        var type = transformType(getFieldInstruction.getFieldType().toString());

        code.append("aload_0 ").append(NL);
        code.append("getfield ").append(ollirResult.getOllirClass().getClassName()).append("/").append(name).append(" ").append(type).append(NL);
        return code.toString();
    }

    private String generatePut (PutFieldInstruction putFieldInstruction) {
        var code = new StringBuilder();

        var name = putFieldInstruction.getField().getName();
        var type = transformType(putFieldInstruction.getValue().getType().toString());

        //append i_load or a_load
        code.append("aload_0 ").append(NL);
        code.append(generators.apply(putFieldInstruction.getValue()));
        code.append("putfield ").append(ollirResult.getOllirClass().getClassName()).append("/").append(name).append(" ").append(type).append(NL);
        return code.toString();
    }


    private String generateCall(CallInstruction call) {
            StringBuilder code = new StringBuilder();

            String type = call.getInvocationType().toString();
            String operands = call.getOperands().toString().split(" ")[1].split("\\.")[0];
            String name = Character.toUpperCase(operands.charAt(0)) + operands.substring(1);

            if (type.equals("NEW")) {
                code.append("new ").append(ollirResult.getOllirClass().getClassName()).append(NL).append("dup").append(NL);
            } else {
                code.append(type).append(" ").append(name).append("/<init>()V").append(NL);
            }

            return code.toString();

    }


    private String dealWithInvokes(CallInstruction callInstruction) {
        var code = new StringBuilder();

        switch(callInstruction.getInvocationType()) {
            case invokevirtual -> code.append("invokevirtual ");
            case invokeinterface -> code.append("invokeinterface ");
            case invokespecial -> code.append("invokespecial ");
            case invokestatic -> code.append("invokestatic ");
            case NEW -> code.append("new ");
            case arraylength -> code.append("arraylength ");
            case ldc -> code.append("ldc ");
        }
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

        if (operand.getType().getTypeOfElement() == ElementType.INT32 || operand.getType().getTypeOfElement() == ElementType.BOOLEAN) {
            code.append("istore_").append(reg).append(NL);
        } else {
            code.append("astore_").append(reg).append(NL);
        }

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

        switch (operand.getType().getTypeOfElement()) {
            case THIS -> {
                return "aload_0" + NL;
            }
            case INT32, BOOLEAN -> {
                return "iload_" + reg + NL;
            }
            case STRING, OBJECTREF, ARRAYREF -> {
                return "aload_" + reg + NL;
            }
            default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
        }
    }

    private String getJasminType(Type type) {
        var code = new StringBuilder();

        ElementType elemType = type.getTypeOfElement();

        switch (elemType) {
            case INT32 -> code.append("I");
            case BOOLEAN -> code.append("Z");
            case VOID -> code.append("V");
            case STRING -> code.append("Ljava/lang/String;");
            case OBJECTREF -> {
                assert type instanceof ClassType;
                String className = ((ClassType) type).getName();
                //antes tinha code.append("L").append(className).append(";");
                code.append("L").append(";");
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

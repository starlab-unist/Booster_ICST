package RegressionOracles;

import Generater.MUTMutation.ASTParser;
import Generater.MUTMutation.TestCaseGenerator;
import org.junit.Test;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import utils.Config;

import java.util.*;

public class Analyzer {
    /**
     * collects local variables that are NOT primitive and the type is CUT
     * (Config.FULL_CLASS_NAME)
     *
     * @param testMethod
     * @return
     */
    List<CtLocalVariable> analyze(CtMethod testMethod) {
        List<CtLocalVariable> result = new ArrayList<>();
        // List<CtLocalVariable> vars = testMethod.getElements(new
        // TypeFilter<>(CtLocalVariable.class));
        // for (CtLocalVariable var : vars) {
        // if (!var.getType().isPrimitive() &&
        // var.getType().getQualifiedName().startsWith(Config.FULL_CLASS_NAME.replaceAll("_ESTest","")))
        // {
        // result.add(var);
        // }
        // }
        List<CtStatement> stmts = testMethod.getBody().getStatements();
        for (CtStatement stmt : stmts) {
            if (isLastStmt(stmt, stmts)) { // check if stmt is MUT
                if (ASTParser.isInvocation(stmt)) {
                    if (ASTParser.isLocalVariable(stmt)) {
                        List<CtLocalVariable> localVars = stmt.getElements(new TypeFilter<>(CtLocalVariable.class));
                        CtLocalVariable localVar = localVars.get(0); // we have only one element in localVars because we
                                                                     // collected from a single statement
                        if (!localVar.getType().isPrimitive()) {
                            result.add(localVar);
                            break;
                        }
                    } else {
                        List<CtInvocation> invokes = stmt.getElements(new TypeFilter<>(CtInvocation.class));
                        CtInvocation invoke = invokes.get(0);
                        if (!invoke.getExecutable().isStatic()) {
                            CtVariableReadImpl target = (CtVariableReadImpl) invoke.getTarget();
                            CtLocalVariable localVar = (CtLocalVariable) target.getVariable().getDeclaration();
                            if (localVar != null) {
                                if (!localVar.getType().isPrimitive()) {
                                    result.add(localVar);
                                    break;
                                }
                            }
                        } else { // MUT is static!
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    public Map<CtMethod, List<CtLocalVariable>> analyze(CtType<?> ctClass, boolean forPrimitive) {
        Map<CtMethod, List<CtLocalVariable>> result = new HashMap<>();
        CtTypeReference<Test> reference = ctClass.getFactory().Type().createReference(Test.class);
        Set<CtMethod<?>> methods = ctClass.getMethods();
        for (CtMethod ctMethod : methods) {
            for (CtAnnotation a : ctMethod.getAnnotations()) {
                if (a.getAnnotationType().getSimpleName().equals(reference.getSimpleName())) {
                    if (forPrimitive) {
                        result.put(ctMethod, analyzePrimitive(ctMethod));
                    } else {
                        result.put(ctMethod, analyze(ctMethod));
                    }
                    break;
                }
            }
        }
        return result;
    }

    /**
     * collects local variable that assigns the return value of MUT and is primitive
     *
     * @param testMethod
     * @return
     */
    List<CtLocalVariable> analyzePrimitive(CtMethod testMethod) {
        List<CtLocalVariable> result = new ArrayList<>();
        List<CtStatement> stmts = testMethod.getBody().getStatements();
        for (CtStatement stmt : stmts) {
            if (isLastStmt(stmt, stmts)) { // check if stmt is MUT
                if (ASTParser.isInvocation(stmt) && ASTParser.isLocalVariable(stmt)) {
                    List<CtLocalVariable> localVars = stmt.getElements(new TypeFilter<>(CtLocalVariable.class));
                    CtLocalVariable localVar = localVars.get(0); // we have only one element in localVars because we
                                                                 // collected from a single statement
                    if (localVar.getType().isPrimitive()) {
                        result.add(localVar);
                        break;
                    }
                }
            }
        }
        return result;
    }

    private boolean isLastStmt(CtStatement stmt, List<CtStatement> stmts) {
        if (stmts == null)
            return false;
        if (stmts.size() == 0)
            return false;
        int index = stmts.size() - 1;
        CtStatement lastStmt = stmts.get(index).clone();
        while (index > 0 &&
                (TestCaseGenerator.getMUT(lastStmt) == null
                        || lastStmt.toString().contains("RegressionOracles.RegressionUtil.Logger.observe("))) {
            index--;
            lastStmt = stmts.get(index).clone();
        }
        return lastStmt.toString().equals(stmt.clone().toString());
    }

    private boolean isNotCUTInvocation(CtStatement stmt) {
        if (!ASTParser.isInvocation(stmt) && !ASTParser.isConstructor(stmt)) {
            // System.out.println("Neither of invok or const: " + stmt);
            return true;
        }
        if (ASTParser.isInvocation(stmt)) {
            List<CtInvocationImpl> invocations = stmt.getElements(new TypeFilter<>(CtInvocationImpl.class));
            CtInvocationImpl invocation = invocations.get(0); // we have only one element because we collected from a
                                                              // single statement
            // System.out.println(invocation.getTarget().getType().getQualifiedName().startsWith(Config.FULL_CLASS_NAME)
            // + " invok sig for " + invocation.getExecutable().getSimpleName() + " : " +
            // stmt);
            if (invocation.getExecutable().isStatic() && invocation.getExecutable().getActualMethod() != null) {
                if (invocation.getExecutable().getActualMethod().getDeclaringClass().getName()
                        .startsWith(Config.FULL_CLASS_NAME))
                    return false;
            } else {
                if (invocation.getTarget().getType().getQualifiedName().startsWith(Config.FULL_CLASS_NAME))
                    return false;
            }
        }

        if (ASTParser.isConstructor(stmt)) {
            List<CtConstructorCallImpl> constructorCalls = stmt
                    .getElements(new TypeFilter<>(CtConstructorCallImpl.class));
            CtConstructorCallImpl constructorCall = constructorCalls.get(0); // we have only one element because we
                                                                             // collected from a single statement
            // System.out.println(constructorCall.getExecutable().getDeclaringType().getQualifiedName().startsWith(Config.FULL_CLASS_NAME)
            // + " constructor sig for " + constructorCall.getExecutable().getSimpleName() +
            // " : " + stmt);
            if (constructorCall.getExecutable().getDeclaringType().getQualifiedName()
                    .startsWith(Config.FULL_CLASS_NAME))
                return false;
        }
        return true;
    }
}

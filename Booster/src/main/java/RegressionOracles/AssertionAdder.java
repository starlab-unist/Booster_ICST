package RegressionOracles;

import RegressionOracles.RegressionUtil.Assertion;
import RegressionOracles.RegressionUtil.Logger;
import RegressionOracles.RegressionUtil.Util;
import org.junit.Assert;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import utils.Config;

import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.List;
import java.util.Map;

public class AssertionAdder {
    private Factory factory;

    public AssertionAdder(Factory factory) {
        this.factory = factory;
    }

    public CtMethod addAssertion(CtMethod testMethod, Map<CtMethod, List<CtLocalVariable>> localVariablesPerTestMethod, Map<CtMethod, List<CtLocalVariable>> localVariablesPrimitive) {
        final CtClass testClass = testMethod.getParent(CtClass.class);
        testClass.removeMethod(testMethod);
        final CtMethod<?> clone = testMethod.clone();
        if (localVariablesPerTestMethod.containsKey(testMethod)) {
            List<CtLocalVariable> localVarsOfMethod = localVariablesPerTestMethod.get(testMethod);
            addAssertionGetter(clone.getSimpleName(), clone, localVarsOfMethod);
        }
        if (localVariablesPrimitive.containsKey(testMethod)) {
            List<CtLocalVariable> localVarsOfPrimitive = localVariablesPrimitive.get(testMethod);
            addAssertionPrimitive(clone.getSimpleName(), clone, localVarsOfPrimitive);
        }
        testClass.addMethod(clone);
//        System.out.println(clone);
        return clone;
    }

    private void addAssertionPrimitive(String testName, CtMethod<?> testMethod, List<CtLocalVariable> ctLocalVariables) {
        ctLocalVariables.forEach(ctLocalVariable -> this.addAssertionPrimitive(testName, testMethod, ctLocalVariable));
    }

    void addAssertionPrimitive(String testName, CtMethod testMethod, CtLocalVariable localVariable) {
//        if (localVariable.getSimpleName().toLowerCase().contains("time") ||
//                localVariable.getSimpleName().toLowerCase().contains("date") ||
//                localVariable.getAssignment().clone().toString().toLowerCase().contains("time") ||
//                localVariable.getAssignment().clone().toString().toLowerCase().contains("date")) //time causes flaky tests in regression mode
//            return;
        CtExpression assigned = Util.assignment(factory, localVariable);
        CtTypeReference assignedType = assigned.getType();
        String key = Util.getKey(localVariable);
        Map<String, List<Assertion>> observationMap = Logger.observations;
        if (observationMap.containsKey(testName)) {
            List<Assertion> observers = observationMap.get(testName);
            for (Assertion observer : observers) {
                if (key.equals(observer.getKey())) {
                    CtInvocation assignmentToAssert = null;
                    if (assignedType.getSimpleName().equals("double")) {
                        assignmentToAssert = createAssert("assertEquals",
                                factory.createCodeSnippetExpression(observer.getGetters().toString()), //expected
                                assigned, //actual
                                factory.createCodeSnippetExpression("0.01")); //delta
                    } else if (assignedType.getSimpleName().equals("float")) {
                        assignmentToAssert = createAssert("assertEquals",
                                factory.createCodeSnippetExpression(observer.getGetters().toString()), //expected
                                assigned, //actual
                                factory.createCodeSnippetExpression("0.01F")); //delta
                    } else if (assignedType.getSimpleName().equals("long")) {
                        String expected = observer.getGetters().toString();
                        if (!expected.endsWith("L"))
                            expected = expected + "L";
                        assignmentToAssert = createAssert("assertEquals",
                                factory.createCodeSnippetExpression(expected), //expected
                                assigned); //actual
                    } else {
                        CtExpression expected = null;
                        if (!observer.getGetters().toString().startsWith(Config.STRING_IDENTIFIER)) {
                            expected = factory.createCodeSnippetExpression(observer.getGetters().toString());
                        } else {
                            if (isNumeric(observer.getGetters().toString()) && observer.getGetters().toString().length() < 1000)
                                expected = factory.createLiteral(observer.getGetters().toString().replace(Config.STRING_IDENTIFIER, ""));
                        }
                        if (expected != null) {
                            assignmentToAssert = createAssert("assertEquals",
                                    expected, //expected
                                    assigned); //actual
                        }
                    }
                    testMethod.getBody().insertEnd(assignmentToAssert);
                }
            }
        }
    }

    private void addAssertionGetter(String testName, CtMethod<?> testMethod, List<CtLocalVariable> ctLocalVariables) {
        ctLocalVariables.forEach(ctLocalVariable -> this.addAssertionGetter(testName, testMethod, ctLocalVariable));
    }

    void addAssertionGetter(String testName, CtMethod testMethod, CtLocalVariable localVariable) {
//        if (localVariable.getSimpleName().toLowerCase().contains("time") || //time causes flaky tests in regression mode
//                localVariable.getSimpleName().toLowerCase().contains("date") ||
//                localVariable.getAssignment().clone().toString().toLowerCase().contains("time") ||
//                localVariable.getAssignment().clone().toString().toLowerCase().contains("date")) //time causes flaky tests in regression mode
//            return;
        Map<String, List<Assertion>> tmpobservationMap = Logger.observations;
        if(tmpobservationMap.containsKey(testName)){
            List<Assertion> tmpobservers = tmpobservationMap.get(testName);
            if(tmpobservers.size()==1 && tmpobservers.get(0).getKey().equals(localVariable.getSimpleName())){
//                String tmpkey = tmpobservers.get(0).getKey();
//                String variableName = localVariable.getSimpleName();
//                System.out.println("Key: "+tmpkey+" Var: "+variableName);

//                System.out.println("Key:"+tmpkey);
                CtVariableAccess tmpvariableaccess = factory.Code().createVariableRead(localVariable.getReference(),false);
                CtInvocation tmpinvocationToAssert = null;
                tmpinvocationToAssert = createAssert("assertNull",
                        factory.createCodeSnippetExpression("null"), //expected
                        tmpvariableaccess);
                testMethod.getBody().insertEnd(tmpinvocationToAssert);
            }
        }

        List<CtMethod> getters = Util.getGetters(localVariable);
        getters.forEach(getter -> {
            String key = Util.getKey(getter, localVariable);
            CtInvocation invocationToGetter =
                    Util.invok(getter, localVariable);
            CtTypeReference assignedType = invocationToGetter.getType();
            Map<String, List<Assertion>> observationMap = Logger.observations;
            if (observationMap.containsKey(testName)) {
                List<Assertion> observers = observationMap.get(testName);
                for (Assertion observer : observers) {
                    if (key.equals(observer.getKey())) {
                        CtInvocation invocationToAssert = null;
                        if (assignedType.getSimpleName().equals("double")) {
                            invocationToAssert = createAssert("assertEquals",
                                    factory.createCodeSnippetExpression(observer.getGetters().toString()), //expected
                                    invocationToGetter, //actual
                                    factory.createCodeSnippetExpression("0.01")); //delta
                        } else if (assignedType.getSimpleName().equals("float")) {
                            invocationToAssert = createAssert("assertEquals",
                                    factory.createCodeSnippetExpression(observer.getGetters().toString()), //expected
                                    invocationToGetter, //actual
                                    factory.createCodeSnippetExpression("0.01F")); //delta
                        } else if (assignedType.getSimpleName().equals("long")) {
                            String expected = observer.getGetters().toString();
                            if (!expected.endsWith("L"))
                                expected = expected + "L";
                            invocationToAssert = createAssert("assertEquals",
                                    factory.createCodeSnippetExpression(expected), //expected
                                    invocationToGetter); //actual
                        } else {
                            CtExpression expected = null;
                            if (!observer.getGetters().toString().startsWith(Config.STRING_IDENTIFIER)) {
                                expected = factory.createCodeSnippetExpression(observer.getGetters().toString());
                            } else {
                                if (invocationToGetter.getExecutable().getSimpleName().equals("toString"))
                                    if (observer.getGetters().toString().length() < 1000)
                                        expected = factory.createLiteral(observer.getGetters().toString().replace(Config.STRING_IDENTIFIER, ""));
                            }
                            if (expected != null) {
                                invocationToAssert = createAssert("assertEquals",
                                        expected, //expected
                                        invocationToGetter); //actual
                            }
                        }
                        testMethod.getBody().insertEnd(invocationToAssert);
                    }
                }
            }
        });
    }

    public static CtInvocation createAssert(String name, CtExpression... parameters) {
        final Factory factory = parameters[0].getFactory();
        CtTypeAccess accessToAssert =
                factory.createTypeAccess(factory.createCtTypeReference(Assert.class));
        CtExecutableReference assertEquals = factory.Type().get(Assert.class)
                .getMethodsByName(name).get(0).getReference();
        if (parameters.length == 3) //expected, actual, and delta
            return factory.createInvocation(
                    accessToAssert,
                    assertEquals,
                    parameters[0],
                    parameters[1],
                    parameters[2]);
        else
            return factory.createInvocation(
                    accessToAssert,
                    assertEquals,
                    parameters[0],
                    parameters[1]);
    }

    public static boolean isNumeric(String str) {
        NumberFormat formatter = NumberFormat.getInstance();
        ParsePosition pos = new ParsePosition(0);
        formatter.parse(str, pos);
        return str.length() == pos.getIndex();
    }
}

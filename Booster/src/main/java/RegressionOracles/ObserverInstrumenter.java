package RegressionOracles;

import RegressionOracles.RegressionUtil.Logger;
import RegressionOracles.RegressionUtil.Util;
import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import utils.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by Mijung
 */
public class ObserverInstrumenter {

    private Factory factory;

    public ObserverInstrumenter(Factory factory) {
        this.factory = factory;
    }


    public Pair<CtClass,List<String>> instrumentObserver(CtMethod testMethod, Map<CtMethod, List<CtLocalVariable>> localVariablesPerTestMethod, Map<CtMethod, List<CtLocalVariable>> localVariablesPrimitive) {
        final CtClass testClass = testMethod.getParent(CtClass.class);
        testClass.removeMethod(testMethod);
        final CtMethod<?> clone = testMethod.clone();

        List<String> instrumentedStatements = new ArrayList<>();
        if (localVariablesPerTestMethod.containsKey(testMethod)) {
            List<CtLocalVariable> localVarsOfMethod = localVariablesPerTestMethod.get(testMethod);
            instrumentedStatements.addAll(instrument(testClass.getSimpleName(), clone, localVarsOfMethod));
//            for(String instrumentedStatement:instrumentedStatements){
//                System.out.println(instrumentedStatement);
//            }
        }
        if (localVariablesPrimitive.containsKey(testMethod)) {
            List<CtLocalVariable> localVarsOfPrimitive = localVariablesPrimitive.get(testMethod);
            instrumentedStatements.addAll(instrumentPrimitive(testClass.getSimpleName(), clone, localVarsOfPrimitive));
//            for(String instrumentedStatement:instrumentedStatements){
//                System.out.println(instrumentedStatement);
//            }
        }
        testClass.addMethod(clone);
        return new Pair<CtClass,List<String>>(testClass,instrumentedStatements);
    }

    public CtClass instrumentObserver(CtMethod<?> testMethod, List<CtLocalVariable> localVariables) {
        final CtClass testClass = testMethod.getParent(CtClass.class);
        testClass.removeMethod(testMethod);
        final CtMethod<?> clone = testMethod.clone();
        instrument(testClass.getSimpleName(), clone, localVariables);
        testClass.addMethod(clone);
//        System.out.println(clone);
        return testClass;
    }


    public List<String> instrumentPrimitive(String testName, CtMethod<?> testMethod, List<CtLocalVariable> ctLocalVariables) {
        List<String> instrumentedStatements = new ArrayList<>();
        for(CtLocalVariable ctLocalVariable:ctLocalVariables) {
            CtExpression assigned = Util.assignment(factory, ctLocalVariable);
            CtInvocation invocationToObserve = createObservePrimitive(testName, Util.getKey(ctLocalVariable), assigned);
            testMethod.getBody().insertEnd(invocationToObserve);
            instrumentedStatements.add(invocationToObserve.toString()+";");
        }
        return instrumentedStatements;
    }

    void instrumentPrimitive(String testName, CtMethod testMethod, CtLocalVariable localVariable) {
        CtExpression assigned = Util.assignment(factory, localVariable);
        CtInvocation invocationToObserve = createObservePrimitive(testName, Util.getKey(localVariable), assigned);
        testMethod.getBody().insertEnd(invocationToObserve);
    }

    CtInvocation createObservePrimitive(String testName, String assignmentVarName, CtExpression assigned) {
        CtTypeAccess accessToLogger =
                factory.createTypeAccess(factory.createCtTypeReference(Logger.class));
        CtExecutableReference refObserve = factory.Type().get(Logger.class)
                .getMethodsByName("observe").get(0).getReference();
        return factory.createInvocation(
                accessToLogger,
                refObserve,
                factory.createLiteral(testName),
                factory.createLiteral(assignmentVarName),
                assigned
        );
    }

    public List<String> instrument(String testName, CtMethod<?> testMethod, List<CtLocalVariable> ctLocalVariables) {
        for(CtLocalVariable ctLocalVariable: ctLocalVariables){
            List<CtMethod> getters = Util.getGetters(ctLocalVariable);
            if(getters.size()>0){
                Factory factory = new Launcher().getFactory();
                CtIf ifStatement = factory.Core().createIf();
                CtExpression<Boolean> ifObjetNullStatement = factory.Code().createCodeSnippetExpression(ctLocalVariable.getSimpleName() + "!=null");
                ifStatement.setCondition(ifObjetNullStatement);
                CtBlock thenBlock = factory.Core().createBlock();

                for(CtMethod getter:getters){
                    CtInvocation invocationToGetter = Util.invok(getter, ctLocalVariable);
                    CtInvocation invocationToObserve = createObserve(testName, Util.getKey(getter, ctLocalVariable), invocationToGetter);
                    thenBlock.addStatement(invocationToObserve.clone());
                }

                CtBlock elseBlock = factory.Core().createBlock();
                CtExpression assigned = Util.assignment(factory, ctLocalVariable);
                CtInvocation invocationToObserve = createObservePrimitive(testName, Util.getKey(ctLocalVariable), assigned);
                elseBlock.addStatement(invocationToObserve.clone());

                ifStatement.setThenStatement(thenBlock);
                ifStatement.setElseStatement(elseBlock);
                testMethod.getBody().insertEnd(ifStatement);
                return new ArrayList<>(Arrays.asList(ifStatement.toString().split("\n")));
            }
        }
        return new ArrayList<>();
    }

    void instrument(String testName, CtMethod testMethod, CtLocalVariable localVariable) {
        List<CtMethod> getters = Util.getGetters(localVariable);
        getters.forEach(getter -> {
            CtInvocation invocationToGetter =
                    Util.invok(getter, localVariable);
            CtInvocation invocationToObserve =
                    createObserve(testName, Util.getKey(getter, localVariable), invocationToGetter);
            testMethod.getBody().insertEnd(invocationToObserve);
        });
    }

    CtInvocation createObserve(String testName, String getterKey, CtInvocation invocationToGetter) {
        CtTypeAccess accessToLogger =
                factory.createTypeAccess(factory.createCtTypeReference(Logger.class));
        CtExecutableReference refObserve = factory.Type().get(Logger.class)
                .getMethodsByName("observe").get(0).getReference();
        return factory.createInvocation(
                accessToLogger,
                refObserve,
                factory.createLiteral(testName),
                factory.createLiteral(getterKey),
                invocationToGetter
        );
    }
}

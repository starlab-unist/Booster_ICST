package Generater.MUTMutation;

import RegressionOracles.Analyzer;
import RegressionOracles.ObserverInstrumenter;
import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;
import utils.Config;
import utils.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.annotation.Annotation;
import java.util.*;

public class TestCaseGenerator {
    private InputCombinations inputCombinations;
    private String testClassName;
    private String packageAndImport;
    private List<CtClass<Object>> generatedTests;
    private List<CtClass<Object>> observerAddedTests = new ArrayList<>(); // only for regression mode
    private final Analyzer analyzer;
    private final ObserverInstrumenter collector;
    private Map<String, Integer> mutCountMap;
    private Set<CtMethod> testCases;
    private Map<Integer, Map<Integer, Set<Integer>>> picked_map = new HashMap<>();
    private Map<Integer, Integer> upperBounds = new HashMap<>();
    private Map<Integer, Set<List<Integer>>> results = new HashMap<>();
    private Launcher launcher;

    /**
     * init test case with input combinations
     */
    public TestCaseGenerator(String testFile, long time_budget, long startTime) {

        testClassName = extractFileName(testFile, ".java");
        // System.out.println(testClassName);

        inputCombinations = new InputCombinations();

        initInputCombinations(testFile,time_budget,startTime);

        getImportAndPackage(testFile);
        generatedTests = new ArrayList<>();
        mutCountMap = new HashMap<>();
        launcher = new Launcher();
        analyzer = new Analyzer();
        collector = new ObserverInstrumenter(launcher.getFactory());

    }

    public static CtAbstractInvocation getMUT(CtStatement lastStmt) {
        CtAbstractInvocation invoke = null;
        if (lastStmt instanceof CtInvocationImpl) {
            invoke = (CtInvocationImpl) lastStmt;
        } else if (lastStmt.getElements(new TypeFilter<>(CtInvocationImpl.class)).size() == 1) {
            invoke = lastStmt.getElements(new TypeFilter<>(CtInvocationImpl.class)).get(0);
        } else if (lastStmt instanceof CtConstructorCallImpl) {
            invoke = (CtConstructorCallImpl) lastStmt;
        } else if (lastStmt.getElements(new TypeFilter<>(CtConstructorCallImpl.class)).size() == 1) {
            invoke = lastStmt.getElements(new TypeFilter<>(CtConstructorCallImpl.class)).get(0);
        } else {
            return null;
        }
        return invoke;
    }

    private static CtStatement getLastMUTStatement(CtMethod testcase) {
        List<CtStatement> stmts = testcase.getBody().getStatements();
        return stmts.get(stmts.size() - 1);
    }

    public void initMap(int size) {
        for (int i = 0; i < size; i++) {
            picked_map.put(i, new HashMap<>());
            results.put(i, new HashSet<>());
        }
    }

    public Launcher getLauncher() {
        return launcher;
    }

    public List<CtClass<Object>> getObserverAddedTests() {
        return observerAddedTests;
    }

    public String getPackageAndImport() {
        return packageAndImport;
    }

    public String getTestClassName() {
        return testClassName;
    }

    public List<CtClass<Object>> getGeneratedTests() {
        return generatedTests;
    }

    public Set<CtMethod> getTestCases() {
        return testCases;
    }

    public Set<MUTInput> getMutInputs() {
        return inputCombinations.getMuts();
    }

    /**
     * get the import and package and set it as packageAndImport
     *
     * @param testFile
     */
    private void getImportAndPackage(String testFile) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(testFile));
            String line = null;
            String lines = "";
            while ((line = bufferedReader.readLine()) != null)
                if (line.trim().startsWith("package ") || line.trim().startsWith("import "))
                    lines += line + "\n";
            packageAndImport = lines;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String extractFileName(String path, String ext) {
        String separator;
        if (System.getProperty("os.name").contains("Window")) {
            separator = "\\\\";
        } else
            separator = File.separator;
        String[] elements = path.split(separator);
        String lastEle = elements[elements.length - 1];
        return lastEle.substring(0, lastEle.length() - ext.length());
    }

    public Pair<CtClass, String> generateTest(MUTInput mutInput, int index) {
        LinkedHashMap<Integer, List<Input>> inputPools = mutInput.getInputs();
        // Set<List<Integer>> result = results.get(index);
        Map<Integer, Set<Integer>> picked = picked_map.get(index);
        List<Integer> combi = new ArrayList<>();

        // int totalCombiNum = 1;
        // for (List<Input> inputpool:inputPools.values()){
        // totalCombiNum *= inputpool.size();
        // }
        //
        // if(result.size()==totalCombiNum){
        // return null;
        // }

        for (Integer pos : inputPools.keySet()) {
            List<Input> inputs = inputPools.get(pos);
            if (inputs.size() == 0) { // TODO: this is just a workaround. The generated test won't compile
                combi.add(pos, -1);
                continue;
            }
            if (!picked.containsKey(pos))
                picked.put(pos, new HashSet<>());
            Random r = new Random();
            int i = r.nextInt((inputs.size()));
            // checking input uniqueness
            while (picked.get(pos).contains(i) && picked.get(pos).size() < inputs.size()) {
                i = r.nextInt((inputs.size()));
                // System.out.println(i + "\t" + inputs.size() + "@" + pos);
            }
            picked.get(pos).add(i);
            combi.add(pos, i);
        }
        // checking combination uniqueness
        // if (result.contains(combi)) {
        // return null;
        // }
        // result.add(combi);

        List<CtExpression> types = new LinkedList<>();
        List<Input> insertStmt = new ArrayList<>();
        /**
         * add mutate types
         */
        CtAbstractInvocation invoke = null;
        CtAbstractInvocation mutInvo = mutInput.getMUTInvocation();
        if (mutInvo instanceof CtInvocationImpl) {
            invoke = (CtInvocationImpl) mutInvo.clone();
        } else if (mutInvo instanceof CtConstructorCallImpl) {
            invoke = (CtConstructorCallImpl) mutInvo.clone();
        }
        Input receiverObj = null;
        for (int i = 0; i < combi.size(); i++) {
            Input par;
            if (combi.get(i).equals(-1)) {
                par = new Input(false);
            } else {
                par = inputPools.get(i).get(combi.get(i));
            }
            if (i == 0 && !par.isMUTStatic() && par.isVar()) { // it is not static invocation
                receiverObj = par;
            }
            if (par.isVar()) {
                insertStmt.add(par);
                /**
                 * Change var name of param arguments accordingly
                 */
                if (i > 0) {
                    if (invoke.getArguments().get(i - 1) instanceof CtVariableReadImpl) {
                        if (par.getVarName() instanceof CtVariableReadImpl) // in case variable reference is typecasted
                                                                            // as a paramter such as
                                                                            // numericEntityUnescaper0_10.translate(((java.lang.CharSequence)
                                                                            // (charBuffer0_3)));
                            ((CtVariableReadImpl) invoke.getArguments().get(i - 1))
                                    .setVariable(((CtVariableReadImpl) par.getVarName()).getVariable());
                        else {
                            if (par.getVarName() instanceof CtFieldReadImpl)
                                return null;
                            if (isVarTypeCased(par.getVarName()))
                                return null;
                            ((CtVariableReadImpl) invoke.getArguments().get(i - 1))
                                    .setVariable((CtVariableReference) par.getVarName());
                        }
                    }
                }
            }
            try {
                types.add((CtExpression) par.getVarName());
            } catch (Exception e) {
                CtExpression variableAccess = par.getVarName().getFactory().createVariableRead();
                try {
                    ((CtVariableRead) variableAccess).setVariable((CtVariableReference) par.getVarName());
                } catch (Exception gg) {
                    gg.printStackTrace();
                }
                types.add(variableAccess);
            }
        }
        /*
         * Change var name of receiver object accordingly
         */
        if (receiverObj != null) {
            CtElement var = receiverObj.getVarName();
            if (isVarTypeCased(var))
                return null;
            try {
                ((CtVariableReadImpl) ((CtInvocationImpl) invoke).getTarget()).setVariable((CtVariableReference) var);
            } catch (ClassCastException e) {
                e.printStackTrace();
                System.out.println(invoke.toString());
                System.out.println(((CtInvocationImpl) invoke).getTarget().toString());
            }
        }
        /*
         * add into mutate testcases
         */
        RawTestCase testCase = new RawTestCase(insertStmt, types, invoke);
        List<CtStatement> stmts = processMutate2(testCase);
        if (stmts == null) {
            System.out.println("Result is Null");
            return null;
        }
        CtAbstractInvocation lastStmt = getMUT(stmts);
        String mutName = lastStmt.getExecutable().getSimpleName();
        if (mutName.contains("<init>")) { // <> symbols cannot be used as class name
            mutName = "init";
        }
        if (!mutCountMap.containsKey(mutName)) {
            mutCountMap.put(mutName, 0);
        }
        mutCountMap.put(mutName, mutCountMap.get(mutName) + 1);
        String testNameId = mutName + "_" + mutCountMap.get(mutName);
        List<CtLocalVariable> variableDefinitions = new ArrayList<>();
        for (CtStatement statement : stmts) {
            variableDefinitions.addAll(statement.getElements(new TypeFilter<>(CtLocalVariable.class)));
        }
        Set<String> localVarNames = new HashSet<>();
        /*
         * check duplicate definition
         */
        boolean hasDuplicateDefinition = false;
        for (CtLocalVariable localVariable : variableDefinitions) {
            if (!localVarNames.contains(localVariable.getSimpleName())) {
                localVarNames.add(localVariable.getSimpleName());
            } else {
                hasDuplicateDefinition = true;
                break;
            }
        }
        if (hasDuplicateDefinition) {
            return null;
        }

        if (Config.REGRESSION_MODE) {
            Pair<CtClass, List<String>> mutatedTestAndStringPair = generateMethodsWithoutCodeSnippet(stmts,
                    testClassName + "_M_" + testNameId);
            Pair<CtClass, List<String>> observerAddedTestAndStringPair = addRegressionOracleToTest(
                    mutatedTestAndStringPair.getKey());
            String clazzStr = mutatedTestAndStringPair.getValue().get(0);
            String methodStr = mutatedTestAndStringPair.getValue().get(1);

            for (String oracle : observerAddedTestAndStringPair.getValue()) {
                if (!oracle.contains("RegressionOracles.RegressionUtil.Logger.observe")) {
                    oracle = oracle.trim() + " // if statement for MUT null check";
                    methodStr = methodStr.substring(0, methodStr.lastIndexOf("}"));
                    methodStr = methodStr + "\t" + oracle + "\n}";
                } else {
                    methodStr = methodStr.substring(0, methodStr.lastIndexOf("}"));
                    methodStr = methodStr + "\t\t" + oracle.trim() + "\n}";
                }
            }
            methodStr = "\t" + methodStr.replaceAll("\n", "\n\t");
            String finalStr = clazzStr.substring(0, clazzStr.lastIndexOf("}")) + "\n" + methodStr + "\n}";

            // generatedTests.add(mutatedTestAndStringPair.getKey());
            return new Pair<CtClass, String>(observerAddedTestAndStringPair.getKey(), finalStr);
        } else {
            CtClass<Object> newTestCase = generateMethods(stmts, testClassName + "_M_" + testNameId);
            // generatedTests.add(newTestCase);
            return new Pair<CtClass, String>(newTestCase, newTestCase.toString());
        }

    }

    public List<CtStatement> processMutToOracle(List<CtStatement> stmts, CtTypeReference returnType) {
        CtStatement lastStmt = stmts.get(stmts.size() - 1);
        String fixedLastStmt = returnType.getQualifiedName() + " returnValue = " + lastStmt.toString();
        Factory facotry = new Launcher().getFactory();
        CtStatement returnMut = facotry.Core().createCodeSnippetStatement();
        ((CtCodeSnippetStatement) returnMut).setValue(fixedLastStmt);
        stmts.set(stmts.size() - 1, returnMut);

        CtStatement assertOracle = facotry.Core().createCodeSnippetStatement();
        ((CtCodeSnippetStatement) assertOracle).setValue("assertFalse(true)");
        stmts.add(assertOracle);

        return stmts;
    }

    private List<CtStatement> processMutate2(RawTestCase testCase) {
        List<CtStatement> stmts = new LinkedList<>();
        List<Input> insertStmts = testCase.getInsertStmts();
        // mijung commenting out. We don't parse the original test case again by using
        // createTestCaseSkeleton method.
        // CtMethod originalTestcase = testCase.getOriginalTestCase();
        // stmts.addAll(originalTestcase.getBody().getStatements().subList(0,
        // originalTestcase.getBody().getStatements().size() - 1));
        for (Input insert : insertStmts) {
            // if(testCase.getMut().toString().contains("addAnnotation")){
            // System.out.println(insert.getVarName().toString());
            // for(CtElement ct:insert.getInput()){
            // System.out.println(ct.toString());
            // }
            // System.out.println("-------------------------");
            // }
            List<CtElement> mutateStmts = insert.getInput();
            for (int i = 0; i < mutateStmts.size(); i++) {
                stmts.add((CtStatement) mutateStmts.get(i));
            }
        }
        CtAbstractInvocation mutant = null;
        CtLocalVariable mutant_assign = null;
        CtAbstractInvocation mut = testCase.getMut();
        if (mut instanceof CtInvocationImpl) {
            mutant = ((CtInvocationImpl) mut).clone().setArguments(testCase.getArguments());
            if (Config.REGRESSION_MODE) {
                CtTypeReference returnType = ((CtInvocationImpl) mut).getType();
                if (returnType != null && !returnType.getSimpleName().equals("void")
                        && !mut.getExecutable().getSimpleName().equals("hashCode")) { // hashCode may cause flaky tests,
                                                                                      // thus we do not add regression
                                                                                      // oracles for this
                    // System.out.println("found primitive ret: " + returnType + " for " + mut);
                    Launcher launcher = new Launcher();
                    Factory factory = launcher.getFactory();
                    String name = returnType.getSimpleName().toLowerCase();
                    name = name.replace("[", "");
                    name = name.replace("]", "");
                    mutant_assign = factory.createLocalVariable(
                            returnType,
                            name + "_mut",
                            ((CtInvocationImpl) mut));
                    // System.out.println(mutant_assign);
                }
            }
            // for(CtStatement stmt:stmts){
            //
            // if(stmt.toString().contains(mut.getExecutable().getSimpleName())){
            // for(CtStatement stmt2:stmts){
            // System.out.println(stmt2.toString());
            // }
            // System.out.println(mut.toString());
            // break;
            // }
            // }
            // System.out.println("------------");

        } else if (mut instanceof CtConstructorCallImpl)
            mutant = ((CtConstructorCallImpl) mut).clone().setArguments(testCase.getArguments());
        if (mutant != null) {
            if (mutant_assign != null)
                stmts.add(mutant_assign);
            else
                stmts.add((CtStatement) mutant);
            return stmts;
        } else {
            return null;
        }
    }

    public CtClass<Object> generateMethods(List<CtStatement> stmts, String newClassName) {
        Factory facotry = new Launcher().getFactory();
        facotry.getEnvironment().disableConsistencyChecks(); // setSelfChecks(true);

        /*
         * Set up Class: new class name is original_MUT_MUTcount (e.g.,
         * XYSeries_ESTest_addOrUpdate_1)
         */
        CtClass<Object> clazz = facotry.Core().createClass();
        clazz.setSimpleName(newClassName);
        Set<ModifierKind> modifiers = new HashSet<>();
        modifiers.add(ModifierKind.PUBLIC);
        clazz.setModifiers(modifiers);

        /*
         * add throwable
         */
        CtTypeReference<? extends Throwable> throwable = facotry.createTypeReference();
        throwable.setSimpleName("Throwable");
        Set<CtTypeReference<? extends Throwable>> throwsExp = new HashSet<>();
        throwsExp.add(throwable);

        /*
         * Set up Method: only one method for each TestClass to skip possible compile
         * errors during javac
         */
        CtMethod<Object> newTestCase = facotry.createMethod();
        CtBlock<Object> methodBody = facotry.createBlock();
        List<CtStatement> reloads = new ArrayList<>();
        for (CtStatement stmt : stmts) {
            CtStatement ctStatement = facotry.Core().createCodeSnippetStatement();
            ((CtCodeSnippetStatement) ctStatement).setValue(stmt.toString());
            reloads.add(ctStatement);
        }
        methodBody.setStatements(reloads);
        newTestCase.setBody(methodBody);
        newTestCase.setSimpleName("test");
        CtTypeReference<Object> returnValue = facotry.createTypeReference();
        returnValue.setSimpleName("void");
        newTestCase.setType(returnValue);
        newTestCase.setModifiers(modifiers);
        clazz.addMethod(newTestCase);
        newTestCase.setThrownTypes(throwsExp);

        /*
         * Set up Annotation
         */
        CtAnnotationType testRefer = facotry.createAnnotationType("Test");
        CtAnnotation<Annotation> testAnno = facotry.createAnnotation();
        testAnno.setAnnotationType(testRefer.getReference());
        testAnno.addValue("timeout", 1000);
        List<CtAnnotation<? extends Annotation>> annotation = new LinkedList<>();
        annotation.add(testAnno);
        newTestCase.setAnnotations(annotation);
        return clazz;
    }

    /**
     * Same as generateMethods method except that this method does not reload
     * CtNodeSnippetStatement that was added to avoid class.toString crash
     *
     * @param stmts
     * @param newClassName
     * @return
     */
    public Pair<CtClass, List<String>> generateMethodsWithoutCodeSnippet(List<CtStatement> stmts, String newClassName) {
        Factory facotry = new Launcher().getFactory();
        facotry.getEnvironment().disableConsistencyChecks(); // setSelfChecks(true);
        /**
         * Set up Class: new class name is original_MUT_MUTcount (e.g.,
         * XYSeries_ESTest_addOrUpdate_1)
         */
        CtClass<Object> clazz = facotry.Core().createClass();
        clazz.setSimpleName(newClassName);
        Set<ModifierKind> modifiers = new HashSet<>();
        modifiers.add(ModifierKind.PUBLIC);
        clazz.setModifiers(modifiers);

        /*
         * add throwable
         */
        CtTypeReference<? extends Throwable> throwable = facotry.createTypeReference();
        throwable.setSimpleName("Throwable");
        Set<CtTypeReference<? extends Throwable>> throwsExp = new HashSet<>();
        throwsExp.add(throwable);

        /*
         * Set up Method: only one method for each TestClass to skip possible compile
         * errors during javac
         */
        CtMethod<Object> newTestCase = facotry.createMethod();
        CtBlock<Object> methodBody = facotry.createBlock();
        // List<CtStatement> reloads = new ArrayList<>();
        // for (CtStatement stmt : stmts) {
        // CtStatement ctStatement = facotry.Core().createCodeSnippetStatement();
        // ((CtCodeSnippetStatement) ctStatement).setValue(stmt.toString());
        // reloads.add(ctStatement);
        // }
        // methodBody.setStatements(reloads);
        methodBody.setStatements(stmts);
        newTestCase.setBody(methodBody);
        newTestCase.setSimpleName("test");
        CtTypeReference<Object> returnValue = facotry.createTypeReference();
        returnValue.setSimpleName("void");
        newTestCase.setType(returnValue);
        newTestCase.setModifiers(modifiers);
        newTestCase.setThrownTypes(throwsExp);

        /*
         * Set up Annotation
         */
        CtAnnotationType testRefer = facotry.createAnnotationType("org.junit.Test");
        CtAnnotation<Annotation> testAnno = facotry.createAnnotation();
        testAnno.setAnnotationType(testRefer.getReference());
        testAnno.addValue("timeout", 1000);
        List<CtAnnotation<? extends Annotation>> annotation = new LinkedList<>();
        annotation.add(testAnno);
        newTestCase.setAnnotations(annotation);

        List<String> classAndMethodStringPair = new ArrayList<>();
        classAndMethodStringPair.add(clazz.toString());
        classAndMethodStringPair.add(newTestCase.toString());

        clazz.addMethod(newTestCase);
        return new Pair(clazz, classAndMethodStringPair);
    }

    /**
     * generate mutate test cases
     *
     * @param testCases
     * @return
     */
    private List<List<CtStatement>> processMutate(List<RawTestCase> testCases) {
        List<List<CtStatement>> res = new ArrayList<>();
        for (RawTestCase testCase : testCases) {
            List<CtStatement> stmts = new LinkedList<>();
            List<Input> insertStmts = testCase.getInsertStmts();
            // mijung commenting out. We don't parse the original test case again by using
            // createTestCaseSkeleton method.
            // CtMethod originalTestcase = testCase.getOriginalTestCase();
            // stmts.addAll(originalTestcase.getBody().getStatements().subList(0,
            // originalTestcase.getBody().getStatements().size() - 1));
            for (Input insert : insertStmts) {
                List<CtElement> mutateStmts = insert.getInput();
                for (int i = 0; i < mutateStmts.size(); i++) {
                    stmts.add((CtStatement) mutateStmts.get(i));
                }
            }
            CtAbstractInvocation mutant = null;
            CtLocalVariable mutant_assign = null;
            CtAbstractInvocation mut = testCase.getMut();
            if (mut instanceof CtInvocationImpl) {
                mutant = ((CtInvocationImpl) mut).clone().setArguments(testCase.getArguments());
                if (Config.REGRESSION_MODE) {
                    CtTypeReference returnType = ((CtInvocationImpl) mut).getType();
                    if (returnType != null && !returnType.getSimpleName().equals("void")
                            && !mut.getExecutable().getSimpleName().equals("hashCode")) { // hashCode may cause flaky
                                                                                          // tests, thus we do not add
                                                                                          // regression oracles for this
                        // System.out.println("found primitive ret: " + returnType + " for " + mut);
                        Launcher launcher = new Launcher();
                        Factory factory = launcher.getFactory();
                        String name = returnType.getSimpleName().toLowerCase();
                        name = name.replace("[", "");
                        name = name.replace("]", "");
                        mutant_assign = factory.createLocalVariable(
                                returnType,
                                name + "_mut",
                                ((CtInvocationImpl) mut));
                        // System.out.println(mutant_assign);
                    }
                }
            } else if (mut instanceof CtConstructorCallImpl)
                mutant = ((CtConstructorCallImpl) mut).clone().setArguments(testCase.getArguments());
            if (mutant != null) {
                if (mutant_assign != null)
                    stmts.add(mutant_assign);
                else
                    stmts.add((CtStatement) mutant);
                res.add(stmts);
            }
        }
        return res;
    }

    /**
     * get raw mutate test case
     *
     * @return
     */
    private List<RawTestCase> getRawMutateTestcase() {
        Set<CtMethod> testcases = CandidatePool.getTestcases();
        Map<MUTInput, Set<List<Input>>> methodCombinations = inputCombinations.getCombinationsMap();
        List<RawTestCase> mutateTestcases = new ArrayList<>();
        for (CtMethod testcase : testcases) {
            CtAbstractInvocation invoke = getMUT(testcase);
            CtExecutableReference exec = invoke.getExecutable();
            String methodName = exec.getSimpleName();
            List<CtElement> argumentsTypes = invoke.getArguments();
            /**
             * get mut from last statement
             */
            MUTInput mut = getMUT(invoke, getArgsTypes(argumentsTypes));
            /**
             * get arguments
             */
            Set<List<Input>> args = methodCombinations.get(mut);
            assert args != null;
            for (List<Input> arg : args) {
                List<CtExpression> types = new LinkedList<>();
                List<Input> trueArguments = arg.subList(1, arg.size());
                List<Input> insertStmt = new ArrayList<>();
                /**
                 * add mutate types
                 */
                for (Input par : trueArguments) {
                    if (par.isVar())
                        insertStmt.add(par);
                    try {
                        types.add((CtExpression) par.getVarName());
                    } catch (Exception e) {
                        CtExpression variableAccess = par.getVarName().getFactory().createVariableRead();
                        ((CtVariableRead) variableAccess).setVariable((CtVariableReference) par.getVarName());
                        types.add(variableAccess);
                    }
                }
                /**
                 * add into mutate testcases
                 */
                mutateTestcases.add(new RawTestCase(testcase, insertStmt, types, invoke));
            }
        }
        return mutateTestcases;
    }

    /**
     * Given input combinations, this method create test case skeleton to be
     * generated.
     * Skeleton contains MUT invocationImpl, argument expressions, and their related
     * inputs.
     *
     * @return
     */
    public List<RawTestCase> createTestCaseSkeleton() {
        List<RawTestCase> mutateTestcases = new ArrayList<>();
        Map<MUTInput, Set<List<Input>>> methodCombinations = inputCombinations.getCombinationsMap();
        for (MUTInput mut : methodCombinations.keySet()) {
            Set<List<Input>> inputs = methodCombinations.get(mut);
            eachinput: for (List<Input> input : inputs) {
                List<CtExpression> types = new LinkedList<>();
                List<Input> insertStmt = new ArrayList<>();
                /**
                 * add mutate types
                 */
                CtAbstractInvocation invoke = null;
                CtAbstractInvocation mutInvo = mut.getMUTInvocation();
                if (mutInvo instanceof CtInvocationImpl) {
                    invoke = (CtInvocationImpl) mutInvo.clone();
                } else if (mutInvo instanceof CtConstructorCallImpl) {
                    invoke = (CtConstructorCallImpl) mutInvo.clone();
                }
                Input receiverObj = null;
                for (int i = 0; i < input.size(); i++) {
                    Input par = input.get(i);
                    if (i == 0 && !par.isMUTStatic() && par.isVar()) { // it is not static invocation
                        receiverObj = par;
                    }
                    if (par.isVar()) {
                        insertStmt.add(par);
                        /**
                         * Change var name of param arguments accordingly
                         */
                        if (i > 0) {
                            if (invoke.getArguments().get(i - 1) instanceof CtVariableReadImpl) {
                                if (par.getVarName() instanceof CtVariableReadImpl) // in case variable reference is
                                                                                    // typecasted as a paramter such as
                                                                                    // numericEntityUnescaper0_10.translate(((java.lang.CharSequence)
                                                                                    // (charBuffer0_3)));
                                    ((CtVariableReadImpl) invoke.getArguments().get(i - 1))
                                            .setVariable(((CtVariableReadImpl) par.getVarName()).getVariable());
                                else {
                                    if (par.getVarName() instanceof CtFieldReadImpl)
                                        continue eachinput;
                                    if (isVarTypeCased(par.getVarName()))
                                        continue eachinput;
                                    ((CtVariableReadImpl) invoke.getArguments().get(i - 1))
                                            .setVariable((CtVariableReference) par.getVarName());
                                }
                            }
                        }
                    }
                    try {
                        types.add((CtExpression) par.getVarName());
                    } catch (Exception e) {
                        CtExpression variableAccess = par.getVarName().getFactory().createVariableRead();
                        try {
                            ((CtVariableRead) variableAccess).setVariable((CtVariableReference) par.getVarName());
                        } catch (Exception gg) {
                            gg.printStackTrace();
                        }
                        types.add(variableAccess);
                    }
                }
                /**
                 * Change var name of receiver object accordingly
                 */
                if (receiverObj != null) {
                    CtElement var = receiverObj.getVarName();
                    if (isVarTypeCased(var))
                        continue eachinput;
                    ((CtVariableReadImpl) ((CtInvocationImpl) invoke).getTarget())
                            .setVariable((CtVariableReference) var);
                }
                receiverObj = null;
                /**
                 * add into mutate testcases
                 */
                mutateTestcases.add(new RawTestCase(insertStmt, types, invoke));
            }
        }
        return mutateTestcases;
    }

    private boolean isVarTypeCased(CtElement var) {
        if (var instanceof CtVariableReadImpl ||
                var instanceof CtArrayReadImpl) { // there are some cases where var is type casted. we ignore this case.
            List<CtTypeReference> casts = ((CtExpression) var).getTypeCasts();
            if (casts.size() > 0)
                return true;
        }
        return false;
    }

    private List<CtTypeReference> getArgsTypes(List<CtElement> args) {
        List<CtTypeReference> types = new ArrayList<>();
        for (CtElement arg : args) {
            CtTypeReference type = null;
            CtTypeReference castType = null;
            List<CtTypeReference> casts = ((CtExpression) arg).getTypeCasts();
            type = ((CtExpression) arg).getType();
            /**
             * get the add the cast type
             */
            if (casts.size() != 0)
                castType = casts.get(0);

            assert type != null || castType != null;
            /**
             * only add if no cast type
             */
            if (type != null && castType == null
                    && (type.isPrimitive() || type.toString().equals("java.lang.String"))) {
                CandidatePool.insertDirectToValues(type, arg);
            }

            if (castType != null &&
                    arg.getElements(new TypeFilter<>(CtVariableReference.class)).size() == 0) { // check if arg is not
                                                                                                // variable
                CandidatePool.insertDirectToValues(castType, arg);
            }
            /**
             * add type
             */
            if (castType != null) {
                types.add(castType);
            } else {
                types.add(type);
            }

        }
        return types;
    }

    private MUTInput getMUT(CtAbstractInvocation methodName, List<CtTypeReference> argumentsTypes) {
        Set<MUTInput> muts = inputCombinations.getCombinationsMap().keySet();
        for (MUTInput mut : muts) {
            if (mut.getMUTInvocation().equals(methodName) && mut.equals(argumentsTypes)) {
                return mut;
            }
        }
        throw new RuntimeException("no match mut found for " + methodName.getExecutable().getSimpleName());
    }

    public CtAbstractInvocation getMUT(List<CtStatement> stmts) {
        CtStatement lastStmt = stmts.get(stmts.size() - 1);
        return getMUT(lastStmt);
    }

    public CtAbstractInvocation getMUT(CtMethod testcase) {
        CtStatement stmt = getLastMUTStatement(testcase);
        CtAbstractInvocation invoke = null;
        if (stmt instanceof CtInvocationImpl) {
            invoke = (CtInvocationImpl) stmt;
        } else if (stmt.getElements(new TypeFilter<>(CtInvocationImpl.class)).size() == 1) {
            invoke = stmt.getElements(new TypeFilter<>(CtInvocationImpl.class)).get(0);
        } else if (stmt instanceof CtConstructorCallImpl) {
            invoke = (CtConstructorCallImpl) stmt;
        } else if (stmt.getElements(new TypeFilter<>(CtConstructorCallImpl.class)).size() == 1) {
            invoke = stmt.getElements(new TypeFilter<>(CtConstructorCallImpl.class)).get(0);
        } else {
            return null;
        }
        return invoke;
    }

    /**
     * init input combinations
     */
    private void initInputCombinations(String testFile, long time_budget, long startTime) {
        inputCombinations.run(testFile, time_budget, startTime);
    }

    /**
     * Add regression assertions
     */
    public void addRegressionOracles() {
        launcher = new Launcher();
        final Analyzer analyzer = new Analyzer();
        final ObserverInstrumenter collector = new ObserverInstrumenter(launcher.getFactory());

        observerAddedTests = new ArrayList<>();
        for (CtClass ctClass : generatedTests) {

            // Analyze
            Map<CtMethod, List<CtLocalVariable>> localVariablesPerTestMethod = analyzer.analyze(ctClass, false);
            Map<CtMethod, List<CtLocalVariable>> localVariablesPrimitive = analyzer.analyze(ctClass, true);
            // localVariablesPerTestMethod.keySet().stream().forEach(key ->
            // System.out.println("{"+ key.getParent(CtClass.class).getSimpleName() + "#" +
            // key.getSimpleName() + "=["+ localVariablesPerTestMethod.get(key) +"]"));
            // Collect
            Set<CtMethod<?>> methods = ctClass.getMethods();
            for (CtMethod ctMethod : methods) {
                Pair<CtClass, List<String>> observerAddedClassAndStringPair = collector.instrumentObserver(ctMethod,
                        localVariablesPerTestMethod, localVariablesPrimitive);
                CtClass<Object> observerAddedClass = observerAddedClassAndStringPair.getKey();
                // observerAddedTests.add(observerAddedClass);
            }
        }
        // To save memory, reset generatedTests. We won't need it any more
        generatedTests = new ArrayList<>();
    }

    public Pair<CtClass, List<String>> addRegressionOracleToTest(CtClass<Object> generatedTest) {
        // Analyze
        Map<CtMethod, List<CtLocalVariable>> localVariablesPerTestMethod = analyzer.analyze(generatedTest, false);
        Map<CtMethod, List<CtLocalVariable>> localVariablesPrimitive = analyzer.analyze(generatedTest, true);
        // Collect
        Set<CtMethod<?>> methods = generatedTest.getMethods();
        if (methods.size() != 1) {
            System.out.println("The number of method in class is not 1.");
            System.exit(1);
        }
        for (CtMethod<?> ctMethod : methods) {
            Pair<CtClass, List<String>> observerAddedClassAndStringPair = collector.instrumentObserver(ctMethod,
                    localVariablesPerTestMethod, localVariablesPrimitive);
            CtClass<Object> observerAddedClass = observerAddedClassAndStringPair.getKey();
            // observerAddedTests.add(observerAddedClass);
            return observerAddedClassAndStringPair;
        }
        return null;
    }
}

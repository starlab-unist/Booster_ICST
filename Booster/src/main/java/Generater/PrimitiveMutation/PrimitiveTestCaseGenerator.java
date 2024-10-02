package Generater.PrimitiveMutation;

import Generater.MUTMutation.TestCaseGenerator;
import RegressionOracles.Analyzer;
import RegressionOracles.ObserverInstrumenter;
import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;
import utils.Config;
import utils.Pair;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.annotation.Annotation;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class PrimitiveTestCaseGenerator {
    private String testClassName;
    private String packageAndImport;
//    private List<CtClass<Object>> generatedTests;
//    private List<CtClass<Object>> observerAddedTests = new ArrayList<>(); //only for regression mode
    private Launcher launcher;
    private final Analyzer analyzer;
    private final ObserverInstrumenter collector;

    /*
     * record stmt use
     */
    private Map<String, Map<CtStatement, Set<CtStatement>>> stmtUses = new HashMap<>();
    /*
     * record stmt candidate
     */
    private Map<String, Map<CtStatement, List<CtStatement>>> stmtCandidates = new HashMap<>();
    /*
     * record stmt used
     */
    private Map<String, Map<CtStatement, Set<CtStatement>>> existedStmts = new HashMap<>();
    /*
     * maintain duplicate map
     */
    private HashSet<String> existsTestcases = new HashSet<>();
    private Map<String, Random> randoms = new HashMap<>();

    /**
     * init test case with input combinations
     */
    public PrimitiveTestCaseGenerator(String testFile, long time_budget, long startTime) {
        testClassName = extractFileName(testFile, ".java");
//        generatedTests = new ArrayList<>();
        getImportAndPackage(testFile);
        PrimitiveMutateParser.run(testFile,time_budget,startTime);
        PrimitiveMutateInput.run(time_budget,startTime);
        launcher = new Launcher();
        analyzer = new Analyzer();
        collector = new ObserverInstrumenter(launcher.getFactory());
    }

    public static String md5(String plainText) {
        byte[] secretBytes = null;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(plainText.getBytes());
            secretBytes = md.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        String md5code = new BigInteger(1, secretBytes).toString(16);
        for (int i = 0; i < 32 - md5code.length(); i++) {
            md5code = "0" + md5code;
        }
        return md5code;
    }

    public Launcher getLauncher() {
        return launcher;
    }

//    public List<CtClass<Object>> getObserverAddedTests() {
//        return observerAddedTests;
//    }

    public String getPackageAndImport() {
        return packageAndImport;
    }

    public String getTestClassName() {
        return testClassName;
    }

//    public List<CtClass<Object>> getGeneratedTests() {
//        return generatedTests;
//    }

    private void printCandidatePools(Map<String, Set<CtElement>> candidates) {
        for (String s : candidates.keySet()) {
            System.out.println("type : " + s);
            for (CtElement e : candidates.get(s)) {
                System.out.println("\t" + e);
            }
        }
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

    public Pair<CtClass,String> mutateTest(CtMethod testCase, int count) {
        String testCaseName = testCase.getSimpleName();
        if (count == 0) {
            existedStmts.put(testCaseName, new HashMap<>());
            randoms.put(testCaseName, new Random());
        }
        Map<CtStatement, Set<CtStatement>> existedStmt = existedStmts.get(testCaseName);
        Random random = randoms.get(testCaseName);

        List<CtStatement> statements = testCase.getBody().getStatements();
        List<CtStatement> mutatedStmts = new ArrayList<>();
        for (CtStatement statement : statements) {
            /*
             * check whether it can be mutated
             */
            if (!isMutatable(statement)) {
                mutatedStmts.add(statement);
                continue;
            }
            if (statement instanceof CtTry) {
                /*mutate try block*/
                List<CtStatement> tryBlockStmts = ((CtTry) statement).getBody().getStatements();
                List<CtStatement> tryBlockMutatedStmts = new ArrayList<>();
                for (CtStatement stmt : tryBlockStmts) {
                    if (!isMutatable(stmt)) {
                        tryBlockMutatedStmts.add(stmt);
                        continue;
                    }
                    if (!existedStmt.containsKey(stmt))
                        existedStmt.put(stmt, new HashSet());
                    CtStatement selectedStmt = null;
                    /*
                     * get candidate
                     */
                    List<CtStatement> cand = getMutatableList(stmt);
                        /*
                            should mutate
                         */
                    if (!shouldMutate(cand)) {
                        tryBlockMutatedStmts.add(stmt);
                        continue;
                    }
                    /*
                     * get used statements
                     */
                    Set<CtStatement> used = existedStmt.get(stmt);
                    /*
                     * get total use
                     */
                    Set<CtStatement> total = getMutatableSet(stmt);
                    /*
                     * if full randomly pick
                     */
                    if (total.size() == used.size()) {
                        selectedStmt = cand.get(random.nextInt(cand.size()));
                    } else {
                        CtStatement tmp = null;
                        while (true) {
                            tmp = cand.get(random.nextInt(cand.size()));
                            if (!used.contains(tmp))
                                break;
                        }
                        assert tmp != null;
                        used.add(tmp);
                        selectedStmt = tmp;
                    }
                    tryBlockMutatedStmts.add(selectedStmt);
                }
                CtTry mutatedTry = (CtTry) statement.clone();
                mutatedTry.getFactory().getEnvironment().disableConsistencyChecks();
                CtBlock<?> mutatedTryBody = mutatedTry.getBody();
                mutatedTryBody.setStatements(tryBlockMutatedStmts);
                mutatedStmts.add(mutatedTry);
                continue;
            }
            if (!existedStmt.containsKey(statement))
                existedStmt.put(statement, new HashSet());
            CtStatement selectedStmt = null;
            /*
             * get candidate
             */
            List<CtStatement> cand = getMutatableList(statement);
            /*
             * should mutate?
             */
            if (!shouldMutate(cand)) {
                mutatedStmts.add(statement.clone());
                continue;
            }
            /*
             * get used statements
             */
            Set<CtStatement> used = existedStmt.get(statement);
            /*
             * get total use
             */
            Set<CtStatement> total = getMutatableSet(statement);
            /*
             * if full randomly pick
             */
            if (total.size() == used.size()) {
                selectedStmt = cand.get(random.nextInt(cand.size()));
            } else {
                CtStatement tmp = null;
                while (true) {
                    tmp = cand.get(random.nextInt(cand.size()));
                    if (!existedStmt.get(statement).contains(tmp))
                        break;
                }
                assert tmp != null;
                existedStmt.get(statement).add(tmp);
                selectedStmt = tmp;
            }
            assert selectedStmt != null;
            mutatedStmts.add(selectedStmt.clone());
        }
//        /*check whether it is duplicate*/
//        String testCaseString = "";
//        for (CtStatement stmt : mutatedStmts) {
//            testCaseString += stmt.clone().toString();
//        }
//        /*
//         * should we use greedy algorithm?
//         */
//        String checkSum = md5(testCaseString);
//        if (!existsTestcases.contains(checkSum))
//            existsTestcases.add(checkSum);
//        else
//            return null;

        /*
         * generate test file
         */
        if (Config.REGRESSION_MODE && mutatedStmts.size() > 0) {
            CtStatement lastStmt = mutatedStmts.get(mutatedStmts.size() - 1);
            CtAbstractInvocation mut = TestCaseGenerator.getMUT(lastStmt);
            if (lastStmt.getElements(new TypeFilter<>(CtLocalVariable.class)).size() <= 0 &&
                    mut != null && mut instanceof CtInvocationImpl) {
                CtLocalVariable mutant_assign = null;
                CtTypeReference returnType = ((CtInvocationImpl) mut).getType();
                if (returnType != null && !returnType.getSimpleName().equals("void")
                        && !mut.getExecutable().getSimpleName().equals("hashCode")) { //hashCode may cause flaky tests, thus we do not add regression oracles for this
//                    System.out.println("found primitive ret: " + returnType + " for " + mut);
                    Launcher launcher = new Launcher();
                    Factory factory = launcher.getFactory();
                    String name = returnType.getSimpleName().toLowerCase();
                    name = name.replace("[", "");
                    name = name.replace("]", "");
                    mutant_assign = factory.createLocalVariable(
                            returnType,
                            name + "_mut",
                            ((CtInvocationImpl) mut)
                    );
//                    System.out.println(mutant_assign);
                }
                if (mutant_assign != null) {
                    mutatedStmts.remove(lastStmt); //removing last stmt, which is treated as MUT.Otherwise, the last statement will be duplicate with mutant_assign
                    mutatedStmts.add(mutant_assign); //adding the new mutant_assign again
                }
            }
        }
        List<CtLocalVariable> variableDefinations = new ArrayList<>();
        List<CtVariableReadImpl> reads = new ArrayList<>();
        List<CtCatchImpl> catches = new ArrayList<>();
        for (CtStatement statement : mutatedStmts) {
            variableDefinations.addAll(statement.getElements(new TypeFilter<>(CtLocalVariable.class)));
//                reads.addAll(statement.getElements(new TypeFilter<>(CtVariableReadImpl.class)));
//                catches.addAll(statement.getElements(new TypeFilter<>(CtCatchImpl.class)));
        }
        /*
         * check duplicate definitions
         */
        Set<String> localVarNames = new HashSet<>();
        boolean hasDuplicateDefinition = false;
        if (variableDefinations != null) {
            for (CtLocalVariable localVariable : variableDefinations) {
                if (!localVarNames.contains(localVariable.getSimpleName())) {
                    localVarNames.add(localVariable.getSimpleName());
                } else {
                    hasDuplicateDefinition = true;
                    break;
                }
            }
        }
        if (hasDuplicateDefinition) {
            return null;
        }

        if (Config.REGRESSION_MODE) {
            Pair<CtClass,List<String>> mutatedTestAndStringPair = generateMethodsWithoutCodeSnippet(mutatedStmts, testClassName + "_P_" + testCase.getSimpleName() + "_" + count);
            Pair<CtClass,List<String>> observerAddedTestAndStringPair = addRegressionOracleToTest(mutatedTestAndStringPair.getKey());
            String clazzStr = mutatedTestAndStringPair.getValue().get(0);
            String methodStr = mutatedTestAndStringPair.getValue().get(1);
            for(String oracle:observerAddedTestAndStringPair.getValue()){
                if(!oracle.contains("RegressionOracles.RegressionUtil.Logger.observe")){
                    oracle = oracle.trim() + " // if statement for MUT null check";
                    methodStr = methodStr.substring(0, methodStr.lastIndexOf("}"));
                    methodStr = methodStr + "\t" + oracle + "\n}";
                } else {
                    methodStr = methodStr.substring(0, methodStr.lastIndexOf("}"));
                    methodStr = methodStr + "\t\t" + oracle.trim() + "\n}";
                }
            }
            methodStr = "\t" + methodStr.replaceAll("\n","\n\t");

            String finalStr = clazzStr.substring(0, clazzStr.lastIndexOf("}")) + "\n"+methodStr+"\n}";

//            generatedTests.add(mutatedTestAndStringPair.getKey());
            return new Pair<CtClass,String>(observerAddedTestAndStringPair.getKey(), finalStr);
        } else {
            CtClass<Object> mutatedTest = generateMethods(mutatedStmts, testClassName + "_P_" + testCase.getSimpleName() + "_" + count);
//            generatedTests.add(mutatedTest);
            return new Pair<CtClass,String>(mutatedTest, mutatedTest.toString());
        }
        /*
         * add test case
         */


    }

    public void run() {
        Set<CtMethod> testcases = PrimitiveMutateParser.getTestcases();
        processTestcases(testcases);
    }

    private void processTestcases(Set<CtMethod> testcases) {
        for (CtMethod testcase : testcases) {
            mutateTestcase(testcase);
        }
    }

    private boolean isMutatable(CtStatement statement) {
        return PrimitiveMutateInput.getInputAssignmentCombinations().containsKey(statement) || PrimitiveMutateInput.getInputCombinations().containsKey(statement) || statement instanceof CtTryImpl;
    }

    private Set<CtStatement> getMutatableSet(CtStatement statement) {
        Set<CtStatement> res = new HashSet<>();
        Map<Integer, List<CtStatement>> args = PrimitiveMutateInput.getInputAssignmentCombinations().get(statement);
        List<CtStatement> assign = PrimitiveMutateInput.getInputCombinations().get(statement);
        if (assign != null)
            res.addAll(assign);
        if (args != null)
            for (List<CtStatement> arg : args.values())
                res.addAll(arg);
        res.add(statement);
        return res;
    }

    private List<CtStatement> getMutatableList(CtStatement statement) {
        Set<CtStatement> res = new HashSet<>();
        Map<Integer, List<CtStatement>> args = PrimitiveMutateInput.getInputAssignmentCombinations().get(statement);
        List<CtStatement> assign = PrimitiveMutateInput.getInputCombinations().get(statement);
        if (assign != null)
            res.addAll(assign);
        if (args != null)
            for (List<CtStatement> arg : args.values())
                res.addAll(arg);
        res.add(statement);
        List<CtStatement> list = new ArrayList<>();
        list.addAll(res);
        return list;
    }

    /*determine should be mutated*/
    private boolean shouldMutate(List<CtStatement> candidates) {
        double rand = Math.random();
        return rand > (1.0 / ((double) candidates.size() + 1));
    }

    private void mutateTestcase(CtMethod testcase) {
        List<CtStatement> statements = testcase.getBody().getStatements();
        /*
         * record stmt use
         */
        Map<CtStatement, Set<CtStatement>> stmtUse = new HashMap<>();
        /*
         * record stmt candidate
         */
        Map<CtStatement, List<CtStatement>> stmtCandidate = new HashMap<>();
        /*
         * record stmt used
         */
        Map<CtStatement, Set<CtStatement>> existedStmt = new HashMap<>();
        Random random = new Random();
        /*
         * init count
         */
        int count = 0;
        /*
         * maintain duplicate map
         */
        HashMap<String, Integer> existsTestcases = new HashMap<>();
        while (count < Config.PRIMITIVE_TEST_NUM) {
            List<CtStatement> mutatedStmts = new ArrayList<>();
            for (CtStatement statement : statements) {
                /*
                 * check whether it can be mutated
                 */
                if (!isMutatable(statement)) {
                    mutatedStmts.add(statement);
                    continue;
                }
                if (statement instanceof CtTry) {
                    /*mutate try block*/
                    List<CtStatement> tryBlockStmts = ((CtTry) statement).getBody().getStatements();
                    List<CtStatement> tryBlockMutatedStmts = new ArrayList<>();
                    for (CtStatement stmt : tryBlockStmts) {
                        if (!isMutatable(stmt)) {
                            tryBlockMutatedStmts.add(stmt);
                            continue;
                        }
                        if (!stmtUse.containsKey(stmt))
                            stmtUse.put(stmt, getMutatableSet(stmt));
                        if (!stmtCandidate.containsKey(stmt))
                            stmtCandidate.put(stmt, getMutatableList(stmt));
                        if (!existedStmt.containsKey(stmt))
                            existedStmt.put(stmt, new HashSet());
                        CtStatement selectedStmt = null;
                        /*
                         * get candidate
                         */
                        List<CtStatement> cand = stmtCandidate.get(stmt);
                        /*
                            should mutate
                         */
                        if (!shouldMutate(cand)) {
                            tryBlockMutatedStmts.add(stmt);
                            continue;
                        }
                        /*
                         * get used statements
                         */
                        Set<CtStatement> used = existedStmt.get(stmt);
                        /*
                         * get total use
                         */
                        Set<CtStatement> total = stmtUse.get(stmt);
                        /*
                         * if full randomly pick
                         */
                        if (total.size() == used.size()) {
                            selectedStmt = cand.get(random.nextInt(cand.size()));
                        } else {
                            CtStatement tmp = null;
                            while (true) {
                                tmp = cand.get(random.nextInt(cand.size()));
                                if (!existedStmt.get(stmt).contains(tmp))
                                    break;
                            }
                            assert tmp != null;
                            existedStmt.get(stmt).add(tmp);
                            selectedStmt = tmp;
                        }
                        tryBlockMutatedStmts.add(selectedStmt);
                    }
                    CtTry mutatedTry = (CtTry) statement.clone();
                    mutatedTry.getFactory().getEnvironment().disableConsistencyChecks();
                    CtBlock<?> mutatedTryBody = mutatedTry.getBody();
                    mutatedTryBody.setStatements(tryBlockMutatedStmts);
                    mutatedStmts.add(mutatedTry);
                    continue;
                }
                if (!stmtUse.containsKey(statement))
                    stmtUse.put(statement, getMutatableSet(statement));
                if (!stmtCandidate.containsKey(statement))
                    stmtCandidate.put(statement, getMutatableList(statement));
                if (!existedStmt.containsKey(statement))
                    existedStmt.put(statement, new HashSet());
                CtStatement selectedStmt = null;
                /*
                 * get candidate
                 */
                List<CtStatement> cand = stmtCandidate.get(statement);
                /*
                 * should mutate?
                 */
                if (!shouldMutate(cand)) {
                    mutatedStmts.add(statement.clone());
                    continue;
                }
                /*
                 * get used statements
                 */
                Set<CtStatement> used = existedStmt.get(statement);
                /*
                 * get total use
                 */
                Set<CtStatement> total = stmtUse.get(statement);
                /*
                 * if full randomly pick
                 */
                if (total.size() == used.size()) {
                    selectedStmt = cand.get(random.nextInt(cand.size()));
                } else {
                    CtStatement tmp = null;
                    while (true) {
                        tmp = cand.get(random.nextInt(cand.size()));
                        if (!existedStmt.get(statement).contains(tmp))
                            break;
                    }
                    assert tmp != null;
                    existedStmt.get(statement).add(tmp);
                    selectedStmt = tmp;
                }
                assert selectedStmt != null;
                mutatedStmts.add(selectedStmt.clone());
            }
            /*check whether it is duplicate*/
            String testcaseString = "";
            for (CtStatement stmt : mutatedStmts) {
                testcaseString += stmt.clone().toString();
            }
            /*
             * should we use greedy algorithm?
             */
            count++;
            String checkSum = md5(testcaseString);
            if (!existsTestcases.containsKey(checkSum))
                existsTestcases.put(checkSum, 0);
            else
                continue;
            /*
             * generate test file
             */
            if (Config.REGRESSION_MODE && mutatedStmts.size() > 0) {
                CtStatement lastStmt = mutatedStmts.get(mutatedStmts.size() - 1);
                CtAbstractInvocation mut = TestCaseGenerator.getMUT(lastStmt);
                if (lastStmt.getElements(new TypeFilter<>(CtLocalVariable.class)).size() <= 0 &&
                        mut != null && mut instanceof CtInvocationImpl) {
                    CtLocalVariable mutant_assign = null;
                    CtTypeReference returnType = ((CtInvocationImpl) mut).getType();
                    if (returnType != null && !returnType.getSimpleName().equals("void")
                            && !mut.getExecutable().getSimpleName().equals("hashCode")) { //hashCode may cause flaky tests, thus we do not add regression oracles for this
//                    System.out.println("found primitive ret: " + returnType + " for " + mut);
                        Launcher launcher = new Launcher();
                        Factory factory = launcher.getFactory();
                        String name = returnType.getSimpleName().toLowerCase();
                        name = name.replace("[", "");
                        name = name.replace("]", "");
                        mutant_assign = factory.createLocalVariable(
                                returnType,
                                name + "_mut",
                                ((CtInvocationImpl) mut)
                        );
//                    System.out.println(mutant_assign);
                    }
                    if (mutant_assign != null) {
                        mutatedStmts.remove(lastStmt); //removing last stmt, which is treated as MUT.Otherwise, the last statement will be duplicate with mutant_assign
                        mutatedStmts.add(mutant_assign); //adding the new mutant_assign again
                    }
                }
            }
            List<CtLocalVariable> variableDefinations = new ArrayList<>();
            List<CtVariableReadImpl> reads = new ArrayList<>();
            List<CtCatchImpl> catches = new ArrayList<>();
            for (CtStatement statement : mutatedStmts) {
                variableDefinations.addAll(statement.getElements(new TypeFilter<>(CtLocalVariable.class)));
//                reads.addAll(statement.getElements(new TypeFilter<>(CtVariableReadImpl.class)));
//                catches.addAll(statement.getElements(new TypeFilter<>(CtCatchImpl.class)));
            }
            /*
             * check duplicate definitions
             */
            Set<String> localVarNames = new HashSet<>();
            boolean hasDuplicateDefinition = false;
            if (variableDefinations != null) {
                for (CtLocalVariable localVariable : variableDefinations) {
                    if (!localVarNames.contains(localVariable.getSimpleName())) {
                        localVarNames.add(localVariable.getSimpleName());
                    } else {
                        hasDuplicateDefinition = true;
                        break;
                    }
                }
            }
            if (hasDuplicateDefinition) {
                continue;
            }

            CtClass<Object> mutatedTest = null;
            if (Config.REGRESSION_MODE) {
//                mutatedTest = generateMethodsWithoutCodeSnippet(mutatedStmts, testClassName + "_P_" + testcase.getSimpleName() + "_" + count); // deprecated
                return;
            } else {
                mutatedTest = generateMethods(mutatedStmts, testClassName + "_P_" + testcase.getSimpleName() + "_" + count);
            }
            /*
             * check symbol not found error: disabled
             boolean symbolNotFound = false;
             Set<String> readVars = new HashSet<>();
             for (CtVariableReadImpl read : reads) {
             String var = read.getVariable().getSimpleName();
             readVars.add(var);
             }

             for (String read : readVars) {
             if (!localVarNames.contains(read) && catches.size() == 0) {
             //                    System.out.println(read);
             symbolNotFound = true;
             break;
             }
             }
             if (symbolNotFound) {
             continue;
             }
             */
            /*
             * add test case
             */
//            generatedTests.add(mutatedTest);
        }
    }

    private CtAbstractInvocation getMUT(List<CtStatement> stmts) {
        if (stmts == null || stmts.size() == 0)
            return null;
        CtStatement stmt = stmts.get(stmts.size() - 1);
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

    public CtClass<Object> generateMethods(List<CtStatement> stmts, String newClassName) {
        Factory facotry = new Launcher().getFactory();
        facotry.getEnvironment().disableConsistencyChecks(); //setSelfChecks(true);

        /*
         * Set up Class: new class name is original_MUT_MUTcount (e.g., XYSeries_ESTest_addOrUpdate_1)
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
         * Set up Method: only one method for each TestClass to skip  possible compile errors during javac
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
        CtAnnotationType testRefer = facotry.createAnnotationType("org.junit.Test");
        CtAnnotation<Annotation> testAnno = facotry.createAnnotation();
        testAnno.setAnnotationType(testRefer.getReference());
        testAnno.addValue("timeout", 1000);
        List<CtAnnotation<? extends Annotation>> annotation = new LinkedList<>();
        annotation.add(testAnno);
        newTestCase.setAnnotations(annotation);
        return clazz;
    }

    /**
     * Same as generateMethods method except that this method does not reload CtNodeSnippetStatement that was added to avoid class.toString crash
     *
     * @param stmts
     * @param newClassName
     * @return
     */
    public Pair<CtClass, List<String>> generateMethodsWithoutCodeSnippet(List<CtStatement> stmts, String newClassName) {
        Factory facotry = new Launcher().getFactory();
        facotry.getEnvironment().disableConsistencyChecks(); //setSelfChecks(true);

        /*
         * Set up Class: new class name is original_MUT_MUTcount (e.g., XYSeries_ESTest_addOrUpdate_1)
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
         * Set up Method: only one method for each TestClass to skip  possible compile errors during javac
         */
        CtMethod<Object> newTestCase = facotry.createMethod();
        CtBlock<Object> methodBody = facotry.createBlock();
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
        return new Pair<CtClass, List<String>>(clazz, classAndMethodStringPair);
    }

    /**
     * Add regression assertions
     */
    public void addRegressionOracles() {
//        launcher = new Launcher();
//
//        final Analyzer analyzer = new Analyzer();
//        final ObserverInstrumenter collector = new ObserverInstrumenter(launcher.getFactory());

//        observerAddedTests = new ArrayList<>();
//        for (CtClass ctClass : generatedTests) {
//            System.out.println("classname: " + ctClass.getQualifiedName());
            // Analyze
//            Map<CtMethod, List<CtLocalVariable>> localVariablesPerTestMethod = analyzer.analyze(ctClass, false);
//            Map<CtMethod, List<CtLocalVariable>> localVariablesPrimitive = analyzer.analyze(ctClass, true);
//            localVariablesPerTestMethod.keySet().stream().forEach(key -> System.out.println("{"+ key.getParent(CtClass.class).getSimpleName() + "#" + key.getSimpleName() + "=["+ localVariablesPerTestMethod.get(key) +"]"));
            // Collect
//            Set<CtMethod<?>> methods = ctClass.getMethods();
//            for (CtMethod ctMethod : methods) {
//                Pair<CtClass,List<String>> observerAddedClassAndStringPair = collector.instrumentObserver(ctMethod, localVariablesPerTestMethod, localVariablesPrimitive);
//                CtClass<Object> observerAddedClass = observerAddedClassAndStringPair.getKey();
//                observerAddedTests.add(observerAddedClass);
//            }
//        }
        //To save memory, reset generatedTests. We won't need it any more
//        generatedTests = new ArrayList<>();
    }

    public Pair<CtClass,List<String>> addRegressionOracleToTest(CtClass<Object> generatedTest) {
        // Analyze
        Map<CtMethod, List<CtLocalVariable>> localVariablesPerTestMethod = analyzer.analyze(generatedTest, false);
        Map<CtMethod, List<CtLocalVariable>> localVariablesPrimitive = analyzer.analyze(generatedTest, true);
        // Collect
        Set<CtMethod<?>> methods = generatedTest.getMethods();
        if(methods.size()!=1){
            System.out.println("The number of method in class is not 1.");
            System.exit(1);
        }
        for (CtMethod<?> ctMethod : methods) {
            Pair<CtClass,List<String>> observerAddedClassAndStringPair = collector.instrumentObserver(ctMethod, localVariablesPerTestMethod, localVariablesPrimitive);
            CtClass<Object> observerAddedClass = observerAddedClassAndStringPair.getKey();
//            observerAddedTests.add(observerAddedClass);
            return observerAddedClassAndStringPair;
        }
        return null;
    }
}

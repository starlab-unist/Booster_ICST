package Compiler;

import Generater.MUTMutation.ASTParser;
import Generater.MUTMutation.CandidatePool;
import Generater.MUTMutation.Input;
import Generater.MUTMutation.MUTInput;
import RegressionOracles.Analyzer;
import RegressionOracles.AssertionAdder;
import RegressionOracles.TryCatchFailAdder;
import org.junit.experimental.ParallelComputer;
import org.junit.runner.Computer;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.mdkt.compiler.InMemoryJavaCompiler;
import spoon.Launcher;
import spoon.SpoonException;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtVariableReadImpl;
import spoon.support.reflect.code.CtConstructorCallImpl;
import sun.misc.URLClassPath;
import utils.Config;
import utils.Pair;
import utils.TriItem;

import javax.tools.*;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.*;

public class Compiler {
    private List<CtClass<Object>> testcases;
    private String packageName;
    private String packageAndImport = "";
    private Map<String, String> sourceMap = new HashMap<>();
    private List<String> options = new ArrayList<String>();
    private Set<String> compileSuccessTestcases = new HashSet<>();
    private Set<String> compileFailedTestCases = new HashSet<>();
    private Set<String> runFailedTestCases = new HashSet<>();
    private Map<String, StackTraceElement[]> runFailedStacktrace = new HashMap<>();
    private Map<String, Set<String>> failedBuckets = new HashMap<>();
    private String TESTCASE_NAME = "test";
    private CtClass<Object> mutatedClass;
    private Map<String, Class<?>> compiledSuccessMap = new HashMap<>();
    private Class<?> compiledClass = null;
    private Map<String, CtMethod> nameToMethod = new HashMap<>();
    private int runCount;
    private int failureCount;
    private int compileCount;
    private int numOfBucket;
    private List<List<CtMethod>> splitTestcases = new LinkedList<>();
    private Set<String> runningClassNames = new HashSet<>();
    private List<CtMethod> mutatedTestcases = new ArrayList<>();
    private List<CtMethod> usedBucketTestcases = new ArrayList<>();
    private List<CtMethod> compiledTestList = new LinkedList<>();
    private List<CtMethod> runnableTestList = new LinkedList<>();
    private List<String> runnableTestStringList = new LinkedList<>();
    private Map<String, Failure> testNameToFailMap = new HashMap<>();
    private Set<String> timedOutTestNameSet = new HashSet<>();
    private static int numOfFailingTests = 0;
    private static int numOfPassingTests = 0;
    private static int mutVariableCounter = 0;

    /**
     * init the test cases
     *
     * @param packageAndImport
     */
    public Compiler(String packageAndImport, String packageName) {
        this.packageAndImport = packageAndImport;
        this.packageName = packageName;
        // init compile options
        initCompileOptions();
        initOutputFolderForClassLoader();
    }

    /**
     * write to file
     *
     * @param fileName
     * @param clazz
     * @throws Exception
     */
    private static void writeFile(String fileName, String clazz) throws Exception {
        File sourceFile = new File(Config.OUTPUT_PATH + File.separator + fileName + ".java");
        sourceFile.createNewFile();
        FileWriter fileWriter = new FileWriter(sourceFile.getAbsoluteFile());
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.print(clazz);
        printWriter.close();
    }

    /**
     * main entry of runner for compiling
     */
    public void runCompile() throws Exception {
        // init map
        generateSourceMap();
        // init compile options
        initCompileOptions();
        // compile all the testcases
        compile();
    }

    /**
     * main entry of for Testcase
     */
    public void runTestcase() throws Exception {
        runTestCases();
    }

    public void runTestCase(CtMethod compiledTest) {
        CtClass<Object> clazz = generateClass(Collections.singletonList(compiledTest), compiledTest.getSimpleName());
        CtMethod method = clazz.getMethod(compiledTest.getSimpleName());
        // System.out.println("Here: " + (method instanceof CtInvocation));
    }

    private void runTestCases() throws Exception {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(Config.THREADS);
        List<Future<TriItem>> results = new LinkedList<>();
        /**
         * issue the compile staff
         */
        for (String className : compileSuccessTestcases) {
            RunTask task = new RunTask(compiledSuccessMap.get(className));
            Future<TriItem> result = executor.submit(task);
            results.add(result);
        }
        executor.shutdown();
        // wait until finish
        while (!executor.isTerminated())
            ;
        // gen result
        genRunStat(results);
    }

    private void genRunStat(List<Future<TriItem>> results) throws Exception {
        for (Future<TriItem> fut : results) {
            TriItem<Boolean, String, String> runStat = fut.get();
            if (!runStat.getFirst()) {
                if (!failedBuckets.containsKey(runStat.getThird()))
                    failedBuckets.put(runStat.getThird(), new HashSet<>());
                failedBuckets.get(runStat.getThird()).add(runStat.getSecond());
                runFailedTestCases.add(runStat.getSecond());
            }
        }
    }

    /**
     * get single file code
     *
     * @param fileName
     * @return
     */
    public String getSingleFile(String fileName) {
        mutatedClass = generateClass(runnableTestList, fileName);
        String clazz = this.packageAndImport + this.mutatedClass.toString();
        return clazz;
    }

    public String testListToFile(List<CtMethod> testList, String fileName) {
        CtClass<Object> testIncludingClass = generateClass(testList, fileName);
        String clazz = this.packageAndImport + testIncludingClass.toString();
        return clazz;
    }

    public String testStringListToFile(List<String> testStringList, String fileName) {
        Factory facotry = new Launcher().getFactory();
        facotry.getEnvironment().disableConsistencyChecks(); // setSelfChecks(true);
        /**
         * Set up Class: new class name is original_MUT_MUTcount (e.g.,
         * XYSeries_ESTest_addOrUpdate_1)
         */
        CtClass<Object> clazz = facotry.Core().createClass();
        clazz.setSimpleName(fileName);
        Set<ModifierKind> modifiers = new HashSet<>();
        modifiers.add(ModifierKind.PUBLIC);
        clazz.setModifiers(modifiers);
        String emptyClassString = clazz.toString().replace("}", "\n");
        for (String testString : testStringList) {
            emptyClassString += testString + "\n\n";
        }
        String testIncludingClassString = emptyClassString + "}";

        return this.packageAndImport + testIncludingClassString;
    }

    /**
     * get bucket file code
     *
     * @param fileName
     * @return
     */
    public String getBucketFile(String fileName) {
        combineTestcases(fileName);
        if (this.mutatedClass.getMethods().size() <= 1)
            return null;
        String clazz = this.packageAndImport + this.mutatedClass.toString();
        return clazz;
    }

    /**
     * combine all testcases into one file
     *
     * @param fileName
     */
    private void combineTestcases(String fileName) {
        Set<String> targetTestcasesSet = new HashSet<>();
        // sampling
        for (Set<String> bucket : failedBuckets.values()) {
            String methodName = bucket.iterator().next();
            targetTestcasesSet.add(methodName);
        }
        // collect methods
        Set<CtMethod<?>> methods = new HashSet<>();
        for (String testcase : targetTestcasesSet) {
            methods.add(nameToMethod.get(testcase));
        }
        System.out.printf("Buckets size: %d\n", methods.size());
        /**
         * generate mutated Class
         */
        CtClass<Object> mutatedClass = generateClass(new ArrayList<>(methods), fileName);
        this.mutatedClass = mutatedClass;
    }

    public boolean compileFile(String fileName, String content) {
        List<Diagnostic<? extends JavaFileObject>> errorMessage = compileWholeClassFile(fileName, content);
        if (checkCompileError(errorMessage)) {
            return true;
        } else {
            System.out.println(errorMessage.toString());
            return false;
        }
    }

    /**
     * generate class
     *
     * @param renamed
     * @param newClassName
     * @return
     */
    private CtClass<Object> generateClass(List<CtMethod> renamed, String newClassName) {
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
        /**
         * add method to class
         */
        for (CtMethod method : renamed) {
            if (method == null) {
                continue;
            }
            // List<CtStatement> reloads = new ArrayList<>();
            // for (CtStatement stmt : method.getBody().getStatements()) {
            // CtStatement ctStatement = facotry.Core().createCodeSnippetStatement();
            // ((CtCodeSnippetStatement) ctStatement).setValue(stmt.toString());
            // reloads.add(ctStatement);
            // }
            // method.getBody().setStatements(reloads);
            clazz.addMethod(method);
        }
        return clazz;
    }

    /**
     * rename method to avoid duplicate
     *
     * @param methods
     * @return
     */
    private List<CtMethod> renameMethods(Set<CtMethod<?>> methods) {
        List<CtMethod> res = new LinkedList<>();
        int ind = 0;
        for (CtMethod method : methods) {
            CtClass clazz = (CtClass) (method.getParent());
            String[] tokens = clazz.getSimpleName().split("_");
            String methodName = "";
            for (int i = tokens.length - 3; i < tokens.length; i++) {
                methodName += tokens[i];
                if (i != tokens.length - 1)
                    methodName += "_";
            }
            // System.out.println(methodName);
            method.setSimpleName(methodName + ind);
            ind++;
            res.add(method);
        }
        return res;
    }

    /**
     * main entry of runner for compiling
     */
    public void runCompileEfficient(String fileName) throws Exception {
        // compile all the testcases
        compileAll(fileName);
    }

    public Pair<CtMethod, String> compileEach(Pair<CtClass, String> generatedTestAndStringPair) {
        CtMethod testMethod = handleTestcaseClass(generatedTestAndStringPair.getKey());
        String testMethodString = generatedTestAndStringPair.getValue();
        testMethodString = testMethodString.replace("public void test", "public void " + testMethod.getSimpleName());
        generatedTestAndStringPair.setValue(testMethodString);
        // Factory factory = new Launcher().getFactory();
        // List<CtStatement> reloads = new ArrayList<>();
        // for (CtStatement stmt : testMethod.getBody().getStatements()) {
        // CtStatement ctStatement = factory.Core().createCodeSnippetStatement();
        // ((CtCodeSnippetStatement) ctStatement).setValue(stmt.toString());
        // reloads.add(ctStatement);
        // }
        // testMethod.getBody().setStatements(reloads);
        nameToMethod.put(testMethod.getSimpleName(), testMethod);
        if (safeCompile2(generatedTestAndStringPair)) {
            // compiledTestList.add(testMethod);
            return new Pair<CtMethod, String>(testMethod, testMethodString);
        }
        return null;
    }

    private boolean safeCompile2(Pair<CtClass, String> generatedTestAndStringPair) {
        List<Diagnostic<? extends JavaFileObject>> errorMessage = new ArrayList<>();
        boolean errorFree = false;
        // CtClass rawClass = generateClass(Collections.singletonList(testCase),
        // testCase.getSimpleName());

        String rawClassWithDependency = null;
        try {
            rawClassWithDependency = this.packageAndImport + generatedTestAndStringPair.getValue();
        } catch (SpoonException e) {
            // System.err.println("WARNING: SpoonException");
            // System.out.println(rawClass);
            return false;
        }

        errorMessage = compileWholeClassFile(generatedTestAndStringPair.getKey().getSimpleName(),
                rawClassWithDependency);
        errorFree = checkCompileError(errorMessage);
        if (!errorFree) {
            // System.out.println("------------------------------------");
            // System.out.println("Test:");
            // System.out.println(generatedTestAndStringPair.getValue());
            // System.out.println();
            // System.out.println("Error message:");
            // for (Diagnostic<? extends JavaFileObject> diagnostic : errorMessage) {
            // System.out.println(diagnostic.toString());
            // }
            // System.out.println("------------------------------------");
        }
        return errorFree;
    }

    private void compileAll(String fileName) throws Exception {
        List<CtMethod> rawTestcasList = new LinkedList<>();
        for (CtClass<Object> testcase : testcases) {
            CtMethod testMethod = handleTestcaseClass(testcase);
            Factory factory = new Launcher().getFactory();
            List<CtStatement> reloads = new ArrayList<>();
            for (CtStatement stmt : testMethod.getBody().getStatements()) {
                CtStatement ctStatement = factory.Core().createCodeSnippetStatement();
                ((CtCodeSnippetStatement) ctStatement).setValue(stmt.toString());
                reloads.add(ctStatement);
            }
            testMethod.getBody().setStatements(reloads);
            nameToMethod.put(testMethod.getSimpleName(), testMethod);
            rawTestcasList.add(testMethod);
        }
        splitGeneratedTestcases(rawTestcasList);
    }

    // private void safeCompile(List<CtMethod> testcases, String fileName) {
    // List<CtMethod> rawTestcasList = new ArrayList<>();
    // rawTestcasList.addAll(testcases);
    // List<CtMethod> compiableTestcases = null;
    // List<Diagnostic<? extends JavaFileObject>> errorMessage = new ArrayList<>();
    // List<Diagnostic<? extends JavaFileObject>> previousErrorMessage = new
    // ArrayList<>();
    // int compileTime = 0;
    // boolean errorFree = false;
    // while (!errorFree) {
    // compiableTestcases = new LinkedList<>();
    // CtClass rawClass = generateClass(rawTestcasList, fileName);
    // String rawClassWithDependency = this.packageAndImport + rawClass.toString();
    // errorMessage = compileWholeClassFile(fileName, rawClassWithDependency);
    // compileTime++;
    // errorFree = checkCompileError(errorMessage);
    // CtClass<?> compiledClass = Launcher.parseClass(rawClassWithDependency);
    // List<CtMethod> methods = compiledClass.getElements(new
    // TypeFilter<>(CtMethod.class));
    // for (CtMethod method : methods) {
    // if (isMethodCompilable(method, errorMessage)) {
    // compiableTestcases.add(nameToMethod.get(method.getSimpleName()));
    // }
    // }
    //
    // rawTestcasList = new ArrayList<>(compiableTestcases);
    // if (repeatingErrorMessage(errorMessage, previousErrorMessage))
    // break;
    // previousErrorMessage = new ArrayList<>(errorMessage);
    // }
    // if (!checkCompileError(errorMessage))
    // System.out.printf("COMPILE ERROR - tried to compile %d times for %s, error
    // message : %s\n", compileTime, fileName, errorMessage);
    // this.compileCount += compiableTestcases.size();
    // this.mutatedTestcases.addAll(compiableTestcases);
    // }

    /**
     * generate split testcases
     *
     * @param compilableTestcases
     */
    public void splitGeneratedTestcases(List<CtMethod> compilableTestcases) {
        if (compilableTestcases.size() < Config.SPLIT_SIZE) {
            this.splitTestcases.add(compilableTestcases);
            return;
        }
        int pos = 0;
        while (pos + Config.SPLIT_SIZE < compilableTestcases.size()) {
            this.splitTestcases.add(compilableTestcases.subList(pos, pos + Config.SPLIT_SIZE));
            pos += Config.SPLIT_SIZE;
        }
        if (pos < compilableTestcases.size()) {
            this.splitTestcases.add(compilableTestcases.subList(pos, compilableTestcases.size()));
        }
    }

    public List<String> splitGeneratedTestCases2(List<CtMethod> compilableTestcases, String fileName) {
        List<String> splitTests = new ArrayList<>();
        if (compilableTestcases.size() < Config.SPLIT_SIZE) {
            splitTestcases.add(compilableTestcases);
        } else {
            int pos = 0;
            while (pos + Config.SPLIT_SIZE < compilableTestcases.size()) {
                splitTestcases.add(compilableTestcases.subList(pos, pos + Config.SPLIT_SIZE));
                pos += Config.SPLIT_SIZE;
            }
            if (pos < compilableTestcases.size()) {
                splitTestcases.add(compilableTestcases.subList(pos, compilableTestcases.size()));
            }
        }
        for (int i = 0; i < splitTestcases.size(); i++) {
            CtClass<Object> clazz = generateClass(splitTestcases.get(i), fileName + "_C_" + (i + 1));
            splitTests.add(clazz.toString());
        }
        return splitTests;
    }

    private boolean repeatingErrorMessage(List<Diagnostic<? extends JavaFileObject>> current,
            List<Diagnostic<? extends JavaFileObject>> previous) {
        /**
         * Assumed current and previous are never null
         */
        if (current.size() != previous.size())
            return false;
        return current.toString().equals(previous.toString());
    }

    private boolean checkCompileError(List<Diagnostic<? extends JavaFileObject>> errorMessage) {
        for (Diagnostic e : errorMessage) {
            if (e.getKind() == Diagnostic.Kind.ERROR) { // Nov. 2021: added due to ambiguity error
                return false;
            }
        }
        return true;
    }

    public void runTestcaseEfficient(Class<?> clazz) throws Exception {
        Computer computer = new ParallelComputer(false, true);
        Result result = JUnitCore.runClasses(computer, clazz);
        System.out.printf("# of %s running testcases: %d\n", clazz.getName(), result.getRunCount());
        this.runCount = result.getRunCount();
        this.failureCount += result.getFailureCount();
        System.out.printf("# of %s failing testcases: %d\n", clazz.getName(), result.getFailureCount());

        int bucketNum = 0;
        List<CtMethod> currentBucket = new LinkedList<>();
        for (Failure failure : result.getFailures()) {
            String key = "";
            String methodName = failure.toString().split("\\(")[0];
            assert methodName.length() != 0;
            // System.out.println(failure.getTrace());
            if (failure.getException() != null) {
                Throwable exception = failure.getException();
                /**
                 * judge whether need to skip
                 */
                // if (exception.getStackTrace().length >= 2) {
                // if (exception instanceof IllegalArgumentException &&
                // exception.getStackTrace()[0].getClassName().equals(Config.FULL_CLASS_NAME) &&
                // exception.getStackTrace()[1].getClassName().equals(clazz.getName())) {
                //// System.out.println(failure.getTrace());
                // System.out.println("Skip the testcase by Illegal Argument Exception @ " +
                // methodName);
                // continue;
                // }
                // }
                for (int i = 0; i < exception.getStackTrace().length; i++) {
                    if (!exception.getStackTrace()[i].getClassName().equals(clazz.getName())) {
                        key += exception.getStackTrace()[i].getClassName()
                                + exception.getStackTrace()[i].getLineNumber();
                    }
                }
            }
            if (failure.getException().getCause() != null) {
                Throwable exception = failure.getException().getCause();
                /**
                 * judge whether need to skip
                 */
                // if (exception.getStackTrace().length >= 2) {
                // if (exception instanceof IllegalArgumentException &&
                // exception.getStackTrace()[0].getClassName().equals(Config.FULL_CLASS_NAME) &&
                // exception.getStackTrace()[1].getClassName().equals(clazz.getName())) {
                //// System.out.println(failure.getTrace());
                // System.out.println("Skip the testcase by Illegal Argument Exception @ " +
                // methodName);
                // continue;
                // }
                // }
                for (int i = 0; i < exception.getStackTrace().length; i++) {
                    if (!exception.getStackTrace()[i].getClassName().equals(clazz.getName())) {
                        key += exception.getStackTrace()[i].getClassName()
                                + exception.getStackTrace()[i].getLineNumber();
                    }
                }
            }
            if (key.equals("")) {
                System.out.println("stack trace key is empty due to " + failure.getTrace() + " @ " + methodName);
                continue;
            }
            if (!failedBuckets.containsKey(key)) {
                failedBuckets.put(key, new HashSet<>());
                bucketNum++;
                currentBucket.add(nameToMethod.get(methodName));
                usedBucketTestcases.add(nameToMethod.get(methodName));
            }
            failedBuckets.get(key).add(methodName);
        }
        System.out.println(String.format("# of bucket in %s : %d", clazz.getName(), bucketNum));
        if (bucketNum > 0) {
            CtClass<Object> splitBucketClass = generateClass(currentBucket, clazz.getSimpleName() + "_B");
            String classContent = this.packageAndImport + splitBucketClass.toString();
            List<Diagnostic<? extends JavaFileObject>> errorMsg = compileWholeClassFile(clazz.getSimpleName() + "_B",
                    classContent);
            if (checkCompileError(errorMsg)) {
                writeFile(clazz.getSimpleName() + "_B", classContent);
            }
        }
        this.numOfBucket = failedBuckets.keySet().size();
    }

    private boolean isMethodCompilable(CtMethod method, List<Diagnostic<? extends JavaFileObject>> errorMessage) {
        List<CtAnnotation<? extends Annotation>> annos = method.getAnnotations();
        for (CtAnnotation a : annos) {
            CtTypeReference annoType = a.getAnnotationType();
            if (annoType.getSimpleName().equals("Override")) {
                // System.out.println("FILTERED: " + method);
                return false; // we don't handle inner method
            }
        }

        int start = method.getPosition().getLine();
        int end = method.getPosition().getEndLine();
        for (Diagnostic<? extends JavaFileObject> error : errorMessage) {
            if (start <= error.getLineNumber() && error.getLineNumber() <= end) {
                return false;
            }
        }
        return true;
    }

    private CtMethod handleTestcaseClass(CtClass<Object> testcase) {
        String methodName = testcase.getSimpleName();
        List<CtMethod> methods = testcase.getElements(new TypeFilter<>(CtMethod.class));
        CtMethod method = methods.get(0);
        method.setSimpleName(methodName);
        return method;
    }

    /**
     * Compile and create .class file
     *
     * @param fileName
     * @param content
     * @return
     */
    private List<Diagnostic<? extends JavaFileObject>> compileWholeClassFile(String fileName, String content) {
        // init source code file
        JavaFileObject file = new Source(fileName, JavaFileObject.Kind.SOURCE, content);
        // init compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // get compile information
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        // init task
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null,
                Arrays.asList(file));
        task.call();
        return diagnostics.getDiagnostics();
    }

    /**
     * compile process
     */
    private void compile() throws Exception {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(Config.THREADS);
        List<Pair<Future<Pair>, String>> results = new LinkedList<>();
        /**
         * issue the compile staff
         */
        for (String className : sourceMap.keySet()) {
            CompileTask task = new CompileTask(className, sourceMap.get(className), packageName);
            Future<Pair> result = executor.submit(task);
            results.add(new Pair<>(result, className));
        }
        executor.shutdown();
        // wait until finish
        while (!executor.isTerminated())
            ;
        // gen result
        genStat(results);
    }

    /**
     * handle the testcases after compile
     *
     * @param results
     * @throws ExecutionException
     * @throws InterruptedException
     */
    private void genStat(List<Pair<Future<Pair>, String>> results) throws ExecutionException, InterruptedException {
        for (Pair<Future<Pair>, String> res : results) {
            Pair<Boolean, Class<?>> runStat = res.getKey().get();
            String testcaseName = res.getValue();
            if (runStat.getKey()) {
                compileSuccessTestcases.add(testcaseName);
                compiledSuccessMap.put(testcaseName, runStat.getValue());
            } else {
                compileFailedTestCases.add(testcaseName);
                continue;
            }
        }
    }

    /**
     * init compile options
     */
    private void initCompileOptions() {
        // add class path
        options.add("-cp");
        options.add(Config.CLASS_PATH);
        // class output path
        options.add("-d");
        options.add(Config.BUILD_PATH + File.separator);
    }

    /**
     * Compile and create .class file
     *
     * @param fileName
     * @param content
     * @return
     */
    private boolean compileTestCase(String fileName, String content) {
        // init source code file
        JavaFileObject file = new Source(fileName, JavaFileObject.Kind.SOURCE, content);
        // init compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // get compile information
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        // init task
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null,
                Arrays.asList(file));
        task.call();
        System.out.printf("Compile Status : %s\n", diagnostics.getDiagnostics());
        return diagnostics.getDiagnostics().size() == 0 ? true : false;
    }

    public Result runCompiledTestCase(Pair<CtMethod, String> compiledTestAndStringPair) throws Exception {
        CtMethod testCase = compiledTestAndStringPair.getKey();
        String testCaseString = compiledTestAndStringPair.getValue();
        Class<?> clazz = null;
        try {
            clazz = ClassLoader.getSystemClassLoader().loadClass(packageNamePlusFileName(testCase.getSimpleName()));
        } catch (Exception e) {
            System.out.println(String.format("Load running class : %s failed.",
                    packageNamePlusFileName(testCase.getSimpleName())));
        }
        Computer computer = new ParallelComputer(false, true);
        Result result = JUnitCore.runClasses(computer, clazz);

        for (Failure failure : result.getFailures()) {
            String testName = failure.getTestHeader().split("\\(")[0];
            testNameToFailMap.put(testName, failure);
            if (failure.getException() != null
                    && failure.getException().getClass().getName().contains("TestTimedOutException")) {
                timedOutTestNameSet.add(testName);
            }
        }

        if (!timedOutTestNameSet.contains(testCase.getSimpleName())) {
            Factory factory = new Launcher().getFactory();
            CtAnnotationType testRefer = factory.createAnnotationType("Test");
            CtAnnotation<Annotation> testAnno = factory.createAnnotation();
            testAnno.setAnnotationType(testRefer.getReference());
            testAnno.addValue("timeout", 4000);
            List<CtAnnotation<? extends Annotation>> annotation = new LinkedList<>();
            annotation.add(testAnno);
            testCase.setAnnotations(annotation);
            testCaseString = testCaseString.replace("@org.junit.Test(timeout = 1000)",
                    "@org.junit.Test(timeout = 4000)");
            // System.out.println(testCaseString);

            if (Config.REGRESSION_MODE) {
                final Analyzer analyzer = new Analyzer();
                final CtClass testClass = testCase.getParent(CtClass.class);
                Map<CtMethod, List<CtLocalVariable>> localVariablesPerTestMethod = analyzer.analyze(testClass, false);
                Map<CtMethod, List<CtLocalVariable>> localVariablesPrimitive = analyzer.analyze(testClass, true);

                Launcher launcher = new Launcher();
                List<CtStatement> origStmts = new ArrayList<>();
                List<CtStatement> stmts = testCase.getBody().getStatements();

                for (CtStatement stmt : stmts) {
                    if (!stmt.clone().toString().contains("RegressionOracles.RegressionUtil.Logger.observe")) {
                        origStmts.add(stmt.clone());
                    }
                }
                testCase.getBody().setStatements(origStmts);

                List<String> oracleRemovedTestStringList = new ArrayList<>();
                int oracleCount = 0;
                for (String line : testCaseString.split("\n")) {
                    if (!line.contains("RegressionOracles.RegressionUtil.Logger.observe")) {
                        if (line.contains("// if statement for MUT null check"))
                            continue;
                        else
                            oracleRemovedTestStringList.add(line);
                    } else {
                        oracleCount += 1;
                    }
                }
                String oracleRemovedTestString = String.join("\n", oracleRemovedTestStringList);

                if (testNameToFailMap.containsKey(testCase.getSimpleName())) { // add try catch fail
                    Pair<CtMethod, String> failAddedMethodAndStringPair = TryCatchFailAdder.addTryCatchFail(testCase,
                            testNameToFailMap.get(testCase.getSimpleName()), launcher);
                    if (failAddedMethodAndStringPair != null) {
                        CtMethod failAdded = failAddedMethodAndStringPair.getKey();
                        String failAddedString = failAddedMethodAndStringPair.getValue();
                        List<String> testString = new ArrayList<>(Arrays.asList(oracleRemovedTestString.split("\n")));

                        List<String> oracleAddedString = new ArrayList<>();

                        boolean flag = false;
                        for (String originalTestString : testString) {
                            for (String line : failAddedString.split("\n")) {
                                if (originalTestString.contains(line.trim()) && !line.trim().equals("}")) {
                                    flag = true;
                                }
                            }
                            if (!flag || originalTestString.trim().equals("}")) {
                                oracleAddedString.add(originalTestString);
                            }
                        }
                        // for(String line:failAddedString.split("\n")){
                        // for(String originalTestString:testString){
                        // if(originalTestString.contains(line.trim()) && !line.trim().equals("}")){
                        // testString.remove(originalTestString);
                        // break;
                        // }
                        // }
                        // }
                        for (String line : failAddedString.split("\n")) {
                            oracleAddedString.add(oracleAddedString.size() - 2, "\t\t" + line);
                        }
                        String tryCatchAddedString = String.join("\n", oracleAddedString);

                        numOfFailingTests++;
                        runnableTestList.add(failAdded);

                        List<String> testMethodStringList = new ArrayList<>(
                                Arrays.asList(tryCatchAddedString.split("\n")));
                        List<String> outputMethodStringList = new ArrayList<>();
                        for (int i = 1; i < testMethodStringList.size() - 1; ++i) {
                            outputMethodStringList.add(testMethodStringList.get(i));
                        }
                        String outputMethodString = String.join("\n", outputMethodStringList);

                        runnableTestStringList.add(outputMethodString);
                    } else {
                        // System.out.println("WARNING: no try catch fail is added for " +
                        // testCase.getSimpleName());
                    }
                } else { // add regression assertions
                    final AssertionAdder assertionAdder = new AssertionAdder(launcher.getFactory());
                    CtMethod assertionAdded = assertionAdder.addAssertion(testCase, localVariablesPerTestMethod,
                            localVariablesPrimitive);
                    if (assertionAdded != null) {
                        List<String> testString = new ArrayList<>(Arrays.asList(oracleRemovedTestString.split("\n")));
                        List<CtStatement> oracleAddedStatements = assertionAdded.getBody().getStatements();
                        int oracleStatementCount = oracleAddedStatements.size() - testString.size() + 5;

                        for (int i = 0; i < oracleStatementCount; ++i) {
                            int index = oracleAddedStatements.size() - oracleStatementCount + i;
                            testString.add(testString.size() - 2,
                                    "\t\t" + oracleAddedStatements.get(index).toString() + ";");
                        }
                        String assertionAddedString = String.join("\n", testString);

                        numOfPassingTests++;
                        runnableTestList.add(assertionAdded);

                        List<String> testMethodStringList = new ArrayList<>(
                                Arrays.asList(assertionAddedString.split("\n")));
                        List<String> outputMethodStringList = new ArrayList<>();
                        for (int i = 1; i < testMethodStringList.size() - 1; ++i) {
                            outputMethodStringList.add(testMethodStringList.get(i));
                        }
                        String outputMethodString = String.join("\n", outputMethodStringList);

                        runnableTestStringList.add(outputMethodString);
                    } else {
                        // System.out.println("WARNING: no assertion is added for " +
                        // testCase.getSimpleName());
                    }
                }
            } else {
                runnableTestStringList.add(testCase.toString());
            }
        }
        return result;
    }

    public static void updatePoolWithNewTest(Set<MUTInput> mutInputs, MUTInput mutInput, CtMethod testCase) {
        List<CtStatement> statements = testCase.getBody().getStatements();
        int stmtIndex = statements.size() - 1;
        CtStatement lastStatement = statements.get(stmtIndex);
        CtInvocationImpl mut = null;

        if (ASTParser.isInvocation(lastStatement)) {
            List<CtInvocationImpl> invokes = lastStatement.getElements(new TypeFilter<>(CtInvocationImpl.class));
            CtInvocationImpl invoke = invokes.get(0);
            if (!invoke.getExecutable().isStatic()) {
                mut = invoke;
            }
        }

        if (mut != null) {
            if (mut.getTarget() instanceof CtVariableReadImpl) {
                CtElement varName = null;
                CtTypeReference varType = null;
                varName = ((CtVariableReadImpl) mut.getTarget()).getVariable();
                varType = ((CtVariableReadImpl) mut.getTarget()).getVariable().getType();
                Input input = new Input(varType, true, varName, new ArrayList<CtElement>(statements));
                List<Input> inputPool = mutInput.getInputs().get(0);
                inputPool.add(input);
            } else {
                // System.out.println("WARNING: update pool with new test1");
            }

            if (ASTParser.isLocalVariable(lastStatement)) {
                CtElement varName = null;
                CtTypeReference varType = null;
                CtStatement lastStatementClone = lastStatement.clone();
                List<CtLocalVariable> localVariablesClone = lastStatementClone
                        .getElements(new TypeFilter<>(CtLocalVariable.class));
                CtLocalVariable localVariableClone = localVariablesClone.get(0);
                String variableName = localVariableClone.getSimpleName();

                localVariableClone.setSimpleName(variableName + mutVariableCounter);

                mutVariableCounter++;
                varName = localVariableClone.getReference();
                varType = localVariableClone.getType();

                List<CtStatement> statementsClone = new ArrayList<>(statements);
                statementsClone.set(statementsClone.size() - 1, lastStatementClone);

                Input input = new Input(varType, true, varName, new ArrayList<CtElement>(statementsClone));

                breakPoint: for (MUTInput mutInputPool : mutInputs) {
                    int argCounter = 0;
                    for (CtTypeReference inputType : mutInputPool.getArgTypes()) {
                        if (inputType != null) {
                            if (inputType.toString().equals(varType.toString())) {
                                mutInputPool.getInputs().get(argCounter).add(input);
                                break breakPoint;
                            }
                        }
                        argCounter++;
                    }
                }
            }

        }
    }

    public void runGeneratedTestcases() throws Exception {
        initOutputFolderForClassLoader();
        for (String className : this.runningClassNames) {
            /*
             * load into jvm
             */
            Class<?> clazz = null;
            try {
                clazz = ClassLoader.getSystemClassLoader().loadClass(className);
            } catch (Exception e) {
                System.out.println(String.format("Load running class : %s failed.", className));
            }
            runTestcaseEfficient(clazz);
        }
    }

    /**
     * init output folder for loaded classes
     */
    private void initOutputFolderForClassLoader() {
        try {
            File output = new File(Config.BUILD_PATH);
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Field ucp = URLClassLoader.class.getDeclaredField("ucp");
            ucp.setAccessible(true);
            URLClassPath urlClassPath = (URLClassPath) ucp.get(urlClassLoader);
            urlClassPath.addURL(output.toURI().toURL());
        } catch (Exception e) {
            System.out.println("Loading output folder for class failed!");
        }
    }

    public String generateBucketFile(String fileName) {
        List<CtMethod> ctMethods = this.usedBucketTestcases;
        CtClass bucketClass = generateClass(ctMethods, fileName);
        String bucketClassString = this.packageAndImport + bucketClass.toString();
        compileTestCase(fileName, bucketClassString);
        return bucketClassString;
    }

    /**
     * generate source map to compile
     */
    private void generateSourceMap() {
        for (CtClass<Object> testcase : testcases) {
            sourceMap.put(testcase.getSimpleName(), this.packageAndImport + testcase.toString());
        }
    }

    private String packageNamePlusFileName(String fileName) {
        if (packageName.equals(""))
            return fileName;
        else
            return packageName + "." + fileName;
    }

    public List<CtClass<Object>> getTestcases() {
        return testcases;
    }

    public void setTestcases(List<CtClass<Object>> testcases) {
        this.testcases = testcases;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getPackageAndImport() {
        return packageAndImport;
    }

    public void setPackageAndImport(String packageAndImport) {
        this.packageAndImport = packageAndImport;
    }

    public Map<String, String> getSourceMap() {
        return sourceMap;
    }

    public void setSourceMap(Map<String, String> sourceMap) {
        this.sourceMap = sourceMap;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(List<String> options) {
        this.options = options;
    }

    public Set<String> getCompileSuccessTestcases() {
        return compileSuccessTestcases;
    }

    public void setCompileSuccessTestcases(Set<String> compileSuccessTestcases) {
        this.compileSuccessTestcases = compileSuccessTestcases;
    }

    public Set<String> getCompileFailedTestCases() {
        return compileFailedTestCases;
    }

    public void setCompileFailedTestCases(Set<String> compileFailedTestCases) {
        this.compileFailedTestCases = compileFailedTestCases;
    }

    public Set<String> getRunFailedTestCases() {
        return runFailedTestCases;
    }

    public void setRunFailedTestCases(Set<String> runFailedTestCases) {
        this.runFailedTestCases = runFailedTestCases;
    }

    public Map<String, StackTraceElement[]> getRunFailedStacktrace() {
        return runFailedStacktrace;
    }

    public void setRunFailedStacktrace(Map<String, StackTraceElement[]> runFailedStacktrace) {
        this.runFailedStacktrace = runFailedStacktrace;
    }

    public Map<String, Set<String>> getFailedBuckets() {
        return failedBuckets;
    }

    public void setFailedBuckets(Map<String, Set<String>> failedBuckets) {
        this.failedBuckets = failedBuckets;
    }

    public String getTESTCASE_NAME() {
        return TESTCASE_NAME;
    }

    public void setTESTCASE_NAME(String TESTCASE_NAME) {
        this.TESTCASE_NAME = TESTCASE_NAME;
    }

    public CtClass<Object> getMutatedClass() {
        return mutatedClass;
    }

    public void setMutatedClass(CtClass<Object> mutatedClass) {
        this.mutatedClass = mutatedClass;
    }

    public Map<String, Class<?>> getCompiledSuccessMap() {
        return compiledSuccessMap;
    }

    public void setCompiledSuccessMap(Map<String, Class<?>> compiledSuccessMap) {
        this.compiledSuccessMap = compiledSuccessMap;
    }

    public Class<?> getCompiledClass() {
        return compiledClass;
    }

    public void setCompiledClass(Class<?> compiledClass) {
        this.compiledClass = compiledClass;
    }

    public Map<String, CtMethod> getNameToMethod() {
        return nameToMethod;
    }

    public void setNameToMethod(Map<String, CtMethod> nameToMethod) {
        this.nameToMethod = nameToMethod;
    }

    public int getRunCount() {
        return runCount;
    }

    public void setRunCount(int runCount) {
        this.runCount = runCount;
    }

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(int failureCount) {
        this.failureCount = failureCount;
    }

    public int getCompileCount() {
        return compileCount;
    }

    public void setCompileCount(int compileCount) {
        this.compileCount = compileCount;
    }

    public int getNumOfBucket() {
        return numOfBucket;
    }

    public List<CtMethod> getCompiledTestList() {
        return compiledTestList;
    }

    public List<CtMethod> getRunnableTestList() {
        return runnableTestList;
    }

    public List<String> getRunnableTestStringList() {
        return runnableTestStringList;
    }

    public void setRunnableTestStringListEmpty() {
        runnableTestStringList.clear();
    }

    class CompileTask implements Callable<Pair> {
        private final String packageName;
        private String fileName;
        private String content;

        /**
         * init task
         *
         * @param fileName
         * @param content
         */
        public CompileTask(String fileName, String content, String packageName) {
            this.fileName = fileName;
            this.content = content;
            this.packageName = packageName;
        }

        public Pair<Boolean, Class<?>> call() throws Exception {
            /**
             * first argument indicates compile success or not
             * second argument contains the compiled class
             */
            Pair<Boolean, Class<?>> result = new Pair<>(false, null);
            InMemoryJavaCompiler compiler = InMemoryJavaCompiler.newInstance();
            compiler.useOptions("-cp", Config.CLASS_PATH);
            compiler.ignoreWarnings();
            Class<?> clazz = null;
            /**
             * check compile
             */
            try {
                clazz = compiler.compile(packageName + "." + fileName, content);
            } catch (Exception e) {
                return result;
            }

            // compile success
            result.setKey(true);
            result.setValue(clazz);
            return result;
        }
    }

    class RunTask implements Callable<TriItem> {
        private Class<?> clazz;

        /**
         * init task
         *
         * @param clazz
         */
        public RunTask(Class<?> clazz) {
            this.clazz = clazz;
        }

        public TriItem<Boolean, String, String> call() throws Exception {
            /**
             * 1st run status
             * 2nd class name
             * 3rd stacktrace key
             */
            TriItem<Boolean, String, String> result = new TriItem<>(false, this.clazz.getSimpleName(), null);
            Result res = JUnitCore.runClasses(clazz);
            if (res.wasSuccessful()) {
                result.setFirst(true);
            } else {
                String key = "";
                for (Failure failure : res.getFailures()) {
                    for (StackTraceElement stackTraceElement : failure.getException().getStackTrace()) {
                        if (!stackTraceElement.getClassName().contains(clazz.getName()))
                            key += stackTraceElement.getClassName() + stackTraceElement.getLineNumber();
                    }
                    if (failure.getException().getCause() != null)
                        for (StackTraceElement stackTraceElement : failure.getException().getCause().getStackTrace()) {
                            if (!stackTraceElement.getClassName().contains(clazz.getName()))
                                key += stackTraceElement.getClassName() + stackTraceElement.getLineNumber();
                        }
                }
                result.setThird(key);
            }
            return result;
        }
    }

    /**
     * wrapper class for source code
     */
    public class Source extends SimpleJavaFileObject {
        private final String content;

        public Source(String name, Kind kind, String content) {
            super(URI.create("memo:///" + name.replace('.', '/') + kind.extension), kind);
            this.content = content;
        }

        @Override
        public CharSequence getCharContent(boolean ignore) {
            return this.content;
        }
    }
}
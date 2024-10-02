import Compiler.Compiler;
import Generater.MUTMutation.ASTParser;
import Generater.MUTMutation.Input;
import Generater.MUTMutation.MUTInput;
import Generater.MUTMutation.TestCaseGenerator;
import Generater.PrimitiveMutation.PrimitiveMutateParser;
import Generater.PrimitiveMutation.PrimitiveTestCaseGenerator;
import org.junit.runner.Result;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import utils.Config;
import utils.Pair;
import utils.RunStat;
import utils.TryCatchWrapper;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.code.CtLiteral;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;

public class Main {
    public static String outputClassName;
    public static long time_budget;
    public static int num_split_output_tests;
    public static String mode = "";

    public static void main(String[] args) throws Exception {
        /*
         * return illegal parameter
         */
        if (args.length != 8) {
            System.err.println(
                    "The command should be : java Main <class path> <path to generated testcase file> <full class name> <.class output folder> <path to output file> <time_budget> <number_of_split_test_file> <thread number> <MUT|P|BOTH> <REGRESSION_MODE>");
            return;
        }
        /*
         * initial arguments
         */
        initParams(args);
        if (mode.equalsIgnoreCase("BOTH")) {
            /*
             * handle mut testcases
             */
            handleMutTestCases();
            /*
             * handle mut testcases
             */
            handlePrimitiveTestCases();
        } else if (mode.equalsIgnoreCase("seqc")) {
            /*
             * handle mut testcases
             */

            handleMutTestCases();
        } else if (mode.equalsIgnoreCase("seqp")) {
            /*
             * handle mut testcases
             */

            handlePrimitiveTestCases();
        }
    }

    private static void handleMutTestCases() throws Exception {
        System.out.println("===================================================================");
        System.out.println("handleMutTestCases : Sequence Chaining (Sequence Input Pool)");
        RunStat runStat = new RunStat(Config.FULL_CLASS_NAME);
        long generationTime = 0;
        long compileTime = 0;
        long runTime = 0;
        long collectingTime = 0;
        List<List<String>> testList = new ArrayList<>(num_split_output_tests);
        int testSplitNum = 1;
        int numOfPrimitiveInputs = 0;
        int numOfObjectInputs = 0;
        int numOfSuccess = 0;
        int numOfFail = 0;
        long startTime = System.currentTimeMillis();
        System.out.println("Starting Time : " + startTime);
        /*
         * e.g.) /base_30/60/branch/Chart/tar_extracted/1b/00/org/jfree/chart/renderer/
         * category/AbstractCategoryItemRenderer_ESTest.java
         */
        TestCaseGenerator testCaseGenerator = new TestCaseGenerator(Config.TEST_FILE, time_budget, startTime);
        // Bottle Neck
        outputClassName = testCaseGenerator.getTestClassName();
        String fileName = outputClassName + "_" + "C";
        Compiler compiler = new Compiler(testCaseGenerator.getPackageAndImport(), ASTParser.getPackageName());
        Set<MUTInput> mutInputs = testCaseGenerator.getMutInputs();
        collectingTime += System.currentTimeMillis() - startTime;
        if (collectingTime > time_budget) {
            System.out.println("Seq-C Collecting Timeout Time Budget : " + time_budget + " ms" + " Collecting Time: "
                    + collectingTime + " ms");
            System.out.println("Terminating");
        }

        int count = 0;

        breakPoint: while (true) {

            if (System.currentTimeMillis() - startTime > time_budget) {
                break;
            }
            int index = 0;
            if (count == 0) {
                testCaseGenerator.initMap(mutInputs.size());
            }
            for (MUTInput mutInput : mutInputs) {

                if (System.currentTimeMillis() - startTime > time_budget) {
                    break breakPoint;
                }

                if ((System.currentTimeMillis() - startTime) >= ((time_budget / num_split_output_tests)
                        * testSplitNum)) {
                    if (testSplitNum != num_split_output_tests) { // if after time checker pass, termination time is on
                                                                  // this code
                        List<String> splitTestList = new ArrayList<>(compiler.getRunnableTestStringList());
                        testList.add(splitTestList);
                        compiler.setRunnableTestStringListEmpty();
                        testSplitNum++;
                    }
                }

                long generationStart = System.currentTimeMillis();
                Pair<CtClass, String> generatedTestAndStringPair = testCaseGenerator.generateTest(mutInput, index);
                generationTime += System.currentTimeMillis() - generationStart;
                if (generatedTestAndStringPair == null) {
                    // System.out.println("Error: generatedTestAndStringPair is null");
                    continue;
                }

                long compileStart = System.currentTimeMillis();
                Pair<CtMethod, String> compiledTestAndStringPair = compiler.compileEach(generatedTestAndStringPair);
                compileTime += System.currentTimeMillis() - compileStart;
                if (compiledTestAndStringPair == null) {

                    deleteClassFile(generatedTestAndStringPair.getKey().getSimpleName());
                    continue;
                }

                long runStart = System.currentTimeMillis();
                Result result = compiler.runCompiledTestCase(compiledTestAndStringPair);
                runTime += System.currentTimeMillis() - runStart;

                // compiler.updatePoolWithNewTest(mutInputs, mutInput,
                // compiledTestAndStringPair.getKey());

                deleteClassFile(compiledTestAndStringPair.getKey().getSimpleName());
                index++;
            }
            count++;
        }

        testList.add(new ArrayList<>(compiler.getRunnableTestStringList()));
        // runStat.setNumOfMutatedTests(compiler.getRunnableTestStringList().size());
        // runStat.setNumOfFailedRunning(compiler.getRunFailedTestCases().size());
        // runStat.setNumOfSuccessCompile(compiler.getCompiledSuccessMap().size());
        compiler.setRunnableTestStringListEmpty();
        for (int i = 0; i < testList.size(); i++) {
            String testString = compiler.testStringListToFile(testList.get(i), fileName + "_" + (i + 1));
            // Writes Test Class to Files
            writeFile(fileName + "_" + (i + 1), testString);
            boolean errorFree = compiler.compileFile(fileName + "_" + (i + 1), testString);
            if (!errorFree) {
                System.out.println("Output class " + fileName + "_" + (i + 1) + " is not compiled.");
            }
        }
        runStat.setCollectingTime(collectingTime);
        runStat.setGenerateTime(generationTime); // Collecting Time 분리 Collection Time -> Gen Time
        runStat.setCompileTime(compileTime);
        runStat.setRunningTime(runTime);

        runStat.setType("Seq-C");
        runStat.setTestId(Config.BUILD_PATH);
        runStat.setTotalTime(System.currentTimeMillis() - startTime);

        File f = new File(Config.OUTPUT_PATH + File.separator + "dive_log");
        if (!f.exists()) {
            writeLog("dive_log", runStat.getHead() + "\n");
        }
        writeLog("dive_log", runStat.getStat() + "\n");
        System.out.println("End Time : " + System.currentTimeMillis());
    }

    private static void handlePrimitiveTestCases() throws Exception {
        System.out.println("===================================================================");
        System.out.println("handlePrimitiveTestCases : Sequence Preserving (Value Input Pool)");

        RunStat runStat = new RunStat(Config.FULL_CLASS_NAME);
        long generationTime = 0;
        long compileTime = 0;
        long runTime = 0;
        long collectingTime = 0;
        List<List<String>> testList = new ArrayList<>(num_split_output_tests);
        int testSplitNum = 1;
        int numOfPrimitiveInputs = 0;
        int numOfObjectInputs = 0;
        long startTime = System.currentTimeMillis();
        System.out.println("Starting Time : " + startTime);
        PrimitiveTestCaseGenerator testCaseGenerator = new PrimitiveTestCaseGenerator(Config.TEST_FILE, time_budget,
                startTime);
        // Bottle Neck
        Compiler compiler = new Compiler(testCaseGenerator.getPackageAndImport(),
                PrimitiveMutateParser.getPackageName());
        outputClassName = testCaseGenerator.getTestClassName();
        String fileName = outputClassName + "_" + "P";
        Set<CtMethod> testCases = PrimitiveMutateParser.getTestcases();
        collectingTime += System.currentTimeMillis() - startTime;

        if (collectingTime > time_budget) {
            System.out.println("P Collecting Timeout. Time Budget : " + time_budget + " ms" + " Collecting Time: "
                    + collectingTime + " ms");
            System.out.println("Terminating");
        }

        int count = 0;
        breakPoint: while (true) {
            if (System.currentTimeMillis() - startTime > time_budget) {
                break;
            }
            for (CtMethod testCase : testCases) {
                if (System.currentTimeMillis() - startTime > time_budget) {
                    break breakPoint;
                }

                if ((System.currentTimeMillis() - startTime) >= ((time_budget / num_split_output_tests)
                        * testSplitNum)) {
                    if (testSplitNum != num_split_output_tests) { // if after time checker pass, termination time is on
                                                                  // this code
                        List<String> splitTestList = new ArrayList<>(compiler.getRunnableTestStringList());
                        testList.add(splitTestList);
                        compiler.setRunnableTestStringListEmpty();
                        testSplitNum++;
                    }
                }
                long generationStart = System.currentTimeMillis();
                Pair<CtClass, String> mutatedTestAndStringPair = testCaseGenerator.mutateTest(testCase, count);
                generationTime += System.currentTimeMillis() - generationStart;

                if (mutatedTestAndStringPair == null) {
                    continue;
                }

                long compileStart = System.currentTimeMillis();
                Pair<CtMethod, String> compiledTestAndStringPair = compiler.compileEach(mutatedTestAndStringPair);
                compileTime += System.currentTimeMillis() - compileStart;

                if (compiledTestAndStringPair == null) {
                    deleteClassFile(mutatedTestAndStringPair.getKey().getSimpleName());
                    continue;
                }
                long runStart = System.currentTimeMillis();
                // System.out.println("Before Compilation of P");
                Result result = compiler.runCompiledTestCase(compiledTestAndStringPair);
                // System.out.println("After Compilation of P");
                runTime += System.currentTimeMillis() - runStart;
                // System.out.println("Done Running of seq-P");
                deleteClassFile(compiledTestAndStringPair.getKey().getSimpleName());
            }
            count++;
        }

        testList.add(new ArrayList<>(compiler.getRunnableTestStringList()));
        // runStat.setNumOfMutatedTests(compiler.getRunnableTestStringList().size());
        compiler.setRunnableTestStringListEmpty();
        for (int i = 0; i < testList.size(); i++) {
            String testString = compiler.testStringListToFile(testList.get(i), fileName + "_" + (i + 1));
            // e.g.) fileName == AbstractCategoryItemRenderer_ESTest_P
            writeFile(fileName + "_" + (i + 1), testString);
            // e.g.) fileName == AbstractCategoryItemRenderer_ESTest_P_4
            boolean errorFree = compiler.compileFile(fileName + "_" + (i + 1), testString);
            if (!errorFree) {
                System.out.println("Output class " + fileName + "_" + (i + 1) + " is not compiled.");
            }
        }
        runStat.setCollectingTime(collectingTime);
        runStat.setGenerateTime(generationTime);
        runStat.setCompileTime(compileTime);
        runStat.setRunningTime(runTime);
        runStat.setType("Seq-P");
        runStat.setTestId(Config.BUILD_PATH);
        runStat.setTotalTime(System.currentTimeMillis() - startTime);

        File f = new File(Config.OUTPUT_PATH + File.separator + "dive_log");
        if (!f.exists()) {
            writeLog("dive_log", runStat.getHead() + "\n");
        }
        writeLog("dive_log", runStat.getStat() + "\n");
        System.out.println("End Time : " + System.currentTimeMillis());
    }

    private static boolean tryFileExists(String outputClassName) {
        File tryFile = new File(outputClassName);
        return tryFile.exists();
    }

    private static String addTryCatchToOriginalFile(String fileName, String outputFileName) {
        String lines = "";
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines += line + "\n";
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return TryCatchWrapper.buildClass(outputFileName, lines);
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

    private static void writeLog(String fileName, String msg) throws Exception {
        // Try block to check for exceptions
        try {

            // Open given file in append mode by creating an
            // object of BufferedWriter class
            BufferedWriter out = new BufferedWriter(
                    new FileWriter(Config.OUTPUT_PATH + File.separator + fileName, true));

            // Writing on output stream
            out.write(msg);
            // Closing the connection
            out.close();
        }

        // Catch block to handle the exceptions
        catch (IOException e) {

            // Display message when exception occurs
            System.out.println("exception occoured" + e);
        }
    }

    private static void deleteClassFile(String methodName) {
        File file = new File(Config.OUTPUT_PATH + File.separator + methodName + ".class");
        if (file.exists()) {
            if (!file.delete()) {
                System.out.println("Can not delete .class file");
            }
        }
    }

    /**
     * init params in Config.java
     *
     * @param args
     */
    private static void initParams(String[] args) {
        // System.out.println("Initiating Param");
        String pathSeparator = System.getProperty("path.separator").toLowerCase();
        Config.CLASS_PATH = args[0] + pathSeparator
                + Thread.currentThread().getContextClassLoader().getResource("").getPath();
        Config.TEST_FILE = args[1];
        Config.FULL_CLASS_NAME = args[2];
        Config.BUILD_PATH = args[3];

        String fileSeparator = System.getProperty("file.separator").toLowerCase();
        int lastIndex = Config.TEST_FILE.lastIndexOf(fileSeparator);
        Config.OUTPUT_PATH = Config.TEST_FILE.substring(0, lastIndex);
        if (args[7].equals("regression")) {
            Config.REGRESSION_MODE = true;
        } else if (args[7].equals("non_regression")) {
            Config.REGRESSION_MODE = false;
        } else {
            System.out.println("Invalid Regression Mode : " + args[7]);
            System.exit(1);
        }
        time_budget = Long.parseLong(args[4]);
        num_split_output_tests = Integer.parseInt(args[5]);
        Config.THREADS = 8;
        mode = args[6];
        Config.PACKAGE = Config.FULL_CLASS_NAME.substring(0, Config.FULL_CLASS_NAME.lastIndexOf("."));
    }

}
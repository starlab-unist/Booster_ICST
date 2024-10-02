package RegressionOracles.RegressionUtil;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.visitor.filter.TypeFilter;
import utils.Config;
import utils.CtClassToStringWrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestCaseOutputWriter {
    private static int index = 1;

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
     * write testcases given classes containing only one test method
     *
     * @param classes
     */
    public static void write(List<CtClass> classes) throws Exception {
        if (classes.size() == 0)
            return;
        CtClass clazz = extractMethods(classes);
        String packageName = "package " + Config.PACKAGE + ";\n";
        CtClass wrappedT = CtClassToStringWrapper.wrapWithCodeSnippet(clazz);
        writeFile(clazz.getSimpleName(), packageName + wrappedT.toString());
        System.out.println("Write " + clazz.getSimpleName() + " finished.");
    }

    /**
     * Write testcases given class containing multiple test methods
     *
     * @param clazz
     * @throws Exception
     */
    public static void write(CtClass clazz) throws Exception {
        if (clazz.getMethods().size() == 0)
            return;
        String packageName = "package " + Config.PACKAGE + ";\n";
        CtClass wrappedT = CtClassToStringWrapper.wrapWithCodeSnippet(clazz);
        writeFile(clazz.getSimpleName(), packageName + wrappedT.toString());
        System.out.println("Write " + clazz.getSimpleName() + " finished.");
    }

    /**
     * extract testcases from wrapped class
     *
     * @param classes
     * @return
     */
    private static CtClass extractMethods(List<CtClass> classes) {
        Launcher launcher = new Launcher();
        CtClass clazz = launcher.getFactory().createClass();
        Set<ModifierKind> modifiers = new HashSet<>();
        modifiers.add(ModifierKind.PUBLIC);
        clazz.setModifiers(modifiers);
        for (CtClass ctClass : classes) {
            String testName = ctClass.getSimpleName();
            List<CtMethod<Object>> test = ctClass.getElements(new TypeFilter<>(CtMethod.class));
            assert test.size() != 0;
            test.get(0).setSimpleName(testName);
            clazz.addMethod(test.get(0));
            /**
             * dummy method to get class name
             * TODO: better idea to get class name
             */
            if (clazz.getSimpleName().length() == 0) {
                String[] tokens = testName.split("_");
                assert tokens.length > 2;
                String className = "";
                for (int i = 0; i < tokens.length - 2; i++)
                    className += tokens[i] + "_";
                clazz.setSimpleName(className + (index++));
            }
        }
        return clazz;
    }

    /**
     * Input test class contains one method per class
     * Ouput test class contains 500 methods per class
     *
     * @param tests
     * @return
     */
    public static List<CtClass<Object>> combineTests(List<CtClass<Object>> tests) {
        List<CtClass<Object>> resultTests = new ArrayList<>(); //500 methods
        List<CtClass> toSplit = new ArrayList<>();
        for (CtClass test : tests) {
            if (toSplit.size() < Config.SPLIT_SIZE) {
                toSplit.add(test);
            } else if (toSplit.size() == Config.SPLIT_SIZE) {
                CtClass clazz = extractMethods(toSplit);
                resultTests.add(clazz);
                toSplit = new ArrayList<>();
            }
        }

        if (resultTests.size() == 0) {
            CtClass clazz = extractMethods(toSplit);
            resultTests.add(clazz);
        }

        //free memory
        toSplit = new ArrayList<>();
        tests = new ArrayList<>();
        return resultTests;
    }
}

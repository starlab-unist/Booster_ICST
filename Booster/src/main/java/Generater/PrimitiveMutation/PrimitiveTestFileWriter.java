package Generater.PrimitiveMutation;

import spoon.reflect.declaration.CtClass;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class PrimitiveTestFileWriter {

    public static void main(String args[]) {
//        if (args.length != 1) {
//            System.err.println("One argument is needed for full path to testcase.java file to mutate");
//            System.exit(0);
//        }
//        String testFile = args[0];
//        PrimitiveTestCaseGenerator testCaseGenerator = new PrimitiveTestCaseGenerator(testFile);
//        testCaseGenerator.run();
//
//        String outDir = extractDirectory(testFile);
//        String packageAndImport = testCaseGenerator.getPackageAndImport();
//        List<CtClass<Object>> generatedTests = testCaseGenerator.getGeneratedTests();
////        System.out.println(outFilePath);
//        int count = 0;
//        for (CtClass<Object> test : generatedTests) {
////            System.out.println(test);
//            String className = test.getSimpleName();
//            String outFile = outDir + File.separator + className + ".java";
//            writeTestFile(outFile, packageAndImport, test);
//            count++;
//        }
//        System.out.println("Number of generated tests for : " + testFile + ": " + count);


    }

    private static void writeTestFile(String filePath, String packageAndImport, CtClass<Object> test) {
//        System.out.println(filePath);
        FileWriter fw;
        try {
            fw = new FileWriter(new File(filePath));
            fw.write(packageAndImport);
            fw.write(System.lineSeparator()); //newline

            fw.write(test.toString());
            fw.write(System.lineSeparator()); //newline

            fw.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private static String extractDirectory(String filePath) {
        return filePath.substring(0, filePath.lastIndexOf(File.separator));
    }
}

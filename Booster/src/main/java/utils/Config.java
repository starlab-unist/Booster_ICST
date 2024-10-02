package utils;

public class Config {
    /*package name*/
    public static String PACKAGE = "";
    /* split testcases size*/
    public static int SPLIT_SIZE = 500;
    /*this can be class path dependency needed*/
    public static String CLASS_PATH = "";
    /*.java output path*/
    public static String OUTPUT_PATH = "";
    /*.class output path*/
    public static String BUILD_PATH = "";
    /*thread number*/
    public static int THREADS = 5;
    /**
     * path to testcase file
     */
    public static String TEST_FILE = "";
    /**
     * full class name org.class.xxx
     */
    public static String FULL_CLASS_NAME = "";

    /**
     * Timeout for mutation generation in seconds
     */
    public static long MUTATION_GENERATE_TIMEOUT = 300;
    /**
     * Timeout for compile in seconds
     */
    public static long COMPILE_TIMEOUT = 300;

    /**
     * For primitive mutation, MAX number of mutations per test
     */
    public static int PRIMITIVE_TEST_NUM = 50;
    /**
     * For MUT mutation, MAX number of mutations per MUT
     */
    public static int MUT_TEST_NUM = 100;
    /**
     * timeout for testcase running
     */
    public static long RUN_TIMEOUT = 3000;

    /**
     * In regression mode, we generate regression oracles
     */
    public static boolean REGRESSION_MODE = false;

    /**
     * In regression mode, we need path to where the source code is stored
     */
    public static String SRC_PATH = "";

    public static String STRING_IDENTIFIER = "String___";
}

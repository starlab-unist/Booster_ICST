package RegressionOracles;

import org.junit.experimental.ParallelComputer;
import org.junit.runner.*;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunNotifier;
import spoon.Launcher;
import spoon.SpoonException;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;
import sun.misc.URLClassPath;
import utils.Config;
import utils.CtClassToStringWrapper;

import javax.tools.*;
import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URLClassLoader;
import java.util.*;

public class RegressionCompileUtil {
    private static RegressionCompileUtil instance = null;
    private List<String> options = new ArrayList<String>();


    /**
     * singleton method
     *
     * @return
     */
    public static RegressionCompileUtil getInstance() {
        if (instance == null)
            instance = new RegressionCompileUtil();
        return instance;
    }

    /**
     * empty constructor
     */
    private RegressionCompileUtil() {
        initCompileOptions();
//        initLoadedClassesFolder(); //removing does not show any differences
    }

    /**
     * Compile given class and create .class file
     * While compiling, remove those methods that cause compile errors
     *
     * @param fileName
     * @param ctClass
     */
    public CtClass compileAndRemoveCompileErrors(String fileName, CtClass ctClass) {
        Map<String, CtMethod> nameToMethod = new HashMap<>(); //to save fully qualified signatures in class
        Set<CtMethod> ctMethods = ctClass.getMethods();
        if (ctMethods.size() == 0)
            return null;
        for (CtMethod ctMethod : ctMethods) {
            nameToMethod.put(ctMethod.getSimpleName(), ctMethod);
        }

        Set<CtMethod> ctMethodsList = new HashSet<>();
        Set<CtMethod> compiledTests = new HashSet<>();
        ctMethodsList.addAll(ctMethods);
        CtClass compiledClass = null;
        List<Diagnostic<? extends JavaFileObject>> errorMessage = new ArrayList<>();
        List<Diagnostic<? extends JavaFileObject>> previousErrorMessage = new ArrayList<>();
        int compileTime = 1;
        boolean errorFree = false;
        String packageName = "package " + Config.PACKAGE + ";\n";
        while (!errorFree) {
            compiledTests = new HashSet<>();
            CtClass rawClass = CtClassToStringWrapper.wrapWithCodeSnippet(ctMethodsList, fileName);
            String content = packageName + rawClass.toString();
            errorMessage = compileWholeClassFile(fileName, content);
            errorFree = isCompileSucceed(errorMessage);
            if (errorFree) {
                return CtClassToStringWrapper.createCtClass(ctMethodsList, fileName);
            }
            compiledClass = parseClass(content);
            List<CtMethod> methods = compiledClass.getElements(new TypeFilter<>(CtMethod.class));
            for (CtMethod method : methods) {
                if (isMethodCompilable(method, errorMessage)) {
//                    System.out.println("Removing " + method.getSimpleName() + " due to compile error");
//                    System.out.println(method.getBody());
                    compiledTests.add(nameToMethod.get(method.getSimpleName()));
                }
            }
            ctMethodsList = new HashSet<>(compiledTests);
            System.out.printf("Try compile %s time : %d, error message : %s\n", compiledClass.getSimpleName(), compileTime++, errorMessage);
            if (repeatingErrorMessage(errorMessage, previousErrorMessage))
                break;
            previousErrorMessage = new ArrayList<>(errorMessage);
        }
        return null;
    }

    private CtClass parseClass(String clazz) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(new VirtualFile(clazz));
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setAutoImports(true);
        Collection<CtType<?>> allTypes = launcher.buildModel().getAllTypes();
        try {
            return (CtClass) allTypes.stream().findFirst().get(); //instead of using Launcher.parseClass to avoid crash due to ESTest_scaffolding.java
        } catch (ClassCastException var4) {
            throw new SpoonException("parseClass only considers classes (and not interfaces and enums). Please consider using a Launcher object for more advanced usage.");
        }
    }


    /**
     * Compile and create .class file
     *
     * @param className
     * @param content
     * @return
     */

    public boolean runCompile(String className, String content) {
        List<Diagnostic<? extends JavaFileObject>> diag = compileWholeClassFile(className, content);
        boolean compileResult = isCompileSucceed(diag);
        return compileResult;
    }


    private boolean repeatingErrorMessage(List<Diagnostic<? extends JavaFileObject>> current, List<Diagnostic<? extends JavaFileObject>> previous) {
        /**
         * Assumed current and previous are never null
         */
        if (current.size() != previous.size())
            return false;
        return current.toString().equals(previous.toString());
    }

    private boolean isMethodCompilable(CtMethod method, List<Diagnostic<? extends JavaFileObject>> errorMessage) {
        List<CtAnnotation<? extends Annotation>> annos = method.getAnnotations();
        for (CtAnnotation a : annos) {
            CtTypeReference annoType = a.getAnnotationType();
            if (annoType.getSimpleName().equals("Override")) {
//                System.out.println("FILTERED: " + method);
                return false; //we don't handle inner method
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

    private List<Diagnostic<? extends JavaFileObject>> compileWholeClassFile(String className, String content) {
        // init source code file
        JavaFileObject file = new RegressionCompileUtil.Source(className, JavaFileObject.Kind.SOURCE, content);
        // init compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // get compile information
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        // init task
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null, Arrays.asList(file));
        task.call();
        return diagnostics.getDiagnostics();
    }

    private Class loadTestcase(String className) {
        /**
         * load into jvm
         */
        Class<?> clazz = null;
        try {
            clazz = ClassLoader.getSystemClassLoader().loadClass(Config.PACKAGE + "." + className);
        } catch (Exception e) {
            System.out.println(String.format("Load running class : %s failed.", className));
        }
        return clazz;
    }

    public Result runCompiledTestCase(String className) throws Exception {
        Class clazz = loadTestcase(className);
        if (clazz != null) {
            Computer computer = new ParallelComputer(false, true);
            Result result = JUnitCore.runClasses(clazz);
            return result;
        }
        return null;
    }

    public List<Failure> runCompiledTestCaseWithListener(String className) throws Exception {
        Class clazz = loadTestcase(className);
        Request request = Request.classes(clazz);
        Runner runner = request.getRunner();
        RunNotifier fNotifier = new RunNotifier();
        final TestListener listener = new TestListener();
        fNotifier.addFirstListener(listener);
        fNotifier.fireTestRunStarted(runner.getDescription());
        runner.run(fNotifier);
        return listener.getTestFails();
    }

    /**
     * init output folder for loaded classes
     */
    private void initLoadedClassesFolder() {
        List<String> folders = new ArrayList<>();
        folders.addAll(Arrays.asList(Config.CLASS_PATH.split(String.valueOf(File.pathSeparatorChar))));
        folders.add(Config.OUTPUT_PATH);
        for (String folder : folders) {
            loadFolder(folder);
        }
    }

    private void loadFolder(String folderPath) {
        try {
            File output = new File(folderPath);
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Field ucp = URLClassLoader.class.getDeclaredField("ucp");
            ucp.setAccessible(true);
            URLClassPath urlClassPath = (URLClassPath) ucp.get(urlClassLoader);
            urlClassPath.addURL(output.toURI().toURL());
        } catch (Exception e) {
            System.out.println("Loading folder : " + folderPath + " failed!");
        }
    }

    /**
     * check whether compile succeed or not
     *
     * @param diag
     * @return
     */
    private boolean isCompileSucceed(List<Diagnostic<? extends JavaFileObject>> diag) {
        for (Diagnostic<? extends JavaFileObject> stat : diag) {
            if (stat.getKind().equals(Diagnostic.Kind.ERROR))
                return false;
        }
        return true;
    }


    /**
     * init compile options
     */
    private void initCompileOptions() {
        //add class path
        options.add("-cp");
        options.add(Config.CLASS_PATH);
        //class output path
        options.add("-d");
        options.add(Config.BUILD_PATH + File.separator);
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

package RegressionOracles;

import dk.brics.automaton.RegExp;
import org.evosuite.PackageInfo;
import org.junit.runner.notification.Failure;
import spoon.Launcher;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import utils.Config;
import utils.Pair;

import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public class TryCatchFailAdder {
    private static StackTraceElement retrieveCrashPoint(StackTraceElement[] stackTrace) {
        /**
         * Get crash point in the test
         */
        int crashPointIndex = -1;
        StackTraceElement result = null;
        for (int i = 0; i < stackTrace.length; i++) {
            if (i > 0 && stackTrace[i].getClassName().contains("ESTest")) {
//                 &&   stackTrace[crashPointIndex].getClassName().startsWith(Config.FULL_CLASS_NAME)) {
                return stackTrace[crashPointIndex];
            } else {
                if (stackTrace[i].getClassName().startsWith(Config.FULL_CLASS_NAME)) {
                    result = stackTrace[i]; //keep whatever the last stack frame on CUT. For stackoverflow exception, stack trace is too deep
                }
            }
            crashPointIndex = i;
        }
        return result;
    }


    private static boolean crashPointMatch(CtStatement stmt, StackTraceElement crashPoint) {
        CtStatement clone = stmt.clone();
        if (clone.toString().contains(crashPoint.getMethodName() + "("))
            return true;
        String className = crashPoint.getClassName().replace("$", ".");
        if (clone.toString().contains(className) &&
                (clone.toString().contains("new " + className) && crashPoint.getMethodName().equals("<init>"))) //sometimes
            return true;
        return false;
    }

    public static Pair<CtMethod, String> addTryCatchFail(CtMethod testMethod, Failure failure, Launcher launcher) {
        StackTraceElement crashPoint = null; //stack frame of crash method, more precisely the method directly called in the test
        String errorType = null; //error type such as java.lang.StackOverflowError
        StackTraceElement topFrame = null; //top stack frame

        Throwable exception = failure.getException();
        if (exception != null) {
            StackTraceElement crashPointFailure = retrieveCrashPoint(exception.getStackTrace());
            if (crashPointFailure != null && !crashPointFailure.getClassName().contains("ESTest")) {
                crashPoint = crashPointFailure;
                StackTraceElement topFrameFailure = exception.getStackTrace()[0];
                if (topFrameFailure != null) {
                    topFrame = topFrameFailure;
                    String errorTypeFailure = null;
                    if (exception.toString().contains(":")) { //java.lang.IllegalStateException: getKey() can only be called after next() and before remove()
                        errorTypeFailure = exception.toString().split(":")[0];
                    } else { //java.lang.StackOverflowError
                        errorTypeFailure = exception.toString();
                    }
                    if (errorTypeFailure != null) {
                        errorType = errorTypeFailure.trim();
                        if (errorType.contains("$")) { //in case it is project specific exception of a inner class, it can't catch precisely. So, let's make it general to RuntimeException.
//                            errorType = errorType.split("\\$")[0];
                            errorType = "java.lang.RuntimeException";
                        }
                    } else
                        return null;
                } else {
                    return null;
                }
            } else {
                System.out.println(exception);
                System.out.println("Stack size is " + exception.getStackTrace().length);
                for(StackTraceElement st: exception.getStackTrace()){
                    System.out.println(st);
                }
                return null;
            }
        } else {
            return null;
        }
        final CtClass testClass = testMethod.getParent(CtClass.class);
        testClass.removeMethod(testMethod);
        CtMethod ctMethod = testMethod.clone();
        String tryCatchString = "";

        List<CtStatement> ctMethodStmts = ctMethod.getBody().getStatements();
        Factory factory = launcher.getFactory();
        List<CtStatement> newStmts = new ArrayList<>();
        boolean noDuplMethod = noDuplMethod(ctMethodStmts, crashPoint);

        for (CtStatement stmt : ctMethodStmts) {
            boolean foundMatch = crashPointMatch(stmt, crashPoint);
            if (foundMatch //it may cause some imprecision when there exists multiple statements invoke the same method, but this is the only way for now
                    || errorType.endsWith("Error")) { //when stack trace is too deep such as due to stack overflow error
                List<CtStatement> tryStmts = new ArrayList<>();
                CtBlock body = factory.Core().createBlock();
                if (foundMatch && errorType.endsWith("Exception") && noDuplMethod) {
                    for (CtStatement newStmt : newStmts) {
                        body.addStatement(newStmt.clone());
                    }
                    tryStmts.add(stmt);  //flat3Map_EntrySetIterator0_240.setValue(object0_1)
                } else {
                    tryStmts.addAll(ctMethodStmts); //if error type is not exception such as StackOverFlow, crashpoint is imprecise
                }

                String snippet = "org.junit.Assert.fail(\"Expecting exception: " + errorType + "\")";
                CtCodeSnippetStatement snippetStatement = factory.Code().createCodeSnippetStatement(snippet);
                tryStmts.add(snippetStatement); //fail("Expecting exception")

                CtBlock ctBlock = factory.Core().createBlock();
                for (CtStatement tryStmt : tryStmts) {
                    ctBlock.addStatement(tryStmt.clone());
                }

                CtTry tryBlock = factory.Core().createTry();
                tryBlock.setBody(ctBlock);
                CtCatch catcher = factory.Core().createCatch();
                CtCatchVariable<? extends Throwable> catchVaribale = factory.Core().createCatchVariable();
                catcher.setParameter(catchVaribale);

                List<CtCatch> catches = new ArrayList<>();
                catches.add(catcher);
                CtCatchVariable<? extends Throwable> throwable = factory.Core().createCatchVariable();
                throwable.setSimpleName(errorType + " e");
                catcher.setParameter(throwable);
                String sourceClass = topFrame.getClassName();
                if (foundMatch && isValidSource(sourceClass) && errorType.endsWith("Exception") && !errorType.equals("TooManyResourcesException")) //only when it's RuntimeException because otherwise, the failure can be flaky
                    snippet = "org.evosuite.runtime.EvoAssertions.verifyException(\"" + sourceClass + "\", e)";
                else
                    snippet = "//Do not verify because it is not a RuntimeException or source class \"" + sourceClass + "\" is not valid";
                catcher.setBody(factory.Code().createCodeSnippetStatement(snippet));
                tryBlock.setCatchers(catches);

                tryCatchString += tryBlock.toString();

                body.addStatement(tryBlock);
                ctMethod.setBody(body);
                break;
            } else {
                newStmts.add(stmt);
            }
        }
        testClass.addMethod(ctMethod);
        if(tryCatchString.equals("")){
            return null;
        }
        return new Pair<CtMethod, String>(ctMethod,tryCatchString);
    }

    /**
     * Checks if there exists multiple methodName calls existing in stmts
     *
     * @param stmts
     * @param crashPoint
     * @return
     */
    private static boolean noDuplMethod(List<CtStatement> stmts, StackTraceElement crashPoint) {
        int occurrence = 0;
        for (CtStatement stmt : stmts) {
            if (crashPointMatch(stmt, crashPoint))
                occurrence++;
        }
        return occurrence <= 1;
    }

    /**
     * Same strategy used in Evosuite's TestCodeVisitor.java
     *
     * @param sourceClass
     * @return
     */
    private static boolean isValidSource(String sourceClass) {
        return (!sourceClass.startsWith(PackageInfo.getEvoSuitePackage() + ".") ||
                sourceClass.startsWith(PackageInfo.getEvoSuitePackage() + ".runtime.")) &&
                !sourceClass.equals(URLClassLoader.class.getName()) && // Classloaders may differ, e.g. when running with ant
                !sourceClass.startsWith(RegExp.class.getPackage().getName()) &&
                !sourceClass.startsWith("java.lang.System") &&
                !sourceClass.startsWith("java.lang.String") &&
                !sourceClass.startsWith("java.lang.Class") &&
                !sourceClass.startsWith("sun.") &&
                !sourceClass.startsWith("com.sun.") &&
                !sourceClass.startsWith("jdk.internal.") &&
                !sourceClass.startsWith("<evosuite>");
    }

}

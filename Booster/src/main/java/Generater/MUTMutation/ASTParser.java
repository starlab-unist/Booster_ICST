package Generater.MUTMutation;

import org.evosuite.shaded.org.hibernate.dialect.Sybase11Dialect;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;
import utils.Config;
import utils.Pair;

import java.util.*;

public class ASTParser {
    /**
     * restore the method to sequence map
     */
    private static Map<CtAbstractInvocation, List<CtElement>> methodToSequence = new HashMap<>();
    private static Set<String> visitedMUTs = new HashSet<>();
    private static String packageName;

    public static String getPackageName() {
        return packageName;
    }

    public static Set<String> getVisitedMUTs() {
        return visitedMUTs;
    }

    /**
     * handle method invocations and constructors
     *
     * @param method
     */
    private static void processMethodInvokeAndConstructor(CtMethod method) throws Exception {
        List<CtStatement> statements = method.getBody().getStatements();
        if (statements.size() == 0) {
            return;
        }
        int stmtIndex = statements.size() - 1;
        CtStatement lastStatement = statements.get(stmtIndex);
        CtAbstractInvocation mut = null;
        boolean lastMethodInvoke = false;
        while (!lastMethodInvoke && stmtIndex >= 0) {
            if (isInvocation(lastStatement)) {
                // System.out.println("Invocation: " + lastStatement);
                List<CtInvocationImpl> invoke = lastStatement.getElements(new TypeFilter<>(CtInvocationImpl.class));
                mut = invoke.get(0);
                lastMethodInvoke = true;
            } else if (isConstructor(lastStatement)) {
                // System.out.println("Constructor: " + lastStatement);
                List<CtConstructorCall> construct = lastStatement
                        .getElements(new TypeFilter<>(CtConstructorCall.class));
                mut = construct.get(0);
                lastMethodInvoke = true;
            } else if (isTryBlock(lastStatement)) {
                List<CtStatement> tryStmts = ((CtTryImpl) lastStatement).getBody().getStatements();
                CtStatement lastTryStmt = tryStmts.get(tryStmts.size() - 1);
                if (isInvocation(lastTryStmt)) {
                    List<CtInvocationImpl> invoke = lastTryStmt.getElements(new TypeFilter<>(CtInvocationImpl.class));
                    mut = invoke.get(0);
                } else if (isConstructor(lastTryStmt)) {
                    List<CtConstructorCall> construct = lastTryStmt
                            .getElements(new TypeFilter<>(CtConstructorCall.class));
                    mut = construct.get(0);
                } else {
                    System.err.println("WARN: No invocation or constructor call in last block of "
                            + method.getSimpleName() + ": " + lastStatement);
                }
                lastMethodInvoke = true;
            } else {
                System.err.println("WARN: Last statement of " + method.getSimpleName()
                        + " is neither invocation nor constructor: " + lastStatement);
                if (--stmtIndex >= 0)
                    lastStatement = statements.get(stmtIndex);
            }
        }
        if (mut != null) {
            if (mut.getArguments().size() != 0)
                processArguments(mut, statements);

            List<CtElement> results = new ArrayList<>();
            for (CtStatement state : statements) {
                if (state.getPosition().getLine() < lastStatement.getPosition().getLine()) {
                    results.add(state);
                }
            }
            CandidatePool.insertMethodToParamsPool(mut, 0, results);
            processMUT(mut);
        }

    }

    /**
     * handle argument processing
     *
     * @param finalStmt
     * @param statements
     * @throws Exception
     */
    private static void processArguments(CtAbstractInvocation finalStmt, List<CtStatement> statements)
            throws Exception {
        List<CtExpression> args = finalStmt.getArguments();
        int pos = 0;
        for (CtExpression arg : args) {
            /*
             * handle like var1
             */
            List<CtElement> results = null;
            if (arg instanceof CtVariableReadImpl) {
                // System.out.println("CtVariableReadImpl: " + arg.toString());
                CtVariableReadImpl variableRead = (CtVariableReadImpl) arg;
                CtVariableReference variable = variableRead.getVariable();

                if (variable.getDeclaration() == null) {
                    results = new LinkedList<>();
                } else {
                    results = getStatements(variable.getDeclaration().getDefaultExpression(), statements);
                }
            } else if (arg instanceof CtUnaryOperatorImpl) {
                // System.out.println("CtUnaryOperatorImpl: " + arg.toString());
                results = new LinkedList<>();
            } else if (arg instanceof CtLiteralImpl) {
                // System.out.println("CtLiteralImpl: " + arg.toString());
                results = new LinkedList<>();
            } else if (arg instanceof CtFieldReadImpl) {
                // System.out.println("CtFieldReadImpl: " + arg.toString());
                CtFieldReadImpl fieldRead = (CtFieldReadImpl) arg;
                results = getStatements((CtExpression) fieldRead.getTarget(), statements);
                // System.out.println(results.toString());
            } else if (arg instanceof CtArrayReadImpl) {
                // System.out.println("CtArrayReadImpl: " + arg.toString());
                CtArrayReadImpl arrayRead = (CtArrayReadImpl) arg;
                results = getStatements(arrayRead.getTarget(), statements);
            } else if (arg instanceof CtBinaryOperatorImpl) {
                // System.out.println("CtBinaryOperatorImpl: " + arg.toString());
                continue; // ignore (boolean1_425 == boolean0_428) appeared in assertion statements
            } else {
                throw new Exception("unknown argument type: " + arg);
            }
            results.add(arg);
            // System.out.println(arg.toString());
            CandidatePool.insertMethodToParamsPool(finalStmt, ++pos, results);
        }
    }

    public static boolean isInvocation(CtStatement stmt) {
        return stmt.getElements(new TypeFilter<>(CtInvocationImpl.class)).size() == 1;
    }

    public static boolean isLocalVariable(CtStatement stmt) {
        return stmt.getElements(new TypeFilter<>(CtLocalVariable.class)).size() == 1;
    }

    public static boolean isConstructor(CtStatement stmt) {
        return stmt.getElements(new TypeFilter<>(CtConstructorCallImpl.class)).size() == 1;
    }

    private static boolean isTryBlock(CtStatement stmt) {
        return stmt.getElements(new TypeFilter<>(CtTryImpl.class)).size() == 1;
    }

    /**
     * get statement list by line number
     *
     * @param arg
     * @param statements
     * @return
     */
    private static List<CtElement> getStatements(CtElement arg, List<CtStatement> statements) {
        List<CtElement> backSlicing = new LinkedList<>();
        for (CtStatement statement : statements) {
            if (arg.getPosition().getLine() == statement.getPosition().getLine()
                    || arg.getPosition().getLine() > statement.getPosition().getLine())
                backSlicing.add((CtElement) statement);
        }
        return backSlicing;
    }

    /**
     * link the method name and arguments type
     * also vartype varname arguments
     *
     * @param mut
     * @throws Exception
     */
    private static void processMUT(CtAbstractInvocation mut) throws Exception {
        List<CtTypeReference> types = new ArrayList<>();
        String methodName = mut.getExecutable().getSimpleName();
        CtElement varName = null;
        CtTypeReference varType = null;
        List<CtElement> args = mut.getArguments();
        /*
         * process receiver object
         * if MUT is static invocation or constructor call,
         * varName and varType are set to null
         */
        if (mut instanceof CtInvocationImpl) {
            CtInvocationImpl invoke = (CtInvocationImpl) mut;
            if (invoke.getTarget() instanceof CtVariableReadImpl) {
                varName = ((CtVariableReadImpl) invoke.getTarget()).getVariable();
                varType = ((CtVariableReadImpl) invoke.getTarget()).getVariable().getType();
            } else if (invoke.getTarget() instanceof CtTypeAccessImpl) { // static invocation
                varName = null;
                varType = null;
            } else {
                return;
                // throw new Exception("unkonwn variable reference during ASTParser.processMUT
                // for Invocation: " + mut.getExecutable().getSignature());
            }
        } else if (mut instanceof CtConstructorCallImpl) {
            CtConstructorCallImpl invoke = (CtConstructorCallImpl) mut;
            varName = null;
            varType = null;
        }

        List<CtElement> stats = methodToSequence.get(mut);
        assert stats != null;
        // assert varName != null;
        assert args != null;
        assert methodName != null;
        // assert varType != null;
        /*
         * process parameter args
         */
        int pos = 0;
        for (CtElement arg : args) {
            CtTypeReference type = null;
            CtTypeReference castType = null;
            List<CtTypeReference> casts = ((CtExpression) arg).getTypeCasts();
            type = ((CtExpression) arg).getType();
            /*
             * get the add the cast type
             */
            if (casts.size() != 0)
                castType = casts.get(0);

            if (type == null && castType == null)
                return;
            assert type != null || castType != null;

            if (type != null) {
                if (castType == null) {
                    if (type.isPrimitive() || type.toString().equals("java.lang.String")) {
                        if (arg.getElements(new TypeFilter<>(CtFieldRead.class)).size() == 1) {
                            CandidatePool.insertVarTypeToInputPool(new Pair<CtTypeReference, CtElement>(type, arg),
                                    stats.subList(0, stats.size() - 1));
                        } else {
                            CandidatePool.insertDirectToValues(type, arg);
                        }
                    } else {
                        if (arg.getElements(new TypeFilter<>(CtFieldRead.class)).size() == 1) {
                            CandidatePool.insertVarTypeToInputPool(new Pair<CtTypeReference, CtElement>(type, arg),
                                    stats.subList(0, stats.size() - 1));
                        } else {
                            // handled by local variable
                        }
                    }
                } else {
                    if (castType.isPrimitive() || castType.toString().equals("java.lang.String")) {
                        if (arg.getElements(new TypeFilter<>(CtFieldRead.class)).size() == 1) { // (double) object.zero
                            CandidatePool.insertVarTypeToInputPool(new Pair<CtTypeReference, CtElement>(castType, arg),
                                    stats.subList(0, stats.size() - 1));
                        } else {
                            if (arg.getElements(new TypeFilter<>(CtVariableReference.class)).size() == 0) { // (type)
                                                                                                            // null
                                CandidatePool.insertDirectToValues(castType, arg);
                            } else if (arg.getElements(new TypeFilter<>(CtVariableReference.class)).size() == 1) { // we
                                                                                                                   // handle
                                                                                                                   // typecasted
                                                                                                                   // variable
                                                                                                                   // reference
                                                                                                                   // as
                                                                                                                   // a
                                                                                                                   // param
                                                                                                                   // such
                                                                                                                   // as
                                                                                                                   // numericEntityUnescaper0_10.translate(((java.lang.CharSequence)
                                                                                                                   // (charBuffer0_3)));
                                CandidatePool.insertVarTypeToInputPool(
                                        new Pair<CtTypeReference, CtElement>(castType, arg),
                                        stats.subList(0, stats.size() - 1));
                            } else {
                                System.out.println("VariableReference is more than 2 in primitive: " + arg.toString());
                            }
                        }
                    } else {
                        if (arg.getElements(new TypeFilter<>(CtFieldRead.class)).size() == 1) {
                            CandidatePool.insertVarTypeToInputPool(new Pair<CtTypeReference, CtElement>(castType, arg),
                                    stats.subList(0, stats.size() - 1));
                        } else {
                            if (arg.getElements(new TypeFilter<>(CtVariableReference.class)).size() == 0) { // (type)
                                                                                                            // null
                                CandidatePool.insertDirectToValues(castType, arg);
                            } else if (arg.getElements(new TypeFilter<>(CtVariableReference.class)).size() == 1) { // we
                                                                                                                   // handle
                                                                                                                   // typecasted
                                                                                                                   // variable
                                                                                                                   // reference
                                                                                                                   // as
                                                                                                                   // a
                                                                                                                   // param
                                                                                                                   // such
                                                                                                                   // as
                                                                                                                   // numericEntityUnescaper0_10.translate(((java.lang.CharSequence)
                                                                                                                   // (charBuffer0_3)));
                                CandidatePool.insertVarTypeToInputPool(
                                        new Pair<CtTypeReference, CtElement>(castType, arg),
                                        stats.subList(0, stats.size() - 1));
                            } else {
                                System.out.println("VariableReference is more than 2: " + arg.toString());
                            }
                        }
                    }
                }
            } else {
                System.out.println("Argument's type is null: " + arg.toString());
            }

            /*
             * add type
             */
            if (castType != null) {
                types.add(castType);
            } else {
                types.add(type);
            }

        }
        /*
         * add type of receiver object
         */
        types.add(0, varType);
        /*
         * Fill data structures
         */
        if (!(varName == null && varType == null)) //
            CandidatePool.insertVarTypeToInputPool(new Pair<CtTypeReference, CtElement>(varType, varName), stats);
        String mutSig = varType + "." + mut.getExecutable().getSignature();
        if (!visitedMUTs.contains(mutSig)) {
            CandidatePool.insertMUTnameToArgtypes(mut, types);
            // System.out.println(mut.toString());
            visitedMUTs.add(mutSig);
        }
        // else
        // System.out.println("VISITED: " + mutSig);
    }

    /**
     * add type to variable map
     *
     * @param testcase
     * @throws Exception
     */
    private static void processVartypeTovarnames(CtMethod testcase) throws Exception {
        List<CtLocalVariable> vars = testcase.getBody().getElements(new TypeFilter<>(CtLocalVariable.class));
        for (CtLocalVariable var : vars) {
            CtTypeReference type = var.getType();
            assert type != null;
            // CandidatePool.insertVartypeTovarnames(type, var);
            CtElement varName = var.getReference();
            List<CtElement> stats = getStatements(var, testcase.getBody().getStatements());
            assert stats != null;
            // if (type.isPrimitive()){
            // System.out.println(testcase.toString());
            // for(CtElement stat: stats){
            // System.out.println(stat.toString());
            // }
            // System.out.println("----------------------------------------");
            // }
            CandidatePool.insertVarTypeToInputPool(new Pair(type, varName), stats);
        }
    }

    /**
     * main process to initial the pool
     *
     * @param testcases
     */
    private static void process(Set<CtMethod> testcases, long time_budget, long startTime) throws Exception {
        for (CtMethod ctMethod : testcases) {

            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            
            // 남은 시간이 time_budget을 초과하는지 확인
            if (elapsedTime > time_budget) {
                System.out.println("Seq-C Collecting Timeout Time Budget : "+time_budget +" ms" +" Collecting Time: " + elapsedTime + " ms");
                System.out.println("Terminating");
                System.exit(1);
            }
            // if (Config.REGRESSION_MODE)
            // removeTryCatchAndAssertion(ctMethod);
            // System.out.println(ctMethod.toString());
            removeTryCatchAndAssertion(ctMethod);

            // System.out.println(ctMethod.toString());
            processStatement(ctMethod);
            // System.out.println(ctMethod.toString());
            // processMethodInvoke(ctMethod);
            processMethodInvokeAndConstructor(ctMethod);
            // System.out.println(ctMethod.toString());
            processVartypeTovarnames(ctMethod);
            // System.out.println(ctMethod.toString());
        }
    }

    /**
     * remove try catch block and assertion part for regression mode
     *
     * @param testcase
     */
    private static void removeTryCatchAndAssertion(CtMethod testcase) {
        List<CtStatement> stmts = new ArrayList<>();
        List<CtStatement> cloned = new ArrayList<>();
        for (CtStatement statement : testcase.getBody().getStatements()) {
            if (statement instanceof CtTry) {
                List<CtStatement> tryStmts = ((CtTry) statement).getBody().getStatements();
                for (CtStatement tryStmt : tryStmts) {
                    if (tryStmt.toString().contains("Assert") && tryStmt.toString().contains("fail")) {
                        continue;
                    } else {
                        stmts.add(tryStmt);
                    }
                }
            } else if (statement.toString().contains("Assert") && statement.toString().contains("assert")) {
                continue;
            } else {
                stmts.add(statement);
            }
        }

        for (CtStatement stmt : stmts) {
            cloned.add(stmt.clone());
        }
        testcase.getBody().setStatements(cloned);
    }

    /**
     * filter the test cases method
     *
     * @param ctModel
     * @return
     */
    private static Set<CtMethod> getTestCases(CtModel ctModel) {
        Set<CtMethod> testcases = new HashSet<CtMethod>();
        List<CtMethod> allMethods = ctModel.getElements(new TypeFilter<>(CtMethod.class));
        for (CtMethod method : allMethods) {
            if (method.getSimpleName().startsWith("test") && method.getAnnotations().size() > 0) {
                testcases.add(method);
            }
        }
        return testcases;
    }

    /**
     * handle to generate statement
     *
     * @param testcase
     */
    private static void processStatement(CtMethod testcase) {
        List<CtAbstractInvocation> invokes = new ArrayList<>();
        invokes.addAll(testcase.getBody().getElements(new TypeFilter<>(CtInvocationImpl.class)));
        invokes.addAll(testcase.getBody().getElements(new TypeFilter<>(CtConstructorCallImpl.class)));

        List<CtStatement> statements = testcase.getBody().getStatements();
        for (CtAbstractInvocation invoke : invokes) {
            List<CtElement> results = new LinkedList<>();
            for (CtStatement statement : statements) {
                try {
                    if (statement.getPosition().getLine() <= invoke.getPosition().getLine()) // ||
                                                                                             // (statement.getPosition().getLine()
                                                                                             // ==
                                                                                             // invoke.getPosition().getLine()))
                        results.add(statement);
                } catch (UnsupportedOperationException e) {
                    // System.out.println(statement);
                    continue;
                }
            }
            methodToSequence.put(invoke, results);
            // if(invoke.toString().contains("addAnnotation")){
            // System.out.println(invoke.toString());
            // for(CtElement result:results){
            // System.out.println(result.toString());
            // }
            // System.out.println("------------------------");
            // }

        }
    }

    /**
     * init the spoon
     *
     * @param path
     * @return
     */
    private static CtModel init(String path) {
        Launcher launcher = new Launcher();
        launcher.addInputResource(path);
        launcher.buildModel();
        return launcher.getModel();
    }

    private static void renameVariable(CtModel testClass) {
        List<CtMethod> methods = testClass.getElements(new TypeFilter<>(CtMethod.class));
        int index = 0;
        Set<CtVariable> usedDefs = new HashSet<>();
        for (CtMethod method : methods) {
            List<CtVariableReference> vars = method.getElements(new TypeFilter<>(CtVariableReference.class));
            Map<CtVariable, List<CtVariableReference>> defUsePair = new HashMap<>();
            for (CtVariableReference var : vars) {
                // System.out.println(var.toString());
                CtVariable def = var.getDeclaration();
                if (def == null) {
                    continue;
                }
                // System.out.println(def.toString());
                usedDefs.add(def);
                if (!defUsePair.containsKey(def))
                    defUsePair.put(def, new LinkedList<>());
                defUsePair.get(def).add(var);
            }
            for (CtVariable def : defUsePair.keySet()) {
                List<CtVariableReference> refs = defUsePair.get(def);
                String varName = def.getSimpleName() + "_" + index;
                index++;
                for (CtVariableReference ref : refs) {
                    if (!ref.getModifiers().contains(ModifierKind.STATIC))
                        ref.setSimpleName(varName);
                }
                def.setSimpleName(varName);
            }
        }
        for (CtVariable var : testClass.getElements(new TypeFilter<>(CtVariable.class))) {
            if (var.getReference().getSimpleName().contains("_"))
                continue;
            var.setSimpleName(var.getSimpleName() + "_" + index);
            index++;
        }
    }

    /**
     * main entry of the ast parser
     *
     * @param path
     */
    public static void parser(String path, long time_budget, long startTime) throws Exception {
        CtModel ctModel = init(path);
        renameVariable(ctModel);
        getPackage(ctModel);
        // System.out.println(ctModel.getAllTypes());
        CandidatePool.setTestFile(ctModel);
        Set<CtMethod> testcases = getTestCases(ctModel);
        CandidatePool.setTestcases(testcases);

        process(testcases,time_budget,startTime);
        // PrimitiveMutateParser.run(ctModel);
    }

    /**
     * get package name
     *
     * @param clazz
     */
    private static void getPackage(CtModel clazz) {
        // for (CtPackage ctPackage : clazz.getAllPackages()) {
        // System.out.println(ctPackage.toString());
        // }
        // packageName = clazz.getAllPackages().toArray()[clazz.getAllPackages().size()
        // - 1].toString();
        List<String> className = Arrays.asList(Config.FULL_CLASS_NAME.split("\\."));
        packageName = String.join(".", className.subList(0, className.size() - 1));
        // System.out.println("Package Name: " + packageName);
    }
}

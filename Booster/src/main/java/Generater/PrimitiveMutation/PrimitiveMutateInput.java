package Generater.PrimitiveMutation;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.support.reflect.code.*;
import spoon.support.reflect.reference.CtExecutableReferenceImpl;

import java.util.*;

/**
 * this is used for the pr
 */
public class PrimitiveMutateInput {
    /**
     * this map contains the statement -> mutate statements map
     */
    private static Map<CtStatement, List<CtStatement>> inputCombinations = new HashMap<>();
    /**
     * this is left for assignment like boolean b1=new XXX(1,2);
     */
    private static Map<CtStatement, Map<Integer, List<CtStatement>>> inputAssignmentCombinations = new HashMap<>();

    private static Map<CtMethod, List<CtStatement>> testToStmts = new HashMap<>();

    public static Map<String, Collection<String>> getStmtToEvosuiteInput() {
        return stmtToEvosuiteInput;
    }

    private static Map<String, Collection<String>> stmtToEvosuiteInput = new HashMap<>(); //<stmt_identifier (e.g., String_DirectAssign, methodSig_argPosition), inputs>

    public static Map<CtStatement, List<CtStatement>> getInputCombinations() {
        return inputCombinations;
    }

    public static Map<CtMethod, List<CtStatement>> getTestToStmts() {
        return testToStmts;
    }

    public static Map<CtStatement, Map<Integer, List<CtStatement>>> getInputAssignmentCombinations() {
        return inputAssignmentCombinations;
    }

    /**
     * @param original
     * @param mutated
     * @return
     */
    private static boolean insertStatement(CtStatement original, CtStatement mutated) {
        if (!inputCombinations.containsKey(original))
            inputCombinations.put(original, new LinkedList<>());
        return inputCombinations.get(original).add(mutated);
    }

    private static int count = 0;

    /**
     * this can be assigned by the method invocation and constructor
     *
     * @param original
     * @param pos
     * @param mutated
     * @return
     */
    private static boolean insertStatement(CtStatement original, int pos, CtStatement mutated) {
        if (!inputAssignmentCombinations.containsKey(original))
            inputAssignmentCombinations.put(original, new HashMap<>());
        if (!inputAssignmentCombinations.get(original).containsKey(pos))
            inputAssignmentCombinations.get(original).put(pos, new LinkedList<>());
        return inputAssignmentCombinations.get(original).get(pos).add(mutated);
    }

    /**
     * process each testcase statements
     *
     * @param testcase
     */
    private static void processTestcase(CtMethod testcase,long time_budget, long startTime) {
//        System.out.println(testcase.getSimpleName());
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - startTime;
            // 남은 시간이 time_budget을 초과하는지 확인
        if (elapsedTime > time_budget) {
            System.out.println("Seq-P Collecting Timeout Time Budget : "+time_budget +" ms" +" Collecting Time: " + elapsedTime + " ms");
            System.out.println("Terminating");
            System.exit(1);
        }

        List<CtStatement> stmts = testcase.getBody().getStatements();
        testToStmts.put(testcase, stmts);
        for (CtStatement stmt : stmts
        ) {
//            System.out.println(stmt);
            processStatement(stmt);
        }
    }


    /**
     * mutate each statement
     *
     * @param stmt
     */
    private static void processStatement(CtStatement stmt) {
        if (stmt instanceof CtLocalVariableImpl) {
            /**
             * this handle like boolen b1 = false;
             */
            CtLocalVariableImpl localVar = (CtLocalVariableImpl) stmt;
            mutateLocalVariableAssignment(localVar);
            CtExpression assign = localVar.getAssignment();
            boolean isInvoke = assign instanceof CtInvocation;
            if (assign instanceof CtInvocationImpl) {
                mutateAssignmentInvoke(stmt, (CtInvocationImpl) assign);
            } else if (assign instanceof CtConstructorCallImpl) {
                mutateConstructorCall(stmt, (CtConstructorCallImpl) assign);
            } else if ((assign instanceof CtFieldReadImpl) && !isInvoke) {
//                mutateDirectAssign(stmt, localVar);
            } else if (assign instanceof CtLiteralImpl) {
                mutateDirectAssign(stmt, localVar);
            } else if (assign instanceof CtNewArrayImpl) {
                /**
                 * ignore new array impl like int[] arr =new int[3];
                 */
                //mutateDirectAssign(stmt, localVar);
            } else if (assign instanceof CtUnaryOperatorImpl) {
                mutateDirectAssign(stmt, localVar);
            } else if (assign instanceof CtVariableReadImpl) {
                // Do nothing
            } else {
                throw new RuntimeException("No match assignment expression");
            }
        } else if (stmt instanceof CtInvocationImpl) {
            mutateInvoke(stmt, (CtInvocationImpl) stmt);
        } else if (stmt instanceof CtAssignmentImpl) {
            CtExpression lhs = ((CtAssignmentImpl) stmt).getAssigned();
            CtExpression rhs = ((CtAssignmentImpl) stmt).getAssignment();
            if (!(lhs instanceof CtFieldWriteImpl) &&
                    !(lhs instanceof CtArrayWriteImpl && (rhs instanceof CtVariableReadImpl || rhs instanceof CtArrayReadImpl))) {//stringArray0[3] = "U?L@a&}7~xy" is handled in Parser
                mutateStatementAssign(stmt, (CtAssignmentImpl) stmt);
            }
        } else if (stmt instanceof CtTryImpl) {
            CtTryImpl tryCatch = (CtTryImpl) stmt;
            for (CtStatement statement : tryCatch.getBody().getStatements())
                processStatement(statement);
        } else if (stmt instanceof CtLoop || stmt instanceof CtReturn || stmt instanceof CtIfImpl) {
            return;
        } else if (stmt instanceof CtComment) {
            //Do nothing
        } else {
            throw new RuntimeException("No match statement type: " + stmt);
        }
    }

    private static boolean isPrimitive(CtTypeReference type) {
        return (type.isPrimitive() || type.getQualifiedName().equals("java.lang.String"));
    }

    /**
     * mutate like Obj obj= j.fe;
     *
     * @param stmt
     * @param assign
     */
    private static void mutateStatementAssign(CtStatement stmt, CtAssignmentImpl assign) {
        if (assign.getType() == null || !assign.getType().isPrimitive())
            return;
        String typeName = PrimitiveMutateParser.getPrimitiveReferenceToName().get(assign.getType());
        if (typeName == null)
            return;
        if (isPrimitive(assign.getType())) {
            String evosuiteInputKey = "ArrayIndexDirectAssign";
            String evosuiteInputValue = evosuiteInputKey;

            evosuiteInputKey = createEvosuiteInputIdentifier(evosuiteInputKey, typeName);
            evosuiteInputValue = createEvosuiteInputIdentifier(evosuiteInputValue, assign.getAssignment().clone().toString());
            insertEvosuiteInput(evosuiteInputKey, evosuiteInputValue);
        }
        Set<CtElement> values = PrimitiveMutateParser.getPrimitiveTypeAndVal().get(typeName);
        for (CtElement value : values) {
            CtAssignmentImpl varAssign = (CtAssignmentImpl) ((CtAssignmentImpl) stmt).clone();
            varAssign.setAssignment(wrapperExpression(value));
            insertStatement(stmt, varAssign);
        }
    }

    /**
     * mutate original assignment
     *
     * @param stmt
     * @param methodInovke
     */
    private static void mutateInvoke(CtStatement stmt, CtInvocationImpl methodInovke) {
        List<CtElement> args = methodInovke.getArguments();
        Map<Integer, List<CtElement>> argsCandidate = generateArgMutateMap(args);
        int pos = -1;
        String evosuiteInputKey = methodInovke.getExecutable().getSignature();
        String evosuiteInputValue = evosuiteInputKey;
        for (CtElement arg : args) {
            /**
             * move the argument position
             */
            pos++;
            CtTypeReference type = null;
            CtTypeReference castType = null;
            List<CtTypeReference> casts = ((CtExpression) arg).getTypeCasts();
            type = ((CtExpression) arg).getType();
            /**
             * get the add the cast type
             */
            if (casts.size() != 0)
                castType = casts.get(0);
            /**
             * get current type
             */
            CtTypeReference currentType = castType == null ? type : castType;
            /**
             * skip no candidate inside the map
             */
            if (!PrimitiveMutateParser.getPrimitiveReferenceToName().containsKey(currentType))
                continue;

            if (isPrimitive(currentType)) {
                evosuiteInputKey = createEvosuiteInputIdentifier(evosuiteInputKey, Integer.toString(pos));
                evosuiteInputValue = createEvosuiteInputIdentifier(evosuiteInputValue, arg.clone().toString());
            }
            String typeName = PrimitiveMutateParser.getPrimitiveReferenceToName().get(currentType);
            Set<CtElement> values = PrimitiveMutateParser.getPrimitiveTypeAndVal().get(typeName);
            /**
             * add original value to the pool
             */
            values = new HashSet<>(values);
            values.add(arg);
            /**
             * add mutate value
             */
            for (CtElement value : values) {
                /**
                 * add mutate value for
                 */
                List<CtElement> originalPrevArgs = generateRandomArgs(argsCandidate, 0, pos - 1);
                List<CtElement> originalNextArgs = generateRandomArgs(argsCandidate, pos + 1, args.size() - 1);
                List<CtElement> mutateArgs = new ArrayList<>();
                mutateArgs.addAll(originalPrevArgs);
                mutateArgs.add(wrapperExpression(value));
                mutateArgs.addAll(originalNextArgs);
                CtInvocationImpl mutateInvoke = (CtInvocationImpl) methodInovke.clone();
                mutateInvoke.setArguments(mutateArgs);
                insertStatement(stmt, pos, mutateInvoke);
            }
        }
        insertEvosuiteInput(evosuiteInputKey, evosuiteInputValue);
    }

    /**
     * mutate like Obj obj= j.fe;
     *
     * @param stmt
     * @param localVar
     */
    private static void mutateDirectAssign(CtStatement stmt, CtLocalVariableImpl localVar) {
        String typeName = PrimitiveMutateParser.getPrimitiveReferenceToName().get(localVar.getType());

        if (isPrimitive(localVar.getType())) {
            String evosuiteInputKey = "DirectAssign";
            String evosuiteInputValue = evosuiteInputKey;

            evosuiteInputKey = createEvosuiteInputIdentifier(evosuiteInputKey, typeName);
            evosuiteInputValue = createEvosuiteInputIdentifier(evosuiteInputValue, localVar.getAssignment().clone().toString());
            insertEvosuiteInput(evosuiteInputKey, evosuiteInputValue);
        }

        Set<CtElement> values = PrimitiveMutateParser.getPrimitiveTypeAndVal().get(typeName);
        /**
         * return for null value
         */
        if (values == null)
            return;
        for (CtElement value : values) {
            CtLocalVariable varAssign = ((CtLocalVariableImpl) stmt).clone();
            varAssign.setAssignment(wrapperExpression(value));
            insertStatement(stmt, varAssign);
        }
    }

    /**
     * mutate constructor call
     *
     * @param stmt
     * @param call
     */
    private static void mutateConstructorCall(CtStatement stmt, CtConstructorCallImpl call) {
        List<CtElement> args = call.getArguments();
        Map<Integer, List<CtElement>> argsCandidate = generateArgMutateMap(args);
        int pos = -1;
        String evosuiteInputKey = call.getExecutable().getSignature();
        String evosuiteInputValue = evosuiteInputKey;
        for (CtElement arg : args) {
            /**
             * move the argument position
             */
            pos++;
            CtTypeReference type = null;
            CtTypeReference castType = null;
            List<CtTypeReference> casts = ((CtExpression) arg).getTypeCasts();
            type = ((CtExpression) arg).getType();
            /**
             * get the add the cast type
             */
            if (casts.size() != 0)
                castType = casts.get(0);
            /**
             * get current type
             */
            CtTypeReference currentType = castType == null ? type : castType;
            /**
             * skip no candidate inside the map
             */
            if (!PrimitiveMutateParser.getPrimitiveReferenceToName().containsKey(currentType))
                continue;

            if (isPrimitive(currentType)) {
                evosuiteInputKey = createEvosuiteInputIdentifier(evosuiteInputKey, Integer.toString(pos));
                evosuiteInputValue = createEvosuiteInputIdentifier(evosuiteInputValue, arg.clone().toString());
            }

            String typeName = PrimitiveMutateParser.getPrimitiveReferenceToName().get(currentType);
            Set<CtElement> values = PrimitiveMutateParser.getPrimitiveTypeAndVal().get(typeName);
            /**
             * add original value to the pool
             */
            values = new HashSet<>(values);
            values.add(arg);
            /**
             * add mutate value
             */
            for (CtElement value : values) {
                /**
                 * add mutate value for
                 */
                List<CtElement> originalPrevArgs = generateRandomArgs(argsCandidate, 0, pos - 1);
                List<CtElement> originalNextArgs = generateRandomArgs(argsCandidate, pos + 1, args.size() - 1);
                CtStatement mutatedStatament = stmt.clone();
                CtLocalVariableImpl varAssign = (CtLocalVariableImpl) mutatedStatament;
                List<CtElement> mutateArgs = new ArrayList<>();
                mutateArgs.addAll(originalPrevArgs);
                mutateArgs.add(wrapperExpression(value));
                mutateArgs.addAll(originalNextArgs);
                CtConstructorCallImpl mutateConstructor = (CtConstructorCallImpl) call.clone();
                mutateConstructor.setArguments(mutateArgs);
                varAssign.setAssignment(mutateConstructor);
                insertStatement(stmt, pos, varAssign);
            }
        }
        insertEvosuiteInput(evosuiteInputKey, evosuiteInputValue);
    }

    /**
     * generate arguments map
     *
     * @param args
     * @return
     */
    private static Map<Integer, List<CtElement>> generateArgMutateMap(List<CtElement> args) {
        Map<Integer, List<CtElement>> res = new HashMap<>();
        int pos = -1;
        for (CtElement arg : args) {
            pos++;
            CtTypeReference type = null;
            CtTypeReference castType = null;
            List<CtTypeReference> casts = ((CtExpression) arg).getTypeCasts();
            type = ((CtExpression) arg).getType();
            /**
             * get the add the cast type
             */
            if (casts.size() != 0)
                castType = casts.get(0);
            /**
             * get current type
             */
            CtTypeReference currentType = castType == null ? type : castType;
            /**
             * skip no candidate inside the map
             */
            if (!PrimitiveMutateParser.getPrimitiveReferenceToName().containsKey(currentType)) {
                List<CtElement> tmpList = new ArrayList<>();
                tmpList.add(arg);
                res.put(pos, tmpList);
                continue;
            }
            String typeName = PrimitiveMutateParser.getPrimitiveReferenceToName().get(currentType);
            Set<CtElement> values = PrimitiveMutateParser.getPrimitiveTypeAndVal().get(typeName);
            res.put(pos, new ArrayList<>(values));
        }
        return res;
    }

    /**
     * get random args
     *
     * @param argsCandidate
     * @param begin
     * @param end
     * @return
     */
    private static List<CtElement> generateRandomArgs(Map<Integer, List<CtElement>> argsCandidate, int begin, int end) {
        List<CtElement> res = new ArrayList<>();
        for (int i = begin; i <= end; i++) {
            List<CtElement> candi = argsCandidate.get(i);
            Collections.shuffle(candi);
            res.add(wrapperExpression(candi.get(0)));
        }
        return res;
    }


    private static void insertEvosuiteInput(String key, String value) {
        if (key.contains("_") && value.contains("_")) {
            if (!stmtToEvosuiteInput.containsKey(key)) {
                stmtToEvosuiteInput.put(key, new HashSet<>());
            }
            stmtToEvosuiteInput.get(key).add(value);
        }
    }

    private static String createEvosuiteInputIdentifier(String curr, String next) {
        return curr + "_" + next;
    }

    /**
     * mutate original assignment
     *
     * @param stmt
     * @param assign
     */
    private static void mutateAssignmentInvoke(CtStatement stmt, CtInvocationImpl assign) {
        List<CtElement> args = assign.getArguments();
        Map<Integer, List<CtElement>> argsCandidate = generateArgMutateMap(args);
        int pos = -1;
        String evosuiteInputKey = assign.getExecutable().getSignature();
        String evosuiteValue = assign.getExecutable().getSignature();

        for (CtElement arg : args) {

            /**
             * move the argument position
             */
            pos++;
            CtTypeReference type = null;
            CtTypeReference castType = null;
            List<CtTypeReference> casts = ((CtExpression) arg).getTypeCasts();
            type = ((CtExpression) arg).getType();
            /**
             * get the add the cast type
             */
            if (casts.size() != 0)
                castType = casts.get(0);
            /**
             * handle field read obj.f1
             */
            if (type == null && castType == null) {
                casts = ((CtExpression) arg.getParent()).getTypeCasts();
                type = ((CtExpression) arg.getParent()).getType();
                if (casts.size() != 0)
                    castType = casts.get(0);
            }
            /**
             * get current type
             */
            CtTypeReference currentType = castType == null ? type : castType;
            /**
             * skip no candidate inside the map
             */
            if (!PrimitiveMutateParser.getPrimitiveReferenceToName().containsKey(currentType))
                continue;

            if (isPrimitive(currentType)) {
                evosuiteInputKey = createEvosuiteInputIdentifier(evosuiteInputKey, Integer.toString(pos));
                evosuiteValue = createEvosuiteInputIdentifier(evosuiteValue, arg.clone().toString());
            }

            String typeName = PrimitiveMutateParser.getPrimitiveReferenceToName().get(currentType);
            Set<CtElement> values = PrimitiveMutateParser.getPrimitiveTypeAndVal().get(typeName);
            /**
             * add original value to the pool
             */
            values = new HashSet<>(values);
            values.add(arg);
            /**
             * add mutate value
             */
            for (CtElement value : values) {
                /**
                 * add mutate value for
                 */
                List<CtElement> originalPrevArgs = generateRandomArgs(argsCandidate, 0, pos - 1);
                List<CtElement> originalNextArgs = generateRandomArgs(argsCandidate, pos + 1, args.size() - 1);
                CtStatement mutatedStatament = stmt.clone();
                CtLocalVariableImpl varAssign = (CtLocalVariableImpl) mutatedStatament;
                List<CtElement> mutateArgs = new ArrayList<>();
                mutateArgs.addAll(originalPrevArgs);
                mutateArgs.add(wrapperExpression(value));
                mutateArgs.addAll(originalNextArgs);
                CtInvocationImpl mutateInvoke = (CtInvocationImpl) assign.clone();
                mutateInvoke.setArguments(mutateArgs);
                varAssign.setAssignment(mutateInvoke);
                insertStatement(stmt, pos, varAssign);
            }
        }
        insertEvosuiteInput(evosuiteInputKey, evosuiteValue);
    }

    /**
     * mutate statement assignment
     *
     * @param stmt
     */
    private static void mutateLocalVariableAssignment(CtStatement stmt) {
        CtLocalVariableImpl localVar = (CtLocalVariableImpl) stmt;
        CtTypeReference varType = localVar.getReference().getType();
        if (localVar.getAssignment() instanceof CtNewArray)
            return;
        if (localVar.getAssignment() instanceof CtInvocation)
            return;
        if (!PrimitiveMutateParser.getPrimitiveReferenceToName().containsKey(varType))
            return;
        String type = PrimitiveMutateParser.getPrimitiveReferenceToName().get(varType);

        if (isPrimitive(varType)) {
            String evosuiteInputKey = "DefaultAssign";
            String evosuiteInputValue = evosuiteInputKey;

            evosuiteInputKey = createEvosuiteInputIdentifier(evosuiteInputKey, type);
            evosuiteInputValue = createEvosuiteInputIdentifier(evosuiteInputValue, localVar.getAssignment().clone().toString());
            insertEvosuiteInput(evosuiteInputKey, evosuiteInputValue);
        }
        Set<CtElement> mutants = PrimitiveMutateParser.getPrimitiveTypeAndVal().get(type);
//        System.out.println("OOOOOO");
//        System.out.println(stmt);
//        System.out.println("OOOOOO");
        for (CtElement mutant : mutants
        ) {
            if (mutant.toString().contains("null"))
                continue;
            CtStatement clonedStmt = stmt.clone();
            CtLocalVariableImpl clonedVar = (CtLocalVariableImpl) clonedStmt;
            CtExpression expr = wrapperExpression(mutant);
            ((CtLocalVariableImpl) clonedStmt).setAssignment(expr);
//            System.out.println(clonedStmt);
            insertStatement(stmt, clonedStmt);
        }
        /**
         * add original one
         */
        insertStatement(stmt, stmt);
//        System.out.println("tttttt");
//        System.out.println(stmt);
    }

    /**
     * wrapper the mutant into ctexpression
     *
     * @param mutant
     * @return
     */
    private static CtExpression wrapperExpression(CtElement mutant) {
        Factory factory = new CtExecutableReferenceImpl<>().getFactory();
        CtLiteral<Object> literal = factory.createLiteral();
        literal.setValue(mutant);
        return literal;
    }


    /**
     * main entry of primitive mutate input to generate the mutate statements
     */
    public static void run(long time_budget, long startTime) {
        Set<CtMethod> testcases = PrimitiveMutateParser.getTestcases();
        /**
         * process testcase
         */
        for (CtMethod testcase : testcases) {
            processTestcase(testcase,time_budget,startTime);
        }
    }
}

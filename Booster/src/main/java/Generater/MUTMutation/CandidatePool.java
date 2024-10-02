package Generater.MUTMutation;

import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtConstructorCallImpl;
import utils.Pair;

import java.util.*;

public class CandidatePool {

    /**
     * this pool is for local variable like org.jfree.data.time.TimeSeries timeSeries0 = new org.jfree.data.time.TimeSeries(quarter1);
     * note that this might cause problem when wrong sequence order
     * do not use it easily
     */
    private static HashMap<CtTypeReference, Set<CtExpression>> definitionPool = new HashMap();
    /**
     * this pool is used to generate the method to arguments map
     * Key is either CtInvocationImpl or CtConstructorImpl
     */
    private static HashMap<CtAbstractInvocation, HashMap<Integer, List<CtElement>>> methodToParamsPool = new HashMap<CtAbstractInvocation, HashMap<Integer, List<CtElement>>>();

    /**
     * Key: variable type such as XYSeries
     * Value: set of variable names such as xYSeries0
     */
    private static HashMap<CtTypeReference, Set<CtLocalVariable>> vartypeTovarnames = new HashMap<>();

    /**
     * Key: type of direct values
     * Value: direct values
     */
    private static HashMap<CtTypeReference, Set<CtElement>> directToValues = new HashMap<>();

    /**
     * To access vartypeToInputPool easily
     * Key: CtTypeReference
     * Value: Set of pairs
     */
    private static HashMap<CtTypeReference, Set<Pair<CtTypeReference, CtElement>>> varTypeNamePool = new HashMap<>();

    /**
     * method of each test case
     */
    private static Set<CtMethod> testcases = new HashSet<>();

    /**
     * Key: Pair of (variable type, variable name)
     * Value: Set of argument inputs
     */
    private static HashMap<Pair<CtTypeReference, CtElement>, Set<List<CtElement>>> vartypeToInputPool = new HashMap<>();

    /**
     * Key is either CtInvocationImpl or CtConstructorImpl
     * Value: list of arguments where index 0 is receiver object followed by parameter arguments afterwards
     */
    private static HashMap<CtAbstractInvocation, Set<List<CtTypeReference>>> MUTnameToArgtypes = new HashMap<>();
    /**
     * store the whole original test file
     */
    private static CtModel testFile;

    /**
     * insert element to the definition pool
     *
     * @param type
     * @param assignment
     */
    public static void insertDefinitionPool(CtTypeReference type, CtExpression assignment) {
        if (!definitionPool.containsKey(type))
            definitionPool.put(type, new HashSet<CtExpression>());
        definitionPool.get(type).add(assignment);
    }

    /**
     * insert method with position of argument and argument
     *
     * @param method
     * @param pos
     * @param statements
     */
    public static void insertMethodToParamsPool(CtAbstractInvocation method, int pos, List<CtElement> statements) {
        if (!methodToParamsPool.containsKey(method))
            methodToParamsPool.put(method, new HashMap<Integer, List<CtElement>>());
        HashMap<Integer, List<CtElement>> posToArgs = methodToParamsPool.get(method);
        posToArgs.put(pos, statements);
    }

    public static HashMap<CtTypeReference, Set<CtExpression>> getDefinitionPool() {
        return definitionPool;
    }

    public static HashMap<CtAbstractInvocation, HashMap<Integer, List<CtElement>>> getMethodToParamsPool() {
        return methodToParamsPool;
    }


    public static HashMap<CtTypeReference, Set<CtLocalVariable>> getVartypeTovarnames() {
        return vartypeTovarnames;
    }

    public static HashMap<CtTypeReference, Set<Pair<CtTypeReference, CtElement>>> getVarTypeNamePool() {
        return varTypeNamePool;
    }

    public static HashMap<Pair<CtTypeReference, CtElement>, Set<List<CtElement>>> getVartypeToInputPool() {
        return vartypeToInputPool;
    }


    public static HashMap<CtAbstractInvocation, Set<List<CtTypeReference>>> getMUTnameToArgtypes() {
        return MUTnameToArgtypes;
    }

    public static boolean insertVartypeTovarnames(CtTypeReference type, CtLocalVariable var) {
        if (!vartypeTovarnames.containsKey(type))
            vartypeTovarnames.put(type, new HashSet<>());
        return vartypeTovarnames.get(type).add(var);
    }

    public static boolean insertVarTypeToInputPool(Pair<CtTypeReference, CtElement> typeAndName, List<CtElement> stmts) {
        insertVarTypeNamePair(typeAndName);
        if (!vartypeToInputPool.containsKey(typeAndName))
            vartypeToInputPool.put(typeAndName, new HashSet<>());
        for (CtElement e : stmts) {
            List<CtConstructorCallImpl> cons = e.getElements(new TypeFilter<>(CtConstructorCallImpl.class));
            if (cons.size() == 1) {
                CtConstructorCallImpl call = cons.get(0);
                CtExpression receiverObj = call.getTarget();
                if (receiverObj != null) { //To handle strBuilder0_111.new org.apache.commons.lang.text.StrBuilderWriter(), this statement causes compile error
                    String simpleName = call.getType().getSimpleName();
                    Factory factory = call.getFactory();
                    CtTypeReference<Object> newType = factory.createTypeReference();
                    newType.setPackage(null);
                    newType.setSimpleName(simpleName);
                    call.setType(newType); //set org.apache.commons.lang.text.StrBuilderWriter() to StrBuilderWriter()
                }
            }
        }
        return vartypeToInputPool.get(typeAndName).add(stmts);
    }

    private static boolean insertVarTypeNamePair(Pair<CtTypeReference, CtElement> typeAndName) {
        if (!varTypeNamePool.containsKey(typeAndName.getKey()))
            varTypeNamePool.put(typeAndName.getKey(), new HashSet<>());
        return varTypeNamePool.get(typeAndName.getKey()).add(typeAndName);
    }

    public static boolean insertMUTnameToArgtypes(CtAbstractInvocation method, List<CtTypeReference> types) {
        if (!MUTnameToArgtypes.containsKey(method))
            MUTnameToArgtypes.put(method, new HashSet<>());
        return MUTnameToArgtypes.get(method).add(types);
    }

    public static HashMap<CtTypeReference, Set<CtElement>> getDirectToValues() {
        return directToValues;
    }

    public static void insertDirectToValues(CtTypeReference type, CtElement value) {
        if (!CandidatePool.directToValues.containsKey(type))
            CandidatePool.directToValues.put(type, new HashSet<>());
        CandidatePool.directToValues.get(type).add(value);
    }

    public static Set<CtMethod> getTestcases() {
        return testcases;
    }

    public static void setTestcases(Set<CtMethod> testcases) {
        CandidatePool.testcases = testcases;
    }

    public static CtModel getTestFile() {
        return testFile;
    }

    public static void setTestFile(CtModel testFile) {
        CandidatePool.testFile = testFile;
    }
}

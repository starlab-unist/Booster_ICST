package Generater.PrimitiveMutation;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.*;
import spoon.support.reflect.declaration.CtMethodImpl;
import spoon.support.reflect.reference.CtExecutableReferenceImpl;
import spoon.support.reflect.reference.CtFieldReferenceImpl;
import spoon.support.reflect.reference.CtLocalVariableReferenceImpl;
import utils.Config;

import java.util.*;

/**
 * this class is build for parser the candidate for primitive value
 */
public class PrimitiveMutateParser {

    private static Map<String, Set<CtElement>> primitiveTypeAndVal = new HashMap<>();
    private static Map<CtTypeReference, String> primitiveReferenceToName = new HashMap<>();
    private static Set<CtMethod> testcases = new HashSet<>();
    private static String packageName; 
    private static String className;

    public static String getClassName() {
        return className;
    }

    public static void setClassName(String className) {
        PrimitiveMutateParser.className = className;
    }

    public static String getPackageName() {
        return packageName;
    }

    /**
     * set testcases
     *
     * @param testcases
     */
    public static void setTestcases(Set<CtMethod> testcases) {
        PrimitiveMutateParser.testcases = testcases;
    }

    /**
     * get testcases
     *
     * @return
     */
    public static Set<CtMethod> getTestcases() {
        return testcases;
    }

    /**
     * get type to String map
     *
     * @return
     */
    public static Map<CtTypeReference, String> getPrimitiveReferenceToName() {
        return primitiveReferenceToName;
    }

    /**
     * get string to value map
     *
     * @return
     */
    public static Map<String, Set<CtElement>> getPrimitiveTypeAndVal() {
        return primitiveTypeAndVal;
    }

    /**
     * insert type and value into the map
     *
     * @param type
     * @param val
     * @return
     */
    private static boolean insertPrimitiveTypeAndVal(CtTypeReference type, CtElement val) {
        if (!primitiveTypeAndVal.containsKey(type.toString()))
            primitiveTypeAndVal.put(type.toString(), new HashSet<>());
        primitiveReferenceToName.put(type, type.toString());
        /**
         * temp fix for double.nan
         */
        if (val.toString().startsWith("java.lang.Double.NaN"))
            if (!(val.getParent() instanceof CtInvocation))
                return primitiveTypeAndVal.get(type.toString()).add(val);
        return primitiveTypeAndVal.get(type.toString()).add(val);
    }

    /**
     * directly handle the primitive
     *
     * @param parent
     */
    private static void processPrimitive(CtElement parent) {
        if (parent instanceof CtUnaryOperatorImpl) {
            /**
             * like -1F
             */
            CtUnaryOperatorImpl ctUnaryOperator = (CtUnaryOperatorImpl) parent;
            List casts = ctUnaryOperator.getTypeCasts();
            if (casts.size() == 0)
                insertPrimitiveTypeAndVal(ctUnaryOperator.getType(), ctUnaryOperator);
            else
                insertPrimitiveTypeAndVal((CtTypeReference) casts.get(0), ctUnaryOperator);
        } else if (parent instanceof CtLocalVariable) {
            /**
             * this situation can be ignored for most situations the evosuite will
             * minify the code, which means that the there will be no direct assignment
             * like int i=1;
             */
        } else if (parent instanceof CtExecutableReferenceImpl) {
            /**
             * this be ignored by treating method invocation specially
             */
        } else if (parent instanceof CtLiteralImpl) {
            /**
             * e.g. 1F
             */
            if (((CtLiteralImpl) parent).getTypeCasts().size() == 0)
                insertPrimitiveTypeAndVal(((CtLiteralImpl) parent).getType(), parent);
            else
                insertPrimitiveTypeAndVal((CtTypeReference) ((CtLiteralImpl) parent).getTypeCasts().get(0), parent);
        } else if (parent instanceof CtFieldReadImpl) {
            /**
             * e.g. java.lang.Double.NaN
             */
            CtFieldReadImpl fieldReference = (CtFieldReadImpl) parent;
            /**
             * filter the custom type
             */
            if (!fieldReference.toString().startsWith("java.lang"))
                return;
            insertPrimitiveTypeAndVal(fieldReference.getType(), fieldReference);
        } else if (parent instanceof CtAssignmentImpl) {
            /**
             * no possibility for assignment in this way
             */
            throw new RuntimeException("impossible argument in the method");
        } else if (parent instanceof CtArrayTypeReference) {
            CtArrayTypeReference array = (CtArrayTypeReference) parent;
            CtElement arrayParent = array.getParent();
            /**
             * int [] arr= new int[2];
             */
            if (arrayParent instanceof CtLocalVariableImpl) {
                insertPrimitiveTypeAndVal(((CtLocalVariableImpl) arrayParent).getAssignment().getType(), ((CtLocalVariableImpl) arrayParent).getDefaultExpression());
            } else if (arrayParent instanceof CtNewArrayImpl) {
                /**
                 * new int[2]
                 */
                insertPrimitiveTypeAndVal(((CtNewArrayImpl) arrayParent).getType(), arrayParent);
            } else if (arrayParent instanceof CtAssignmentImpl) {
                /**
                 * e.g. p.arr=arr;
                 * just ignore
                 */
            } else if (arrayParent instanceof CtFieldReferenceImpl) {
                /**
                 *  sub scene of e.g. p.arr=arr;
                 * just ignore
                 */
            } else if (arrayParent instanceof CtLocalVariableReferenceImpl) {
                /**
                 * reference for array variable
                 * just ignore
                 */
            } else if (arrayParent instanceof CtExecutableReferenceImpl) {
                /**
                 * obj.m1(int[],int[],int)
                 * this should be handled by the method invocation part
                 */
            } else {
                throw new RuntimeException("no match type for array reference");
            }
        } else if (parent instanceof CtArrayWrite) {
            CtArrayWrite arrayWrite = (CtArrayWrite) parent;
            CtAssignmentImpl assign = (CtAssignmentImpl) arrayWrite.getParent();
            if (assign.getAssignment() instanceof CtLiteralImpl)
                processPrimitive(assign.getAssignment());
        } else if (parent instanceof CtVariableReadImpl) {
            /**
             * charArray0[0] = char1;
             * we skip this statement
             */
        } else if (parent instanceof CtArrayReadImpl) {
            /**
             * charArray0[0] = ((double) (doubleArray0[3]));
             * we skip this statement
             */
        } else if (parent instanceof CtInvocationImpl) {
            /**
             * bool0 = flat3Map_Values0.remove(((java.lang.Object) (flat3Map1)))
             * we skip this statement
             */
        } else {
            throw new RuntimeException("no match primitive in extractPrimitive:" + parent);
        }
    }

    /**
     * process primitive variable
     *
     * @param var
     */
    private static void processPrimitive(CtTypeReference var) {
        CtElement parent = var.getParent();
        if (parent instanceof CtUnaryOperatorImpl) {
            /**
             * like -1F
             */
            CtUnaryOperatorImpl ctUnaryOperator = (CtUnaryOperatorImpl) parent;
            List casts = ctUnaryOperator.getTypeCasts();
            if (casts.size() == 0)
                insertPrimitiveTypeAndVal(ctUnaryOperator.getType(), ctUnaryOperator);
            else
                insertPrimitiveTypeAndVal((CtTypeReference) casts.get(0), ctUnaryOperator);
        } else if (parent instanceof CtLocalVariable) {
            /**
             * this situation can be ignored for most situations the evosuite will
             * minify the code, which means that the there will be no direct assignment
             * like int i=1;
             */
        } else if (parent instanceof CtExecutableReferenceImpl) {
            /**
             * this be ignored by treating method invocation specially
             */
        } else if (parent instanceof CtLiteralImpl) {
            /**
             * e.g. 1F
             */
            if (((CtLiteralImpl) parent).getTypeCasts().size() == 0)
                insertPrimitiveTypeAndVal(((CtLiteralImpl) parent).getType(), parent);
            else
                insertPrimitiveTypeAndVal((CtTypeReference) ((CtLiteralImpl) parent).getTypeCasts().get(0), parent);
        } else if (parent instanceof CtFieldReferenceImpl) {
            /**
             * e.g. java.lang.Double.NaN
             */
            CtFieldReferenceImpl fieldReference = (CtFieldReferenceImpl) parent;
            /**
             * filter the custom type
             */
            if (!fieldReference.toString().startsWith("java.lang"))
                return;
            insertPrimitiveTypeAndVal(fieldReference.getType(), fieldReference);
        } else if (parent instanceof CtAssignmentImpl) {
            CtAssignmentImpl assign = (CtAssignmentImpl) parent;
            /**
             * for primitive read like java.lang.Double.NaN
             */
            if (assign.getAssignment().getType().isPrimitive()) {
                processPrimitive(assign.getAssignment().getType());
                return;
            }
//            throw new RuntimeException("Unknown assignment type!"); //we do not handle non-primitive anyways
        } else if (parent instanceof CtArrayTypeReference) {
            CtArrayTypeReference array = (CtArrayTypeReference) parent;
            CtElement arrayParent = array.getParent();
            /**
             * int [] arr= new int[2];
             */
            if (arrayParent instanceof CtLocalVariableImpl) {
                insertPrimitiveTypeAndVal(((CtLocalVariableImpl) arrayParent).getAssignment().getType(), ((CtLocalVariableImpl) arrayParent).getDefaultExpression());
            } else if (arrayParent instanceof CtNewArrayImpl) {
                /**
                 * new int[2]
                 */
                insertPrimitiveTypeAndVal(((CtNewArrayImpl) arrayParent).getType(), arrayParent);
            } else if (arrayParent instanceof CtAssignmentImpl) {
                /**
                 * e.g. p.arr=arr;
                 * just ignore
                 */
            } else if (arrayParent instanceof CtFieldReferenceImpl) {
                /**
                 *  sub scene of e.g. p.arr=arr;
                 * just ignore
                 */
            } else if (arrayParent instanceof CtLocalVariableReferenceImpl) {
                /**
                 * reference for array variable
                 * just ignore
                 */
            } else if (arrayParent instanceof CtExecutableReferenceImpl) {
                /**
                 * obj.m1(int[],int[],int)
                 * this should be handled by the method invocation part
                 */
            } else if (arrayParent instanceof CtArrayTypeReference) {
                /**
                 * double array be ignored
                 */
            } else if (arrayParent instanceof CtLiteralImpl) {
                /**
                 * handle (char[])(null)
                 */
                if (((CtLiteralImpl) arrayParent).getTypeCasts().size() == 0)
                    insertPrimitiveTypeAndVal(((CtLiteralImpl) arrayParent).getType(), arrayParent);
                else
                    insertPrimitiveTypeAndVal((CtTypeReference) ((CtLiteralImpl) arrayParent).getTypeCasts().get(0), arrayParent);
            } else if (arrayParent instanceof CtArrayWriteImpl) {
                /**
                 * doubleArray0[0] = xxx be ignored
                 */
            } else if (arrayParent instanceof CtArrayReadImpl) {
                /**
                 * doubleArray0[1] = doubleArray0[0] be ignored
                 */
            } else if (arrayParent instanceof CtTypeReference) {
                /**
                 * java.lang.Class<double[]>
                 */
            } else if (arrayParent instanceof CtTypeAccess) {
                /**
                 * double[]
                 */
            } else {
                throw new RuntimeException("no match type for array reference");
            }
        } else if (parent instanceof CtArrayWriteImpl) {
            CtArrayWrite arrayWrite = (CtArrayWriteImpl) parent;
            CtAssignmentImpl assign = (CtAssignmentImpl) arrayWrite.getParent();
            processPrimitive(assign.getAssignment());
        } else if (parent instanceof CtLocalVariableReference) {
            /**
             * handle literal assignment
             */
            if (((CtLocalVariableReference) parent).getDeclaration().getReference().getType().isPrimitive()) {
                if (parent.getParent() instanceof CtVariableWrite) {
                    CtVariableWrite write = (CtVariableWrite) (parent.getParent());
                    CtTypeReference type = write.getType();
                    if (write.getParent() instanceof CtAssignmentImpl) {
                        CtAssignmentImpl stmt = (CtAssignmentImpl) write.getParent();
                        CtExpression assign = stmt.getAssignment();
                        processPrimitive(assign);
                    } else if (write.getParent() instanceof CtUnaryOperatorImpl) {
                        CtUnaryOperatorImpl stmt = (CtUnaryOperatorImpl) write.getParent();
                        processPrimitive(stmt);
                    }
                } else if (parent.getParent() instanceof CtVariableReadImpl) {
                    /**
                     * Ignore when primitive variable is just read
                     */
                } else {
                    throw new RuntimeException("not array write");
                }
            } else {
                throw new RuntimeException("the local variable is not primitive type");
            }
        } else if (parent instanceof CtFieldReadImpl) {
            /**
             * ignore cause we can not tell the type
             */
        } else if (parent instanceof CtVariableReadImpl) {
            /**
             * ignore cause we can not tell the type
             */
        } else if (parent instanceof CtArrayReadImpl) {
            /**
             * ignore because right hand side is not primitive such as characterArray0[1] = (Character) charArray0[0];
             */
        } else if (parent instanceof CtMethodImpl) {
            /**
             * ignore. we do not handle when methodImpl is declared as method param
             */
        } else if (parent instanceof CtBinaryOperatorImpl) {
            /**
             * ignore. (boolean1 == boolean0) in Assertion statements, we don't mutate assertions
             */
        } else {
            throw new RuntimeException("no match primitive in extractPrimitive: " + parent);
        }
    }

    /**
     * extract primitive from testcase
     * except the method invocation
     *
     * @param testcase
     */
    private static void extractPrimitive(CtMethod testcase) {
        List<CtTypeReference> vars = testcase.getBody().getElements(new TypeFilter<>(CtTypeReference.class));
        for (CtTypeReference var : vars) {
            if (!var.isPrimitive())
                continue;
            if (var.getParent().getParent() instanceof CtArrayWriteImpl) {
                processPrimitive(var.getParent().getParent());
            }
            processPrimitive(var);
        }
    }

    /**
     * extract primitive from method
     *
     * @param testcase
     */
    private static void extractPrimitiveFromMethod(CtMethod testcase) {
        List<CtInvocationImpl> invokes = testcase.getElements(new TypeFilter<>(CtInvocationImpl.class));
        for (CtInvocationImpl invoke : invokes) {
            List<CtElement> args = invoke.getArguments();
            for (CtElement arg : args) {
                if (arg instanceof CtVariableReadImpl || arg instanceof CtInvocationImpl || arg instanceof CtConstructorCallImpl || arg instanceof CtArrayReadImpl || arg instanceof CtBinaryOperatorImpl || arg instanceof CtThisAccessImpl)
                    continue;
                if (arg instanceof CtLiteralImpl || arg instanceof CtUnaryOperatorImpl || arg instanceof CtFieldReadImpl){
                    processPrimitive(arg);
                }
                else {
                    throw new RuntimeException("unrecongnized argument type: " + arg);
                }
            }
        }
    }


    /**
     * extract primitive type from the constructor
     *
     * @param testcase
     */
    private static void extractPrimitiveFromConstructor(CtMethod testcase) {
        List<CtConstructorCallImpl> constructors = testcase.getElements(new TypeFilter<>(CtConstructorCallImpl.class));
        for (CtConstructorCallImpl constructorCall : constructors) {
            List<CtElement> args = constructorCall.getArguments();
            for (CtElement arg : args) {
                if (arg instanceof CtVariableReadImpl || arg instanceof CtArrayReadImpl || arg instanceof CtInvocationImpl ||
                        arg instanceof CtBinaryOperatorImpl)
                    continue;
                if (arg instanceof CtLiteralImpl || arg instanceof CtUnaryOperatorImpl || arg instanceof CtFieldReadImpl){
                    processPrimitive(arg);
                }
                else {
                    throw new RuntimeException("unrecongnized argument type: " + arg);
                }
            }
        }
    }

    /**
     * Process entry for test cases
     *
     * @param testcases
     */
    private static void process(Set<CtMethod> testcases, long time_budget, long startTime ) {
        /**
         * main loop process
         */
        // int counter  = 0;
        for (CtMethod testcase : testcases) {

            long currentTime = System.currentTimeMillis();
            long elapsedTime = currentTime - startTime;
            // System.out.println("Counter : "+counter);
            // 남은 시간이 time_budget을 초과하는지 확인
            if (elapsedTime > time_budget) {
                System.out.println("Seq-P Collecting Timeout Time Budget : "+time_budget +" ms" +" Collecting Time: " + elapsedTime + " ms");
                System.out.println("Terminating");
                System.exit(1);
            }

//            System.out.println(testcase.getSimpleName());
//            if (Config.REGRESSION_MODE)
//                removeTryCatchAndAssertion(testcase);
            removeTryCatchAndAssertion(testcase);
            extractPrimitive(testcase);
            extractPrimitiveFromMethod(testcase);
            extractPrimitiveFromConstructor(testcase);
            extractLocalVariableAssignment(testcase);
            // counter+=1;
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
     * handle assignment
     *
     * @param testcase
     */
    private static void extractLocalVariableAssignment(CtMethod testcase) {
        List<CtStatement> stmts = testcase.getBody().getStatements();
        /**
         * go through statements
         */
        for (CtStatement statement : stmts
        ) {
            if (!(statement instanceof CtLocalVariable))
                continue;
            CtLocalVariable localDef = (CtLocalVariable) statement;
            if (!localDef.getReference().getType().isPrimitive() || !localDef.getReference().getType().toString().contains("java.lang.String"))
                continue;
            insertPrimitiveTypeAndVal(localDef.getReference().getType(), localDef.getAssignment());
        }
    }

    /**
     * filter the test cases method
     *
     * @param ctModel
     * @return
     */
    private static Set<CtMethod> getTestCases(CtModel ctModel) {
        Set<CtMethod> testcases = new HashSet<CtMethod>();
        for (CtType ctType : ctModel.getAllTypes()) {
            for (Object ctMethod : ctType.getAllMethods()) {
                String name = ((CtMethod) ctMethod).getSimpleName();
                if (!name.startsWith("test"))
                    continue;
                testcases.add((CtMethod) ctMethod);
            }
        }
        return testcases;
    }

    /**
     * process add null type and specific value
     */
    private static void postProcess() {
        Factory factory = new CtExecutableReferenceImpl<>().getFactory();
        /**
         * create null type
         */
        CtTypeReference<Object> ref = factory.createTypeReference();
        ref.setSimpleName("null");
        /**
         * create '\u0000' char value
         */
        CtLiteral<Character> charDef = factory.createLiteral('\u0000');
        for (String type : primitiveTypeAndVal.keySet()) {
            /**
             * add null for string type
             */
            if (type.equals("java.lang.String"))
                primitiveTypeAndVal.get(type).add(ref);
            /**
             * add special char value
             */
            if (type.equals("char"))
                primitiveTypeAndVal.get(type).add(charDef);
            /**
             * add null for non primitive type
             */
            //if (!type.equals("int") && !type.equals("double") && !type.equals("float") && !type.equals("boolean") && !type.equals("char") && !type.equals("long") && !type.equals("byte") && !type.equals("short"))
            //    primitiveTypeAndVal.get(type).add(ref);
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
//        if (Config.REGRESSION_MODE) {
//            String srcPath = Config.SRC_PATH;
//            if (!srcPath.endsWith(File.separator))
//                srcPath = srcPath + File.separator;
//            srcPath = srcPath + Config.FULL_CLASS_NAME.replace(".", "/") + ".java";
//            launcher.addInputResource(srcPath);
//        }
        launcher.buildModel();
        return launcher.getModel();
    }

    /**
     * entry of ct element
     *
     * @param path
     */
    public static void run(String path,long time_budget, long startTime) {
        CtModel clazz = init(path);
        renameVariable(clazz);
        getPackage(clazz);
        List<CtClass> elements = (List<CtClass>) clazz.getElements(new TypeFilter(CtClass.class));
        setClassName(elements.get(0).getSimpleName());
        Set<CtMethod> testcases = getTestCases(clazz);
        setTestcases(testcases);
        process(testcases,time_budget,startTime);
        postProcess();
//        printMap();
    }

    private static void renameVariable(CtModel testClass) {
        List<CtMethod> methods = testClass.getElements(new TypeFilter<>(CtMethod.class));
        int index = 0;
        Set<CtVariable> usedDefs = new HashSet<>();
        for (CtMethod method : methods) {
            List<CtVariableReference> vars = method.getElements(new TypeFilter<>(CtVariableReference.class));
            Map<CtVariable, List<CtVariableReference>> defUsePair = new HashMap<>();
            for (CtVariableReference var : vars) {
//                System.out.println(var.toString());
                CtVariable def = var.getDeclaration();
                if (def == null) {
                    continue;
                }
//                System.out.println(def.toString());
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
     * get package name
     *
     * @param clazz
     */
    private static void getPackage(CtModel clazz) {
        List<String> className = Arrays.asList(Config.FULL_CLASS_NAME.split("\\."));
        packageName = String.join(".", className.subList(0, className.size() - 1));
//        packageName = clazz.getAllPackages().toArray()[clazz.getAllPackages().size() - 1].toString();
    }


    /**
     * debug print map
     */
    private static void printMap() {
        Set<String> types = primitiveTypeAndVal.keySet();
        for (String type : types) {
            System.out.println("****************");
            System.out.println(">>>>>>>>New type>>>>>>");
            System.out.println(type);
            System.out.println(">>>>>>>>>>>>>>");
            for (CtElement val : primitiveTypeAndVal.get(type)) {
                System.out.println(val);
            }
            System.out.println("****************");
        }
    }
}

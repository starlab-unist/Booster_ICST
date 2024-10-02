package Generater.MUTMutation;

import spoon.Launcher;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.*;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtTypeReference;
import sun.misc.URLClassPath;
import utils.Config;
import utils.Pair;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URLClassLoader;
import java.util.*;

public class InputCombinations {
    private String packageAndImport;
    private Map<MUTInput, Set<List<Input>>> combinationsMap = new HashMap<>();

    private HashMap<CtAbstractInvocation, Set<List<CtTypeReference>>> methods;

    private Set<MUTInput> muts;
    private boolean simplied = true;

    private int inputNum = 0;

    public Set<MUTInput> getMuts() {
        return muts;
    }

    public Map<MUTInput, Set<List<Input>>> getCombinationsMap() {
        return combinationsMap;
    }

    public void run(String testFile, long time_budget, long startTime) {
        try {

            packageAndImport = getImportAndPackage(testFile);

            ASTParser.parser(testFile,time_budget,startTime);

        } catch (Exception e) {
            e.printStackTrace();
        }
        muts = processMUTs(time_budget,startTime);
        if(muts == null){
            System.out.println("Null Input detected either by timeout or other errors");
            System.exit(1);
        }
    }

    private LinkedHashMap<Integer, List<Input>> inputPoolsEqualityCheck(
            LinkedHashMap<Integer, List<Input>> inputPools) {
        Factory factory = new Launcher().getFactory();
        initOutputFolderForClassLoader();
        LinkedHashMap<Integer, List<Input>> setInputPools = new LinkedHashMap<Integer, List<Input>>();
        for (Integer key : inputPools.keySet()) {
            List<Input> inputs = inputPools.get(key);
            List<Input> setInputs = new ArrayList<>();
            for (Input input : inputs) {
                if (input.getType().isPrimitive()) {
                    setInputs.addAll(inputs);
                    break;
                }
                if (setInputs.size() == 0) {
                    setInputs.add(input);
                    continue;
                }
                boolean equalFlag = false;
                for (Input otherInput : setInputs) {
                    try {
                        CtClass<Object> clazz = makeEqualityCheckClass("EqualityCheck_" + (inputNum), input,
                                otherInput);
                        List<Diagnostic<? extends JavaFileObject>> errorMessage = new ArrayList<>();
                        // writeFile("EqualityCheck_"+(inputNum),packageAndImport+clazz.toString());
                        errorMessage = compileWholeClassFile("EqualityCheck_" + (inputNum),
                                packageAndImport + clazz.toString());
                        if (!checkCompileError(errorMessage)) {
                            System.out.println("EqualityCheck does not compiled");
                            System.out.println(clazz.toString());
                            System.out.println(errorMessage);
                            equalFlag = true;
                        } else {
                            Class<?> equalityCheckClass = null;
                            equalityCheckClass = ClassLoader.getSystemClassLoader()
                                    .loadClass(packageNamePlusFileName("EqualityCheck_" + (inputNum)));
                            Object instance = equalityCheckClass.newInstance();
                            Method equalityCheckMethod = equalityCheckClass.getMethod("equalitycheck");
                            equalFlag = (boolean) equalityCheckMethod.invoke(instance);
                            deleteClassFile("EqualityCheck_" + (inputNum));
                        }
                    } catch (ClassNotFoundException | NoSuchMethodException e) {
                        System.out.println(String.format("Load running class : %s failed.",
                                packageNamePlusFileName("EqualityCheck_" + (inputNum))));
                    } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                        System.out.println(String.format("Instantiate class : %s failed.",
                                packageNamePlusFileName("EqualityCheck_" + (inputNum))));
                        e.printStackTrace();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    inputNum++;
                    if (equalFlag) {
                        break;
                    }
                }
                if (!equalFlag) {
                    setInputs.add(input);
                }
            }
            setInputPools.put(key, setInputs);
        }
        return setInputPools;
    }

    private List<Input> inputPoolEqualityCheck(List<Input> inputPool) {
        Factory factory = new Launcher().getFactory();
        initOutputFolderForClassLoader();
        List<Input> setInputs = new ArrayList<>();
        for (Input input : inputPool) {
            if (input.getType() == null) {
                return inputPool;
            }
            if (input.getType().isPrimitive()) {
                setInputs.addAll(inputPool);
                break;
            }
            if (setInputs.size() == 0) {
                setInputs.add(input);
                continue;
            }
            boolean equalFlag = false;
            for (Input otherInput : setInputs) {
                try {
                    CtClass<Object> clazz = makeEqualityCheckClass("EqualityCheck_" + (inputNum), input, otherInput);
                    List<Diagnostic<? extends JavaFileObject>> errorMessage = new ArrayList<>();
                    // writeFile("EqualityCheck_"+(inputNum),packageAndImport+clazz.toString());
                    errorMessage = compileWholeClassFile("EqualityCheck_" + (inputNum),
                            packageAndImport + clazz.toString());
                    if (!checkCompileError(errorMessage)) {
                        System.out.println("EqualityCheck does not compiled");
                        System.out.println(clazz.toString());
                        System.out.println(errorMessage);
                        equalFlag = true;
                    } else {
                        Class<?> equalityCheckClass = null;
                        equalityCheckClass = ClassLoader.getSystemClassLoader()
                                .loadClass(packageNamePlusFileName("EqualityCheck_" + (inputNum)));
                        Object instance = equalityCheckClass.newInstance();
                        Method equalityCheckMethod = equalityCheckClass.getMethod("equalitycheck");
                        equalFlag = (boolean) equalityCheckMethod.invoke(instance);
                        deleteClassFile("EqualityCheck_" + (inputNum));
                    }
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    System.out.println(String.format("Load running class : %s failed.",
                            packageNamePlusFileName("EqualityCheck_" + (inputNum))));
                } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
                    System.out.println(String.format("Instantiate class : %s failed.",
                            packageNamePlusFileName("EqualityCheck_" + (inputNum))));
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                inputNum++;
                if (equalFlag) {
                    break;
                }
            }
            if (!equalFlag) {
                setInputs.add(input);
            }
        }
        return setInputs;
    }

    private Set<MUTInput> processMUTs(long time_budget, long startTime) {
        Set<MUTInput> result = new HashSet<>();
        HashMap<CtAbstractInvocation, Set<List<CtTypeReference>>> methods = CandidatePool.getMUTnameToArgtypes();
        HashMap<CtTypeReference, List<Input>> varTypeToInputPool = new HashMap<>();
        long collectingTime = 0;
        for (CtAbstractInvocation method : methods.keySet()) {
            collectingTime = System.currentTimeMillis() - startTime; 
            if(collectingTime > time_budget){
                System.out.println("Seq-C Collecting Timeout Time Budget : "+time_budget +" ms" +" Collecting Time: " + collectingTime + " ms");
                System.out.println("Terminating");
                return null;
            }
            // System.out.println("method : " + method);
            for (List<CtTypeReference> types : methods.get(method)) {
                LinkedHashMap<Integer, List<Input>> hashedInputPools = new LinkedHashMap<>();
                // System.out.println("types : ");
                // //여기서의 타입은 메소드를 호출한 객체의 타입 또는, 파라미터의 타입을 말함
                // // method : stackedBarRenderer3D0_52.getSeriesURLGenerator(5)
                // // types : [org.jfree.chart.renderer.category.StackedBarRenderer3D, int]
                for (int i = 0; i < types.size(); i++) {
                    CtTypeReference type = types.get(i);
                    // System.out.println(" "+type);
                    List<Input> inputs;
                    if (varTypeToInputPool.containsKey(type))
                        inputs = varTypeToInputPool.get(type);
                    else {
                        inputs = processInputPool(type, i == 0);
                        // inputs = inputPoolEqualityCheck(inputs);
                        varTypeToInputPool.put(type, inputs);
                    }
                    hashedInputPools.put(i, inputs);
                }
                MUTInput inst = new MUTInput(method, types, hashedInputPools);
                
                result.add(inst);
            }
        }
        return result;
    }

    private List<Input> processInputPool(CtTypeReference type, boolean isReceiverObject) {
        List<Input> inputs = new ArrayList<>();

        HashMap<CtTypeReference, Set<Pair<CtTypeReference, CtElement>>> pairsMap = CandidatePool.getVarTypeNamePool();
        HashMap<Pair<CtTypeReference, CtElement>, Set<List<CtElement>>> pairsToInputMap = CandidatePool
                .getVartypeToInputPool();
        HashMap<CtTypeReference, Set<CtElement>> directValuesMap = CandidatePool.getDirectToValues();

        if (isReceiverObject && type == null) { // MUT is static, so arg[0] is null
            Input input = new Input(true);
            inputs.add(input);
            return inputs;
        }
        boolean containsKeyInDirectValuesMap = directValuesMap.containsKey(type);
        boolean containsKeyInPairsMap = pairsMap.containsKey(type);

        if (type.isPrimitive()) {
            // if (containsKeyInDirectValuesMap) { //if there exists direct value input, we
            // just use this for primitives
            // Set<List<CtElement>> converted =
            // convertPrimitiveStructure(directValuesMap.get(type));
            // inputs = fillInputStructure(converted, type);
            // } else { //if there exists no direct value input for primitves, we use ones
            // in pairsToInputMap
            // if (containsKeyInPairsMap) {
            // Set<Pair<CtTypeReference, CtElement>> pairs = pairsMap.get(type);
            // for (Pair<CtTypeReference, CtElement> pair : pairs) {
            // Set<List<CtElement>> inputSet = pairsToInputMap.get(pair);
            // inputs = fillInputStructure(inputSet, type, pair.getValue());
            // }
            // } else {
            // System.err.println("WARNING InputCombinations.processMUTs: no key exists in
            // both direct value and Pair for primitive type " + type.toString());
            // }
            // }
            if (containsKeyInDirectValuesMap) {
                Set<List<CtElement>> converted = convertPrimitiveStructure(directValuesMap.get(type));
                inputs = fillInputStructure(converted, type);
            }
            if (containsKeyInPairsMap) {
                Set<Pair<CtTypeReference, CtElement>> pairs = pairsMap.get(type);
                for (Pair<CtTypeReference, CtElement> pair : pairs) {
                    Set<List<CtElement>> inputSet = pairsToInputMap.get(pair);
                    inputs.addAll(fillInputStructure(inputSet, type, pair.getValue()));
                }
            }
            if (!containsKeyInDirectValuesMap && !containsKeyInPairsMap) {
                System.err.println(
                        "WARNING InputCombinations.processMUTs: no key exists in both direct value and Pair for primitive type "
                                + type.toString());
            }

        } else {
            if (containsKeyInDirectValuesMap) { // if there exists direct value input, we additionally use this for
                                                // input
                Set<List<CtElement>> converted = convertPrimitiveStructure(directValuesMap.get(type));
                inputs = fillInputStructure(converted, type);
                // System.out.println(directValuesMap.get(type));
            }

            if (containsKeyInPairsMap) {
                Set<Pair<CtTypeReference, CtElement>> pairs = pairsMap.get(type);
                for (Pair<CtTypeReference, CtElement> pair : pairs) {
                    Set<List<CtElement>> inputSet = pairsToInputMap.get(pair);
                    inputs = fillInputStructure(inputSet, type, pair.getValue());
                }
            }

            if (!containsKeyInDirectValuesMap && !containsKeyInPairsMap) {
                Set<Pair<CtTypeReference, CtElement>> pairs = furtherFindInPairs(pairsMap, type);
                if (pairs != null) { // For some reason, there are few types that are different in MUTnameToArgtypes
                                     // and varTypeNamePool
                    for (Pair<CtTypeReference, CtElement> pair : pairs) {
                        Set<List<CtElement>> inputSet = pairsToInputMap.get(pair);
                        inputs = fillInputStructure(inputSet, type, pair.getValue());
                    }
                } else {
                    System.err.println(
                            "WARNING InputCombinations.processMUTs: no key exists in both direct value and Pair for type "
                                    + type.toString());
                }
            }
        }

        return inputs;
    }

    private LinkedHashMap<Integer, List<Input>> processMUT(List<CtTypeReference> arginputs) {
        LinkedHashMap<Integer, List<Input>> result = new LinkedHashMap<>();

        HashMap<CtTypeReference, Set<Pair<CtTypeReference, CtElement>>> pairsMap = CandidatePool.getVarTypeNamePool();
        HashMap<Pair<CtTypeReference, CtElement>, Set<List<CtElement>>> pairsToInputMap = CandidatePool
                .getVartypeToInputPool();
        HashMap<CtTypeReference, Set<CtElement>> directValuesMap = CandidatePool.getDirectToValues();

        for (int i = 0; i < arginputs.size(); i++) {
            CtTypeReference type = arginputs.get(i);
            if (i == 0 && type == null) { // MUT is static, so arg[0] is null
                Input input = new Input(true);
                List<Input> inputs = new ArrayList<>();
                inputs.add(input);
                insertArgInputs(result, i, inputs);
                continue;
            }
            boolean containsKeyInDirectValuesMap = directValuesMap.containsKey(type);
            boolean containsKeyInPairsMap = pairsMap.containsKey(type);
            if (type.isPrimitive()) {
                if (containsKeyInDirectValuesMap) { // if there exists direct value input, we just use this for
                                                    // primitives
                    Set<List<CtElement>> converted = convertPrimitiveStructure(directValuesMap.get(type));
                    List<Input> inputs = fillInputStructure(converted, type);
                    insertArgInputs(result, i, inputs);
                } else { // if there exists no direct value input for primitves, we use ones in
                         // pairsToInputMap
                    if (containsKeyInPairsMap) {
                        Set<Pair<CtTypeReference, CtElement>> pairs = pairsMap.get(type);
                        for (Pair<CtTypeReference, CtElement> pair : pairs) {
                            Set<List<CtElement>> inputSet = pairsToInputMap.get(pair);
                            List<Input> inputs = fillInputStructure(inputSet, type, pair.getValue());
                            insertArgInputs(result, i, inputs);
                        }
                    } else {
                        System.err.println(
                                "WARNING InputCombinations.processMUTs: no key exists in both direct value and Pair for primitive type "
                                        + type.toString());
                        result.put(i, new ArrayList<>());
                    }
                }
            } else {
                if (containsKeyInDirectValuesMap) { // if there exists direct value input, we additionally use this for
                                                    // input
                    Set<List<CtElement>> converted = convertPrimitiveStructure(directValuesMap.get(type));
                    List<Input> inputs = fillInputStructure(converted, type);
                    insertArgInputs(result, i, inputs);
                }

                if (containsKeyInPairsMap) {
                    Set<Pair<CtTypeReference, CtElement>> pairs = pairsMap.get(type);
                    for (Pair<CtTypeReference, CtElement> pair : pairs) {
                        Set<List<CtElement>> inputSet = pairsToInputMap.get(pair);
                        List<Input> inputs = fillInputStructure(inputSet, type, pair.getValue());
                        insertArgInputs(result, i, inputs);
                    }
                }

                if (!containsKeyInDirectValuesMap && !containsKeyInPairsMap) {
                    Set<Pair<CtTypeReference, CtElement>> pairs = furtherFindInPairs(pairsMap, type);
                    if (pairs != null) { // For some reason, there are few types that are different in MUTnameToArgtypes
                                         // and varTypeNamePool
                        for (Pair<CtTypeReference, CtElement> pair : pairs) {
                            Set<List<CtElement>> inputSet = pairsToInputMap.get(pair);
                            List<Input> inputs = fillInputStructure(inputSet, type, pair.getValue());
                            insertArgInputs(result, i, inputs);
                        }
                    } else {
                        System.err.println(
                                "WARNING InputCombinations.processMUTs: no key exists in both direct value and Pair for type "
                                        + type.toString());
                        result.put(i, new ArrayList<>());
                    }
                }
            }
        }
        return result;
    }

    private Set<Pair<CtTypeReference, CtElement>> furtherFindInPairs(
            HashMap<CtTypeReference, Set<Pair<CtTypeReference, CtElement>>> pairsMap, CtTypeReference type) {
        for (CtTypeReference t : pairsMap.keySet()) {
            if (t.toString().endsWith(type.toString())) { // we treat this as the same
                return pairsMap.get(t);
            }
        }
        return null;
    }

    private boolean insertArgInputs(LinkedHashMap<Integer, List<Input>> result, int argPos, List<Input> inputs) {
        if (!result.containsKey(argPos)) {
            result.put(argPos, new ArrayList<>());
        }
        return result.get(argPos).addAll(inputs);
    }

    /**
     * For those arguments that have variable name.
     * In this case,
     *
     * @param inputs
     * @param type
     * @param varName
     * @return
     */
    private List<Input> fillInputStructure(Set<List<CtElement>> inputs, CtTypeReference type, CtElement varName) {
        List<Input> result = new ArrayList<>();
        for (List<CtElement> input : inputs) {
            Input inst = new Input(type, true, varName, input);
            result.add(inst);
        }
        return result;
    }

    /**
     * For those arguments that do not have variable name
     *
     * @param inputs
     * @param type
     * @return
     */
    private List<Input> fillInputStructure(Set<List<CtElement>> inputs, CtTypeReference type) {
        List<Input> result = new ArrayList<>();

        for (List<CtElement> input : inputs) {
            CtElement directValue = null;
            if (input.size() == 1) {
                directValue = input.get(0);
            } else {
                System.err
                        .println("InputCombination.fillInputStructure: for directValue, input size should be 1. Type: "
                                + type + ", Input: " + input);
                continue;
            }
            Input inst = new Input(type, false, directValue, input);
            result.add(inst);
        }
        return result;
    }

    private Set<List<CtElement>> convertPrimitiveStructure(Set<CtElement> elements) {
        Set<List<CtElement>> results = new HashSet<>();
        for (CtElement e : elements) {
            List<CtElement> list = new ArrayList<>();
            list.add(e);
            results.add(list);
        }
        return results;
    }

    public CtClass<Object> makeEqualityCheckClass(String fileName, Input o1, Input o2) {
        Factory facotry = new Launcher().getFactory();
        facotry.getEnvironment().disableConsistencyChecks(); // setSelfChecks(true);
        /*
         * Set up Class
         */
        CtClass<Object> clazz = facotry.Core().createClass();
        clazz.setSimpleName(fileName);
        Set<ModifierKind> modifiers = new HashSet<>();
        modifiers.add(ModifierKind.PUBLIC);
        clazz.setModifiers(modifiers);
        CtConstructor constructor = facotry.Core().createConstructor();
        constructor.setSimpleName(fileName);
        constructor.setModifiers(modifiers);
        CtStatement consBody = facotry.Core().createCodeSnippetStatement();
        constructor.setBody(consBody);
        clazz.addConstructor(constructor);

        /*
         * add throwable
         */
        CtTypeReference<? extends Throwable> throwable = facotry.createTypeReference();
        throwable.setSimpleName("Throwable");
        Set<CtTypeReference<? extends Throwable>> throwsExp = new HashSet<>();
        throwsExp.add(throwable);

        /*
         * Set up equality check Method:
         */
        CtMethod<Object> equalityCheckMethod = facotry.createMethod();
        CtBlock<Object> methodBody = facotry.createBlock();
        List<CtStatement> reloads = new ArrayList<>();

        // CtStatement nullCheckStatement = facotry.Core().createCodeSnippetStatement();
        // ((CtCodeSnippetStatement)
        // nullCheckStatement).setValue(String.format("System.out.println(object1().equals(((%s)
        // (null))))", o1.getType().getQualifiedName().replace("$",".")));
        // reloads.add(nullCheckStatement);
        //
        // CtStatement nullCheckStatement2 =
        // facotry.Core().createCodeSnippetStatement();
        // ((CtCodeSnippetStatement)
        // nullCheckStatement2).setValue("System.out.println(object1().equals(object2()))");
        // reloads.add(nullCheckStatement2);
        //
        // CtStatement nullCheckStatement3 =
        // facotry.Core().createCodeSnippetStatement();
        // ((CtCodeSnippetStatement)
        // nullCheckStatement3).setValue("System.out.println(object1())");
        // reloads.add(nullCheckStatement3);
        //
        // CtStatement nullCheckStatement4 =
        // facotry.Core().createCodeSnippetStatement();
        // ((CtCodeSnippetStatement)
        // nullCheckStatement4).setValue("System.out.println(object2())");
        // reloads.add(nullCheckStatement4);

        CtStatement ctStatement = facotry.Core().createCodeSnippetStatement();
        ((CtCodeSnippetStatement) ctStatement)
                .setValue("return object1() == null ? object2() == null : object1().equals(object2())");
        reloads.add(ctStatement);
        methodBody.setStatements(reloads);
        equalityCheckMethod.setBody(methodBody);
        equalityCheckMethod.setSimpleName("equalitycheck");
        CtTypeReference<Object> returnValue = facotry.createTypeReference();
        returnValue.setSimpleName("boolean");
        equalityCheckMethod.setType(returnValue);
        equalityCheckMethod.setModifiers(modifiers);
        equalityCheckMethod.setThrownTypes(throwsExp);

        /*
         * Set up object1 Method
         */
        CtMethod<Object> object1Method = facotry.createMethod();
        CtBlock<Object> object1MethodBody = facotry.createBlock();
        List<CtStatement> sequences = new ArrayList<>();
        if (o1.isVar()) {
            for (CtElement sequence : o1.getInput()) {
                CtStatement statement = facotry.Core().createCodeSnippetStatement();
                ((CtCodeSnippetStatement) statement).setValue(sequence.toString());
                sequences.add(statement);
            }
        }
        CtStatement returnStatement1 = facotry.Core().createCodeSnippetStatement();
        ((CtCodeSnippetStatement) returnStatement1).setValue(String.format("return %s", o1.getVarName().toString()));
        sequences.add(returnStatement1);
        object1MethodBody.setStatements(sequences);
        object1Method.setBody(object1MethodBody);
        object1Method.setSimpleName("object1");
        CtTypeReference<Object> returnValue1 = facotry.createTypeReference();
        returnValue1.setSimpleName(o1.getType().getQualifiedName().replace("$", "."));
        object1Method.setType(returnValue1);
        object1Method.setModifiers(modifiers);
        object1Method.setThrownTypes(throwsExp);

        /*
         * Set up object2 Method
         */
        CtMethod<Object> object2Method = facotry.createMethod();
        CtBlock<Object> object2MethodBody = facotry.createBlock();
        List<CtStatement> otherSequences = new ArrayList<>();
        if (o2.isVar()) {
            for (CtElement otherSequence : o2.getInput()) {
                CtStatement otherStatement = facotry.Core().createCodeSnippetStatement();
                ((CtCodeSnippetStatement) otherStatement).setValue(otherSequence.toString());
                otherSequences.add(otherStatement);
            }
        }
        CtStatement returnStatement2 = facotry.Core().createCodeSnippetStatement();
        ((CtCodeSnippetStatement) returnStatement2).setValue(String.format("return %s", o2.getVarName().toString()));
        otherSequences.add(returnStatement2);
        object2MethodBody.setStatements(otherSequences);
        object2Method.setBody(object2MethodBody);
        object2Method.setSimpleName("object2");
        CtTypeReference<Object> returnValue2 = facotry.createTypeReference();
        returnValue2.setSimpleName(o2.getType().getQualifiedName().replace("$", "."));
        object2Method.setType(returnValue2);
        object2Method.setModifiers(modifiers);
        object2Method.setThrownTypes(throwsExp);

        clazz.addMethod(equalityCheckMethod);
        clazz.addMethod(object1Method);
        clazz.addMethod(object2Method);
        // System.out.println(clazz.toString());
        return clazz;
    }

    private List<Diagnostic<? extends JavaFileObject>> compileWholeClassFile(String fileName, String content) {
        List<String> options = new ArrayList<String>();
        options.add("-cp");
        options.add(Config.CLASS_PATH);
        options.add("-d");
        options.add(Config.BUILD_PATH + File.separator);
        // init source code file
        JavaFileObject file = new Source(fileName, JavaFileObject.Kind.SOURCE, content);
        // init compiler
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        // get compile information
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        // init task
        JavaCompiler.CompilationTask task = compiler.getTask(null, null, diagnostics, options, null,
                Arrays.asList(file));
        task.call();
        return diagnostics.getDiagnostics();
    }

    private String getImportAndPackage(String testFile) {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(testFile));
            String line = null;
            String lines = "";
            while ((line = bufferedReader.readLine()) != null)
                if (line.trim().startsWith("package ") || line.trim().startsWith("import "))
                    lines += line + "\n";
            return lines;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String packageNamePlusFileName(String fileName) {
        if (ASTParser.getPackageName().equals(""))
            return fileName;
        else
            return ASTParser.getPackageName() + "." + fileName;
    }

    private void initOutputFolderForClassLoader() {
        try {
            File output = new File(Config.BUILD_PATH);
            URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            Field ucp = URLClassLoader.class.getDeclaredField("ucp");
            ucp.setAccessible(true);
            URLClassPath urlClassPath = (URLClassPath) ucp.get(urlClassLoader);
            urlClassPath.addURL(output.toURI().toURL());
        } catch (Exception e) {
            System.out.println("Loading output folder for class failed!");
        }
    }

    private void writeFile(String fileName, String clazz) throws Exception {
        File sourceFile = new File(Config.OUTPUT_PATH + File.separator + fileName + ".java");
        sourceFile.createNewFile();
        FileWriter fileWriter = new FileWriter(sourceFile.getAbsoluteFile());
        PrintWriter printWriter = new PrintWriter(fileWriter);
        printWriter.print(clazz);
        printWriter.close();
    }

    private void deleteClassFile(String fileName) {
        File file = new File(Config.OUTPUT_PATH + File.separator + fileName + ".class");
        if (file.exists()) {
            if (!file.delete()) {
                System.out.println("Can not delete .class file");
            }
        }
    }

    private boolean checkCompileError(List<Diagnostic<? extends JavaFileObject>> errorMessage) {
        for (Diagnostic e : errorMessage) {
            if (e.getKind() == Diagnostic.Kind.ERROR) { // Nov. 2021: added due to ambiguity error
                return false;
            }
        }
        return true;
    }

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

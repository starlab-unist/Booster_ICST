package utils;

import spoon.Launcher;
import spoon.reflect.code.CtCodeSnippetStatement;
import spoon.reflect.code.CtStatement;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CtClassToStringWrapper {
    /**
     * This method wraps the statements in CtClass into CtCodeSnippetStatement
     * This should be done to avoid crash caused in CtClass.toString
     *
     * @param ctClass
     * @return
     */
    public static CtClass wrapWithCodeSnippet(CtClass ctClass) {
        return wrapWithCodeSnippet(ctClass.getMethods(), ctClass.getSimpleName());
    }

    public static CtClass wrapWithCodeSnippet(Set<CtMethod> methods, String className) {
        Factory factory = new Launcher().getFactory();
        CtClass clazz = factory.Core().createClass();
        clazz.setSimpleName(className);
        Set<ModifierKind> modifiers = new HashSet<>();
        modifiers.add(ModifierKind.PUBLIC);
        clazz.setModifiers(modifiers);

        for (CtMethod method : methods) {
            if (method == null) {
                continue;
            }
            CtMethod clone = method.clone();
            List<CtStatement> reloads = new ArrayList<>();
            for (CtStatement stmt : clone.getBody().getStatements()) {
                CtStatement ctStatement = factory.Core().createCodeSnippetStatement();
                ((CtCodeSnippetStatement) ctStatement).setValue(stmt.toString());
                reloads.add(ctStatement);
            }
            clone.getBody().setStatements(reloads);
            clazz.addMethod(clone);
        }

        return clazz;
    }

    /**
     * No wrapping with  CtCodeSnippetStatement.
     * Just create a CtClass given a set of methods
     *
     * @param methods
     * @param className
     * @return
     */
    public static CtClass createCtClass(Set<CtMethod> methods, String className) {
        Factory factory = new Launcher().getFactory();
        CtClass clazz = factory.Core().createClass();
        clazz.setSimpleName(className);
        Set<ModifierKind> modifiers = new HashSet<>();
        modifiers.add(ModifierKind.PUBLIC);
        clazz.setModifiers(modifiers);

        for (CtMethod method : methods) {
            if (method == null) {
                continue;
            }
            CtMethod clone = method.clone();
            clazz.addMethod(clone);
        }
        if (clazz.getMethods().size() > 0)
            return clazz;
        else
            return null;
    }
}

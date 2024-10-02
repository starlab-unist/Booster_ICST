package RegressionOracles.RegressionUtil;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 26/06/17
 */
public class Util {
    public static String getKey(CtMethod method, CtLocalVariable localVariable) {
        if (method.getParent(CtClass.class)==null){
            return method.getParent(CtInterface.class).getSimpleName() + "#" + method.getSimpleName() + "#" + localVariable.getSimpleName();
        } else {
            return method.getParent(CtClass.class).getSimpleName() + "#" + method.getSimpleName() + "#" + localVariable.getSimpleName();
        }
        
    }

    public static CtInvocation invok(CtMethod method, CtLocalVariable localVariable) {
        final CtExecutableReference reference = method.getReference();
        final CtVariableAccess variableRead = method.getFactory().createVariableRead(localVariable.getReference(), false);
        return method.getFactory().createInvocation(variableRead, reference);
    }

    public static List<CtMethod> getGetters(CtLocalVariable localVariable) {
        if (localVariable.getType().getTypeDeclaration() != null) {
            return ((Set<CtMethod<?>>) localVariable.getType().getTypeDeclaration().getMethods()).stream()
                    .filter(method -> method.getParameters().isEmpty() &&
                            method.getModifiers().contains(ModifierKind.PUBLIC) &&
                            method.getType() != localVariable.getFactory().Type().VOID_PRIMITIVE &&
                            (method.getType().isPrimitive() || method.getType().getSimpleName().equals("String")) &&
                            (method.getSimpleName().startsWith("get") ||
                                    method.getSimpleName().startsWith("is") ||
                                    method.getSimpleName().equals("size"))
                    ).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    public static String getKey(CtLocalVariable localVariable) {
        return localVariable.getSimpleName();
    }

    public static CtExpression assignment(Factory factory, CtLocalVariable localVariable) {
        return factory.Code().createVariableAssignment(
                localVariable.getReference(), false, localVariable.getDefaultExpression()).getAssigned();
    }
}

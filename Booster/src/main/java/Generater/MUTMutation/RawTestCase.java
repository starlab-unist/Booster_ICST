package Generater.MUTMutation;

import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtMethod;
import spoon.support.reflect.code.CtInvocationImpl;

import java.util.List;

/**
 * Raw test case data
 */
public class RawTestCase {
    private CtMethod originalTestCase;
    private List<Input> insertStmts;
    private List<CtExpression> arguments;
    private CtAbstractInvocation mut;

    public RawTestCase(CtMethod originalTestCase, List<Input> insertStmts, List<CtExpression> arguments, CtAbstractInvocation mut) {
        this.originalTestCase = originalTestCase;
        this.insertStmts = insertStmts;
        this.arguments = arguments;
        this.mut = mut;
    }

    public RawTestCase(List<Input> insertStmts, List<CtExpression> arguments, CtAbstractInvocation mut) {
        this.originalTestCase = null;
        this.insertStmts = insertStmts;
        this.arguments = arguments;
        this.mut = mut;
    }

    public CtMethod getOriginalTestCase() {
        return originalTestCase;
    }

    public void setOriginalTestCase(CtMethod originalTestCase) {
        this.originalTestCase = originalTestCase;
    }

    public List<Input> getInsertStmts() {
        return insertStmts;
    }

    public void setInsertStmts(List<Input> insertStmts) {
        this.insertStmts = insertStmts;
    }

    public List<CtExpression> getArguments() {
        return arguments.subList(1, arguments.size());  //skip arg[0] because it is for receiver object
    }

    public void setArguments(List<CtExpression> arguments) {
        this.arguments = arguments;
    }

    public CtAbstractInvocation getMut() {
        return mut;
    }

    public void setMut(CtInvocationImpl mut) {
        this.mut = mut;
    }

    @Override
    public String toString() {
        return this.mut.getExecutable().getSignature() + "\n\t"
                + this.arguments + "\n\t"
                + this.insertStmts;
    }
}

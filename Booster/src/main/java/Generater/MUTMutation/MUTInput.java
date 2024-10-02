package Generater.MUTMutation;

import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.reference.CtTypeReference;

import java.util.LinkedHashMap;
import java.util.List;

public class MUTInput {
    private CtAbstractInvocation mutInvocation;
    private List<CtTypeReference> argTypes;
    /**
     * Key: arg position of argTypes
     * Value: sets of existing inputs
     */
    private LinkedHashMap<Integer, List<Input>> inputs;

    private int upperBound;

    public MUTInput(CtAbstractInvocation mutInvocation, List<CtTypeReference> argTypes, LinkedHashMap<Integer, List<Input>> inputs) {
        this.mutInvocation = mutInvocation;
        this.argTypes = argTypes;
        this.inputs = inputs;
        int upperBound = 1;
        for (Integer pos : this.inputs.keySet()) {
            upperBound = upperBound * this.inputs.get(pos).size();
        }
        this.upperBound = upperBound;
    }

    public CtAbstractInvocation getMUTInvocation() {
        return mutInvocation;
    }

    public List<CtTypeReference> getArgTypes() {
        return argTypes;
    }

    public LinkedHashMap<Integer, List<Input>> getInputs() {
        return inputs;
    }

    public boolean equals(List<CtTypeReference> args) {
        if (args.size() != argTypes.size() - 1)
            return false;
        List<CtTypeReference> trueArgs = argTypes.subList(1, argTypes.size());
        for (int i = 0; i < trueArgs.size(); i++) {
            CtTypeReference arg = args.get(i);
            if (!arg.getSimpleName().equals(trueArgs.get(i).getSimpleName()))
                return false;
        }
        return true;
    }

    public int getUpperBound() {
        return upperBound;
    }
}

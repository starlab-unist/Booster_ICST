package RegressionOracles.RegressionUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Benjamin DANGLOT
 * benjamin.danglot@inria.fr
 * on 21/06/17
 */
public class Logger {

    public static Map<String, List<Assertion>> observations = new HashMap<String, List<Assertion>>(); //<Test name, Assertion>

    public static void observe(String testName, String key, Object object) {
        Assertion assertion = new Assertion(key, object);
        if (!observations.containsKey(testName))
            observations.put(testName, new ArrayList<>());
        observations.get(testName).add(assertion);
    }
}

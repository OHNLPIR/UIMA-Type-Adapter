package edu.mayo.bsi.uima.adapter.type;

import edu.mayo.bsi.uima.adapter.api.ITypeAdapter;
import edu.mayo.bsi.uima.adapter.api.IValueTransformer;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @deprecated use {@link DirectMappingTypeAdapter} instead
 * Provides a simple mapping of a source field within the source UIMA annotation to a target field within the destination<br>
 * Not thread safe: use external synchronization or a separate instance per thread
 * <br>
 */
public class DirectTypeAdapter implements ITypeAdapter {

    // An ordered sequence of method calls to retrieve and/or store information to an annotation
    private ArrayList<Method> sourceCallStack;
    private ArrayList<Method> destCallStack;
    private Class<? extends Annotation> sourceClass;
    private Class<? extends Annotation> targetClass;
    private ArrayList<IValueTransformer> transformers;

    public DirectTypeAdapter() {
        sourceCallStack = new ArrayList<>();
        destCallStack = new ArrayList<>();
        transformers = new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public boolean initialize(JSONObject config) {
        JSONObject src = config.getJSONObject("source");
        JSONObject dest = config.getJSONObject("dest");
        if (src == null || dest == null) {
            throw new IllegalArgumentException("Configuration has missing values, " +
                    "both source and dest must be defined!");
        }
        // Populate call Stacks
        processAnnotationDef(src, sourceCallStack);
        processAnnotationDef(dest, destCallStack);
        // Set source and target classes
        try {
            targetClass = (Class<? extends Annotation>) Class.forName(src.getString("class"));
            sourceClass = (Class<? extends Annotation>) Class.forName(dest.getString("class"));
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throwMissingConfig(src.getString("class"), null);
        }
        return true;
    }

    @Override
    public Class<? extends Annotation> getSourceFeatureStructureClass() {
        return sourceClass;
    }

    @Override
    public Class<? extends Annotation> getDestFeatureStructureClass() {
        return targetClass;
    }

    @Override
    public Collection<TOP> convert(JCas cas, TOP ann) {
//        Object val = getSourceAnnotationValue(ann);
//        return createAndStoreTargetAnnotation(cas, val, ann.getBegin(), ann.getEnd());
        return null; // TODO
    }

    /**
     * Parses a JSON definition for a source or target annotation to construct a call-stack for conversion tasks
     *
     * @param obj       The JSON object containing the configuration for the annotation
     * @param callStack a call stack to populate
     */
    @SuppressWarnings("unchecked")
    private void processAnnotationDef(JSONObject obj, List<Method> callStack) {
        String annClass = obj.getString("class");
        if (annClass == null) throwMissingConfig(null, null);
        JSONArray callStackDef = obj.getJSONArray("methods");
        if (callStackDef == null) throwMissingConfig(annClass, "methods");
        try {
            Class currStackObject = Class.forName(annClass);
            for (Object methodDef : callStackDef) {
                if (methodDef instanceof JSONObject) {
                    JSONObject methodObj = (JSONObject) methodDef;
                    String fieldName = methodObj.getString("field-name");
                    if (fieldName == null || fieldName.length() == 0)
                        throwMissingConfig(annClass, "methods.field-name");
                    String paramType = methodObj.getString("param-type"); // Not a required object
                    boolean isSet = paramType != null && paramType.length() > 0;
                    // field -> get/setField
                    String sub = fieldName.substring(1);
                    String first = fieldName.substring(0, 1);
                    String methodName = (isSet ? "set" : "get") + first.toUpperCase() + sub;
                    if (isSet) {
                        Class setClass = Class.forName(paramType);
                        Method m = currStackObject.getDeclaredMethod(methodName, setClass);
                        callStack.add(m);
                    } else {
                        Method m = currStackObject.getDeclaredMethod(methodName);
                        callStack.add(m);
                    }
                } else {
                    throw new IllegalArgumentException("Invalid method definition in " + annClass + " callstack definition");
                }
            }
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            e.printStackTrace();
            throwMissingConfig(annClass, null);
        }
    }

    /**
     * Retrieves the target value from a given source annotation by following the call stack defined in the config
     *
     * @param ann The source annotation
     * @return The retrieved value
     */
    private Object getSourceAnnotationValue(Annotation ann) {
        Object last = ann;
        for (int i = 0; i < sourceCallStack.size(); i++) { // Traverse in order
            Method m = sourceCallStack.get(i);
            try {
                last = m.invoke(ann);
            } catch (IllegalAccessException | InvocationTargetException e) {
                e.printStackTrace();
                throw new IllegalStateException("Error in retrieving source value", e);
            }
        }
        return last;
    }

    /**
     * Performs some sort of transformation on this value before storing. Nothing is done in the basic adapter but
     * extensions will override this method
     * @param value The value from the source annotation
     * @return The value to store into the target annotation, or null if it is no longer a valid value we wish to store
     */
    protected Object transform(Object value) {
        return value;
    }

    /**
     * Creates a target annotation using the given value and a callstack defined in the configuration
     *
     * @param cas   The cas to index the created annotation in
     * @param value The value to store
     * @param start The positional start in characters of this annotation
     * @param end   The positional end in characters of this annotation
     * @return The created annotation
     */
    @SuppressWarnings({"unchecked", "ConstantConditions"})
    private Annotation createAndStoreTargetAnnotation(JCas cas, Object value, int start, int end) {
        // Traverse bottom to top
        int i = destCallStack.size() - 1;
        Method m = destCallStack.get(i--);
        m.setAccessible(true);
        try {
            Constructor cons = m.getDeclaringClass().getConstructor(JCas.class, Integer.class, Integer.class);
            if (cons == null) {
                throw new IllegalArgumentException("Supplied class " + m.getDeclaringClass() + " is not a subtype of a UIMA annotation!");
            }
            Object o = cons.newInstance(cas, start, end);
            if (o instanceof Annotation) {
                Annotation ann = (Annotation) o;
                m.invoke(ann, value);
                ann.addToIndexes();
            }
            // First object created and populated, now do the rest of the stack
            for (; i >= 0 && (m = destCallStack.get(i)) != null; i--) {
                cons = m.getDeclaringClass().getConstructor(JCas.class, Integer.class, Integer.class);
                if (cons == null) {
                    throw new IllegalArgumentException("Supplied class " + m.getDeclaringClass() + " is not a subtype of a UIMA annotation!");
                }
                Object temp = cons.newInstance(cas, start, end);
                if (temp instanceof Annotation) {
                    Annotation ann = (Annotation) temp;
                    m.invoke(temp, o);
                    ann.addToIndexes();
                }
                o = temp;
            }
            if (!(o instanceof Annotation)) {
                throw new IllegalArgumentException("Expected " + o.getClass().getName() + " as adapter destination type!");
            }
            return (Annotation) o;

        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            throw new IllegalStateException("Error during conversion of " + value.getClass().getName() + ":" + value.toString());
        }
    }

    @SuppressWarnings("ConstantConditions")
    private void throwMissingConfig(String className, String value) {
        throw new IllegalArgumentException(className == null ? "No class defined for target annotation!"
                : value + " is missing for " + className + " class definition");
    }
}

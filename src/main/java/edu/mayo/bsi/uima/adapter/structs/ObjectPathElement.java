package edu.mayo.bsi.uima.adapter.structs;

import edu.mayo.bsi.uima.adapter.Util;
import edu.mayo.bsi.uima.adapter.ae.TypeAdapterAnnotator;
import edu.mayo.bsi.uima.adapter.api.IPathElement;
import edu.mayo.bsi.uima.adapter.api.exceptions.AdapterFailureException;
import edu.mayo.bsi.uima.adapter.api.exceptions.IllegalConfigurationException;
import org.apache.uima.UIMAFramework;
import org.apache.uima.cas.CommonArrayFS;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.util.Level;
import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;


public class ObjectPathElement implements IPathElement {

    private Class<?> type;
    private String[] currPath;
    private Map<String, IPathElement> children;
    private Map<String, Class<?>> childType;

    private Map<String, Method> childAccessors;
    private Map<String, Method> childSetters;

    private Map<String, Constructor<?>> childConstructors;


    public ObjectPathElement(String[] currPath, JSONObject config) {
        this.children = new HashMap<>();
        this.childType = new HashMap<>();
        this.childAccessors = new HashMap<>();
        this.childSetters = new HashMap<>();
        this.childConstructors = new HashMap<>();
        this.currPath = currPath;
        try {
            this.type = Class.forName(config.getString("type"));
        } catch (ClassNotFoundException e) {
            throw new AssertionError("Reached unreachable code with missing class!", e); // Should not be reached ever
        }
        JSONArray fieldsArr = config.optJSONArray("fields");
        if (fieldsArr == null) {
            return;
        }
        for (Object o : fieldsArr) {
            try {
                if (!(o instanceof JSONObject)) {
                    UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Skipping invalid configuration in " + o + " at " + Util.constructPath(currPath));
                    continue;
                }
                JSONObject fieldObj = (JSONObject) o;
                Class<?> type = Class.forName(fieldObj.getString("type"));
                String name = fieldObj.getString("name");
                String cName = name.substring(0, 1).toUpperCase() + name.substring(1);
                Method childAccessor = type.getDeclaredMethod("get" + cName);
                childAccessor.setAccessible(true);
                Method childSetter = type.getDeclaredMethod("set" + cName, type);
                childSetter.setAccessible(true);
                Constructor<?> constructor;
                boolean isArray = false;
                if (Annotation.class.isAssignableFrom(type)) {
                    constructor = type.getDeclaredConstructor(JCas.class, Integer.class, Integer.class);
                } else if (CommonArrayFS.class.isAssignableFrom(type)) {
                    constructor = type.getDeclaredConstructor(JCas.class, Integer.class);
                    isArray = true;
                } else if (TOP.class.isAssignableFrom(type)) {
                    constructor = type.getDeclaredConstructor(JCas.class);
                } else {
                    constructor = null;
                }
                if (constructor != null) {
                    constructor.setAccessible(true);
                }
                String[] newPath = Arrays.copyOf(this.currPath, currPath.length + 1);
                newPath[this.currPath.length] = name;
                IPathElement childElement;
                if (isArray) {
                    childElement = new ArrayPathElement(newPath, fieldObj);
                } else {
                    childElement = new ObjectPathElement(newPath, fieldObj);
                }
                if (this.children.put(name, childElement) != null) {
                    UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Duplicate child element definition " + name + " at " + Util.constructPath(currPath));
                }
                if (this.childType.put(name, type) != null) {
                    UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Duplicate child type definition " + name + " at " + Util.constructPath(currPath));
                }
                if (this.childAccessors.put(name, childAccessor) != null) {
                    UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Duplicate child get definition " + name + " at " + Util.constructPath(currPath));
                }
                if (this.childSetters.put(name, childSetter) != null) {
                    UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Duplicate child set definition " + name + " at " + Util.constructPath(currPath));
                }
                if (this.childConstructors.put(name, constructor) != null) {
                    UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Duplicate child constructor definition " + name + " at " + Util.constructPath(currPath));
                }
            } catch (Exception e) {
                UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Skipping invalid configuration in " + o + " at " + Util.constructPath(currPath));
                e.printStackTrace();
            }
        }
    }

    @Override
    public void store(JCas cas, Deque<String> pathStack, Object curr, Object value, int begin, int end) throws IllegalConfigurationException, AdapterFailureException {
        if (curr instanceof Annotation) {
            Util.expand((Annotation) curr, begin, end);
        }
        String next = pathStack.pop();
        if (!children.containsKey(next)) {
            throw new IllegalConfigurationException("Field " + next + " as a field of " + Util.constructPath(currPath) + " was not defined in configuration!");
        }
        if (pathStack.isEmpty()) { // End of the path, store value
            if (!childType.get(next).getClass().isAssignableFrom(value.getClass())) {
                throw new AdapterFailureException("Field " + next + " as a field of " + Util.constructPath(currPath) + " expects " + childType.get(next) + " but received " + value.getClass());
            }
            try {
                childSetters.get(next).invoke(curr, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new AdapterFailureException("Failure assigning value " + value + " to " + Util.constructPath(currPath) + " " + next, e);
            }
        } else {
            try {
                Object obj = childAccessors.get(next).invoke(curr);
                Class<?> childType = this.childType.get(next);
                if (obj == null) {
                    if (Annotation.class.isAssignableFrom(childType)) {
                        obj = childConstructors.get(next).newInstance(cas, begin, end);
                    } else if (CommonArrayFS.class.isAssignableFrom(childType)) {
                        if (!(children.get(next) instanceof ArrayPathElement)) {
                            throw new IllegalConfigurationException("An array was parsed as an object at " + Util.constructPath(currPath));
                        }
                        int len = ((ArrayPathElement) children.get(next)).getLength();
                        obj = childConstructors.get(next).newInstance(cas, len);
                    } else if (TOP.class.isAssignableFrom(childType)) {
                        obj = childConstructors.get(next).newInstance(cas);
                    } else {
                        throw new IllegalConfigurationException("Tried to store an object that is not an UIMA type " +
                                "as an UIMA type at " + Util.constructPath(currPath));
                    }
                    ((TOP)obj).addToIndexes();
                    childSetters.get(next).invoke(curr, obj);
                }
                children.get(next).store(cas, pathStack, obj, value, begin, end);
            } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
                throw new AdapterFailureException("Failure assigning value " + value + " to " + Util.constructPath(currPath) + " " + next, e);
            }
        }
    }

    @Override
    public Object[] get(Deque<String> pathStack, Object obj, Integer begin, Integer end) throws AdapterFailureException {
        if (obj == null) {
            return new Object[] {null, null, null};
        }
        if (pathStack.isEmpty()) {
            return new Object[] {obj, begin, end};
        }
        String next = pathStack.pop();
        if (!childAccessors.containsKey(next)) {
            throw new AdapterFailureException("No accessor for " + next + " is defined at " + Util.constructPath(currPath));
        }
        try {
            Object nextObj = childAccessors.get(next).invoke(obj);
            if (nextObj == null) {
                return new Object[] {null, null, null};
            }
            if (nextObj instanceof Annotation) {
                begin = ((Annotation) nextObj).getBegin();
                end = ((Annotation) nextObj).getEnd();
            }
            return children.get(next).get(pathStack, nextObj, begin, end);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AdapterFailureException("Failure getting element at " + Util.constructPath(currPath), e);
        }
    }


    @Override
    public boolean isArray() {
        return false;
    }

    public Class<?> getType() {
        return type;
    }
}

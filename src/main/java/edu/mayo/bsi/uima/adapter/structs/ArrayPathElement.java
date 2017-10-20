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
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Deque;
import java.util.Stack;

// TODO: support dynamic array resizing? may not be possible with current impl at all
public class ArrayPathElement implements IPathElement {
    private String[] currPath;
    private String name;
    private Class<?> type;
    private int length;
    private Class<?> elementType;
    private IPathElement elementPathElement;
    private Constructor<?> elementConstructor;
    private Method getElement;
    private Method setElement;

    public ArrayPathElement(String[] path, JSONObject config) {
        currPath = path;
        name = config.getString("name");
        try {
            type = Class.forName(config.getString("type"));
            elementType = Class.forName(config.getString("element-type"));
        } catch (Exception e) { // Should never reach this point except in root element
            UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "Skipping invalid configuration at " + Util.constructPath(path));
            e.printStackTrace();
            return;
        }
        length = config.getInt("size");
        JSONObject o = config.getJSONObject("elementDef");
        try {
            String[] newPath = Arrays.copyOf(this.currPath, currPath.length + 1);
            newPath[this.currPath.length] = name;
            boolean isArray = false;
            if (Annotation.class.isAssignableFrom(elementType)) {
                elementConstructor = elementType.getDeclaredConstructor(JCas.class, Integer.class, Integer.class);
            } else if (CommonArrayFS.class.isAssignableFrom(elementType)) {
                elementConstructor = elementType.getDeclaredConstructor(JCas.class, Integer.class);
                isArray = true;
            } else if (TOP.class.isAssignableFrom(elementType)) {
                elementConstructor = elementType.getDeclaredConstructor(JCas.class);
            } else {
                elementConstructor = null;
            }
            if (isArray) {
                elementPathElement = new ArrayPathElement(newPath, o);
            } else {
                elementPathElement = new ObjectPathElement(newPath, o);
            }
            if (elementConstructor != null) {
                elementConstructor.setAccessible(true);
            }
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Could not get element constructors in array definition at " + Util.constructPath(path));
        }
        try {
            getElement = type.getDeclaredMethod("get", Integer.class); // Get by index
            setElement = type.getDeclaredMethod("set", Integer.class, elementType);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException("Could not initialize element set and get for array type " + type.getName() + " with element type " + elementType.getName(), e);
        }
    }

    @Override
    public void store(JCas cas, Deque<String> pathStack, Object curr, Object value, int begin, int end) throws
            IllegalConfigurationException, AdapterFailureException {
        if (!(curr instanceof CommonArrayFS)) {
            throw new AdapterFailureException("Tried to parse an object as an array at " + Util.constructPath(currPath));
        }
        if (pathStack.isEmpty()) {
            throw new AdapterFailureException("Empty Stack!");
        }
        int idx;
        String next = pathStack.pop();
        try {
            idx = Integer.valueOf(next);
        } catch (NumberFormatException e) {
            idx = 0;
            UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "No array index in path pointing to array, defaulting to 0 at " + Util.constructPath(currPath));
            pathStack.push(next);
        }
        try {
            Object obj = getElement.invoke(curr, idx);
            if (obj == null) {
                if (Annotation.class.isAssignableFrom(elementType)) {
                    obj = elementConstructor.newInstance(cas, begin, end);
                } else if (CommonArrayFS.class.isAssignableFrom(elementType)) {
                    if (!(elementPathElement instanceof ArrayPathElement)) {
                        throw new IllegalConfigurationException("An array was parsed as an object at " + Util.constructPath(currPath));
                    }
                    int len = ((ArrayPathElement) elementPathElement).getLength();
                    obj = elementConstructor.newInstance(cas, len);
                } else if (TOP.class.isAssignableFrom(elementType)) {
                    obj = elementConstructor.newInstance(cas);
                } else {
                    throw new IllegalConfigurationException("Tried to store an object that is not an UIMA type " +
                            "as an UIMA type at " + Util.constructPath(currPath));
                }
                ((TOP)obj).addToIndexes();
                setElement.invoke(curr, idx, obj);
            }
            elementPathElement.store(cas, pathStack, obj, value, begin, end);
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new AdapterFailureException("Tried to insert into an array at index " + idx + " but array only had length " + getLength());
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new AdapterFailureException("Tried to insert into an array at index " + idx + " but a failure occurred", e);
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
        int idx;
        String next = pathStack.pop();
        try {
            idx = Integer.valueOf(next);
        } catch (NumberFormatException e) {
            idx = 0;
            UIMAFramework.getLogger(TypeAdapterAnnotator.class).log(Level.WARNING, "No array index in path pointing to array, defaulting to 0 at " + Util.constructPath(currPath));
            pathStack.push(next);
        }
        try {
            Object nextObj = getElement.invoke(obj, idx);
            if (nextObj instanceof Annotation) {
                begin = ((Annotation) nextObj).getBegin();
                end = ((Annotation) nextObj).getEnd();
            }
            return elementPathElement.get(pathStack, nextObj, begin, end);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new AdapterFailureException("Failure getting element " + idx + " at " + Util.constructPath(currPath), e);
        }
    }

    @Override
    public boolean isArray() {
        return true;
    }

    int getLength() {
        return length;
    }
}

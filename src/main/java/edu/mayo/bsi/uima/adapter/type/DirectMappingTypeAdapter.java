package edu.mayo.bsi.uima.adapter.type;

import edu.mayo.bsi.uima.adapter.Util;
import edu.mayo.bsi.uima.adapter.api.IPathElement;
import edu.mayo.bsi.uima.adapter.api.ITypeAdapter;
import edu.mayo.bsi.uima.adapter.api.IValueTransformer;
import edu.mayo.bsi.uima.adapter.api.exceptions.AdapterFailureException;
import edu.mayo.bsi.uima.adapter.api.exceptions.IllegalConfigurationException;
import edu.mayo.bsi.uima.adapter.structs.ObjectPathElement;
import org.apache.uima.cas.CommonArrayFS;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

/**
 * The basic {@link ITypeAdapter} that uses path elements to perform mapping and transformers to modify evaluated output
 */
public class DirectMappingTypeAdapter implements ITypeAdapter {

    private IPathElement sourcePathDef;
    private IPathElement targetPathDef;
    private Collection<Mapping> mappings;
    private Class<? extends TOP> sourceClass;
    private Class<? extends TOP> destClass;
    private Constructor<? extends TOP> blankDestConstructor;

    @Override
    public Class<? extends TOP> getSourceFeatureStructureClass() {
        return sourceClass;
    }

    @Override
    public Class<? extends TOP> getDestFeatureStructureClass() {
        return destClass;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean initialize(JSONObject config) {
        try {
            sourcePathDef = new ObjectPathElement(new String[0], config.getJSONObject("sourceDef"));
            targetPathDef = new ObjectPathElement(new String[0], config.getJSONObject("targetDef"));
            try {
                sourceClass = (Class<? extends TOP>) ((ObjectPathElement) sourcePathDef).getType();
                destClass = (Class<? extends TOP>) ((ObjectPathElement) targetPathDef).getType();
            } catch (ClassCastException e) {
                throw new RuntimeException("Cannot do non-uima type conversions!");
            }

            blankDestConstructor = destClass.getDeclaredConstructor(JCas.class);
            mappings = new LinkedList<>();
            for (Object mappingObj : config.getJSONArray("mappings")) {
                if (!(mappingObj instanceof JSONObject)) {
                    throw new RuntimeException(); // TODO
                }
                JSONObject mappingConfig = (JSONObject)mappingObj;
                LinkedList<IValueTransformer> transformers = new LinkedList<>();
                for (Object transformerObj : config.getJSONArray("transformers")) {
                    if (!(transformerObj instanceof JSONObject)) {
                        throw new RuntimeException(); // TODO
                    }
                    Class<? extends IValueTransformer> transformerClazz =
                            (Class<? extends IValueTransformer>) Class.forName(((JSONObject) transformerObj).getString("type"));
                    transformers.addLast(transformerClazz.getDeclaredConstructor().newInstance());
                }
                String src = mappingConfig.getString("src");
                String dest = mappingConfig.getString("dest");
                mappings.add(new Mapping(src, dest, transformers));
            }
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
            e.printStackTrace(); // TODO
            return false;
        }
        return false; // TODO
    }

    @SuppressWarnings("unchecked")
    @Override
    public Collection<TOP> convert(JCas cas, TOP ann) throws AdapterFailureException {
        Deque<String> stack;
        TOP ret;
        try {
            ret = constructBlankInstance(cas);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new AdapterFailureException("Error in adapter creating target type", e);
        }
        for (Mapping m : mappings) {
            stack = new LinkedList<>();
            for (String s : m.srcArr) {
                stack.addLast(s);
            }
            Object[] value = sourcePathDef.get(stack, ann, null, null);
            if (value[0] == null) {
                continue;
            }
            for (IValueTransformer transformer : m.transformers) {
                value[0] = transformer.transform(value);
                if (value[0] == null) {
                    break;
                }
            }
            if (value[0] == null) {
                continue;
            }
            stack = new LinkedList<>();
            for (String s : m.destArr) {
                stack.addLast(s);
            }
            try {
                targetPathDef.store(cas, stack, value[0], ret, (int) value[1], (int) value[2]);
            } catch (IllegalConfigurationException e) {
                throw new AdapterFailureException("Error in adapter storing target type", e);
            }
        }
        return Collections.singleton(ret);
    }

    private TOP constructBlankInstance(JCas cas) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        if (CommonArrayFS.class.isAssignableFrom(getDestFeatureStructureClass())) {
            throw new IllegalArgumentException("Conversion of static object to array not supported by MappingTypeAdapters");
        } else {
            TOP obj = blankDestConstructor.newInstance(cas);
            obj.addToIndexes();
            return obj;
        }
    }


}

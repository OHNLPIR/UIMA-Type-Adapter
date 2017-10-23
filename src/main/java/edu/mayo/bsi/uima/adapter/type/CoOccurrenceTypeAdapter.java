package edu.mayo.bsi.uima.adapter.type;

import edu.mayo.bsi.uima.adapter.api.IPathElement;
import edu.mayo.bsi.uima.adapter.api.ITypeAdapter;
import edu.mayo.bsi.uima.adapter.api.exceptions.AdapterFailureException;
import edu.mayo.bsi.uima.adapter.api.exceptions.IllegalConfigurationException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.jcas.tcas.Annotation;
import org.json.JSONObject;
import sun.security.util.Cache;

import java.util.Collection;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

/**
 * Maps based on source annotations that occur within the same covering annotation as the destination class.
 * This type adapter is meant to be run after another type adapter and/or after both source and destination
 * annotations already exist
 */

public class CoOccurrenceTypeAdapter implements ITypeAdapter {

    private Class<? extends Annotation> COVERING_CLAZZ;
    private Class<? extends Annotation> SOURCE_CLAZZ;
    private Class<? extends Annotation> DEST_CLAZZ;

    private Cache<JCas, Map<? extends Annotation, Collection<? extends Annotation>>> COVERING_CACHE;
    private Cache<JCas, Map<? extends Annotation, Collection<? extends Annotation>>> COVERED_CACHE;

    private IPathElement sourcePathDef;
    private IPathElement targetPathDef;

    Collection<Mapping> mappings;

    @Override
    public Class<? extends TOP> getSourceFeatureStructureClass() {
        return SOURCE_CLAZZ;
    }

    @Override
    public Class<? extends TOP> getDestFeatureStructureClass() {
        return DEST_CLAZZ;
    }

    @Override
    public boolean initialize(JSONObject config) {
        return false;
    }

    @Override
    public Collection<TOP> convert(JCas cas, TOP fs) throws AdapterFailureException {
        if (!(fs instanceof Annotation)) {
            throw new AdapterFailureException("Co-OccurrenceTypeAdapter somehow ended up with a non-annotation source (should have been caught on config load)");
        }
        Collection<? extends Annotation> coveringInstances = COVERING_CACHE.get(cas).get(fs); // TODO nullcheck
        Deque<String> path;
        Collection<TOP> ret = new LinkedList<>();
        for (Annotation ann : coveringInstances) {
            Collection<? extends Annotation> coveredTargetInstances = COVERED_CACHE.get(cas).get(ann);
            for (Annotation target : coveredTargetInstances) {
                for (Mapping m : mappings) {
                    path = new LinkedList<>();
                    for (String s : m.srcArr) {
                        path.addLast(s);
                    }
                    Object[] value = sourcePathDef.get(path, fs, null, null);
                    path = new LinkedList<>();
                    for (String s : m.destArr) {
                        path.addLast(s);
                    }
                    try {
                        targetPathDef.store(cas, path, target, value[0], (int) value[1], (int) value[2]);
                    } catch (IllegalConfigurationException e) {
                        throw new AdapterFailureException("Failure during value storage", e);
                    }
                }
                ret.add(ann);
            }
        }
        return ret;
    }
}

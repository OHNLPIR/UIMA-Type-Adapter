package edu.mayo.bsi.uima.adapter.ae;

import edu.mayo.bsi.uima.adapter.api.ITypeAdapter;
import edu.mayo.bsi.uima.adapter.api.exceptions.AdapterFailureException;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;

import java.util.Collection;
import java.util.Map;

public class TypeAdapterAnnotator extends JCasAnnotator_ImplBase {

    public Map<Class<? extends TOP>, Collection<ITypeAdapter>> adapterMap;

    @Override
    public void process(JCas jCas) throws AnalysisEngineProcessException {
        for (Map.Entry<Class<? extends TOP>, Collection<ITypeAdapter>> e : adapterMap.entrySet()) {
            for (TOP ann : JCasUtil.select(jCas, e.getKey())) {
                for (ITypeAdapter adapter : e.getValue()) {
                    try {
                        adapter.convert(jCas, ann);
                    } catch (AdapterFailureException e1) {
                        e1.printStackTrace();
                    }
                }
            }
        }
    }
}

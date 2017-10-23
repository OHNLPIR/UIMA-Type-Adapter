package edu.mayo.bsi.uima.adapter.api;

        import edu.mayo.bsi.uima.adapter.api.exceptions.AdapterFailureException;
        import org.apache.uima.jcas.JCas;
        import org.apache.uima.jcas.cas.TOP;
        import org.json.JSONObject;

        import java.util.Collection;

/**
 * Implementations of this type define a conversion from one UIMA feature structure to another<br>
 */
public interface ITypeAdapter {

    /**
     * @return The source feature structure that this type adapter accepts as input
     */
    Class<? extends TOP> getSourceFeatureStructureClass();

    /**
     * @return The destination feature structure that this type adapter aims to convert into
     */
    Class<? extends TOP> getDestFeatureStructureClass();

    /**
     * Initializes the given type adapter with the parameter settings
     *
     * @param config The settings to use as applied to this adapter
     * @return Whether initialization was successful
     */
    boolean initialize(JSONObject config);

    /**
     * Converts a given annotation to the target type and stores it within the cas index. <br>
     *
     * @param cas The cas in which to store the converted feature structure
     * @param fs  The feature structure to convert
     * @return The converted annotation(s), can be either single or multiple element depending if this is a n:1 or n:m conversion,
     * or null if conversion failed
     */
    Collection<TOP> convert(JCas cas, TOP fs) throws AdapterFailureException;
}

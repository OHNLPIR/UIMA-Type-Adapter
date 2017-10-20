package edu.mayo.bsi.uima.adapter.api;

import org.json.JSONObject;

/**
 * A transformer that manipulates source data to output data desired
 *
 * @param <SRC_TYPE>  The input data type
 * @param <DEST_TYPE> The output data type
 */
public interface IValueTransformer<SRC_TYPE, DEST_TYPE> {

    /**
     * Initializes transformer with settings from config
     *
     * @param config A JSON configuration object
     */
    void loadConfig(JSONObject config);

    /**
     * Performs a transformation on the given
     *
     * @param value The input value to take
     * @return The transformed value (can be null)
     */
    DEST_TYPE transform(SRC_TYPE value);
}

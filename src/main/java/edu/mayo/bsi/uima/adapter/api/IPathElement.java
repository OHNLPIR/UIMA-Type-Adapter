package edu.mayo.bsi.uima.adapter.api;

import edu.mayo.bsi.uima.adapter.api.exceptions.AdapterFailureException;
import edu.mayo.bsi.uima.adapter.api.exceptions.IllegalConfigurationException;
import org.apache.uima.jcas.JCas;
import org.json.JSONObject;

import java.util.Deque;

/**
 * Represents part of a path of a UIMA TOP and provides methods for accessing and storing values using a given path
 */
public interface IPathElement {

    /**
     * Stores the given value to the given path in this Path Element
     *
     * @param cas       The UIMA JCAS to store the value and created annotations into
     * @param pathStack The stack of remaining children to access
     * @param curr      The current object this path element is a child of (can be null) TODO handle this
     * @param value     The value to store
     * @param begin     The starting index of the value being stored, ignored if not applicable
     *                  for type and there are no parents that are an annotation
     * @param end       The ending index of the value being stored, ignored if not applicable
     *                  for type and there are no parents that are an annotation
     * @throws IllegalConfigurationException If a configuration error prevents proper conversion
     * @throws AdapterFailureException       If an error occurs during the attempt to store data
     */
    void store(JCas cas, Deque<String> pathStack, Object curr, Object value, int begin, int end) throws IllegalConfigurationException, AdapterFailureException;

    /**
     * Gets a value associated with the provided object at a certain point within the provided path
     *
     * @param pathStack The path to follow from the current object to get to the required value
     * @param obj       The object containing the required value at the path
     * @param begin     The beginning index of the parent element in this path tree, if present and/or known, null otherwise
     * @param end       The end index of the parent element in this path tree, if present and/or known, null otherwise
     * @return A 3 element array consisting of {value, begin_idx, end_idx}, elements may be null
     * @throws AdapterFailureException If a failure occurs during retrieval
     */
    Object[] get(Deque<String> pathStack, Object obj, Integer begin, Integer end) throws AdapterFailureException;

    /**
     * @return Whether the path represented by the current path element is an array
     */
    boolean isArray();

}

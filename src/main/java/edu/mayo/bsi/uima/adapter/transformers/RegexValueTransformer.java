package edu.mayo.bsi.uima.adapter.transformers;

import edu.mayo.bsi.uima.adapter.api.IValueTransformer;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A type adapter that filters a specific group using regex
 */
public class RegexValueTransformer implements IValueTransformer<String, String> {

    private Pattern pattern;
    private int group;


    @Override
    public void loadConfig(JSONObject config) {
        // TODO
    }

    @Override
    public String transform(String value) {
        Matcher m = pattern.matcher(value);
        if (m.find()) {
            return m.group(group);
        } else {
            return null; // TODO error checking
        }
    }
}

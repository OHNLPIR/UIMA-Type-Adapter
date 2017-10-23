package edu.mayo.bsi.uima.adapter.type;

import edu.mayo.bsi.uima.adapter.Util;
import edu.mayo.bsi.uima.adapter.api.IValueTransformer;

import java.util.List;

class Mapping {
    String[] srcArr;
    String[] destArr;
    List<IValueTransformer> transformers;

    Mapping(String src, String dest, List<IValueTransformer> transformers) {
        this.srcArr = Util.escapedStringSplit(src, '.', '\\');
        this.destArr = Util.escapedStringSplit(dest, '.', '\\');;
        this.transformers = transformers;
    }
}
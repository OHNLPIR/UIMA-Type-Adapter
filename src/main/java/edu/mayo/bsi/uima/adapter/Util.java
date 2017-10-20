package edu.mayo.bsi.uima.adapter;

import org.apache.uima.jcas.tcas.Annotation;

import java.util.LinkedList;
import java.util.List;

public class Util {
    /**
     * Expands boundries to contain a given span
     * @param ann The annotation to expand
     * @param begin start of span
     * @param end end of span
     */
    public static void expand(Annotation ann, int begin, int end) {
        if (ann.getBegin() != 0 || ann.getEnd() != 0) {
            ann.setBegin(Math.min(begin, ann.getBegin()));
            ann.setEnd(Math.max(end, ann.getEnd()));
        } else {
            ann.setBegin(begin);
            ann.setEnd(end);
        }
    }

    /**
     * Splits an input string into a string array, separated by the provided SPLIT_SEPARATOR, but only if
     * it is not preceded by the supplied ESCAPE_CHAR
     *
     * @param raw             The raw text to split
     * @param SPLIT_SEPARATOR The field separator char defined for this message
     * @param ESCAPE_CHAR     The escape character defined for this message in the message header segment
     * @return An array of strings denoting the individual fields of the HL7 Message Segment, or an empty array if raw
     * is null
     */
    public static String[] escapedStringSplit(String raw, char SPLIT_SEPARATOR, char ESCAPE_CHAR) {
        if (raw == null) {
            return new String[0];
        }
        List<String> buf = new LinkedList<>();
        char[] rawCharBuf = raw.toCharArray();
        int currIdx = 0;
        boolean escaped = false;
        StringBuilder nextBuilder = new StringBuilder();
        while (currIdx < rawCharBuf.length) {
            char next = rawCharBuf[currIdx];
            if (rawCharBuf[currIdx] == ESCAPE_CHAR) {
                if (!escaped) { // Set escape state to true
                    escaped = true;
                } else { // Exit escape state, double escape so append an escape char
                    escaped = false;
                    nextBuilder.append(ESCAPE_CHAR);
                }
            } else {
                if (escaped) { // Currently in escape state
                    if (next == SPLIT_SEPARATOR) { // Is an escaped separator, normalize and append only escaped char
                        nextBuilder.append(SPLIT_SEPARATOR);
                    } else { // Not an escaped separator, ignore and exit escaped state
                        nextBuilder.append(ESCAPE_CHAR).append(next);
                    }
                    escaped = false;
                } else { // Not in an escape state
                    if (next == SPLIT_SEPARATOR) { // Split here
                        String nextString = nextBuilder.toString();
                        if (nextString.length() == 0) {
                            nextString = null;
                        }
                        buf.add(nextString);
                        nextBuilder = new StringBuilder();
                    } else {
                        nextBuilder.append(next);
                    }
                }
            }
            currIdx++;
        }
        // Append tail after last separator
        if (escaped) {
            nextBuilder.append(ESCAPE_CHAR); // Trailing escape
        }
        String nextString = nextBuilder.toString();
        if (nextString.length() == 0) {
            nextString = null;
        }
        buf.add(nextString);
        return buf.toArray(new String[buf.size()]);
    }

    /**
     * Utility method used to construct a human readable path definition
     */
    public static String constructPath(String[] path) {
        StringBuilder currPath = new StringBuilder();
        boolean flag = false;
        for (String s : path) {
            if (flag) {
                currPath.append(".");
            } else {
                flag = true;
            }
            // Escape path again if we are going to send to user
            currPath.append(s.replace("\\", "\\\\").replace(".", "\\."));
        }
        return currPath.toString();
    }
}

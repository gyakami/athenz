//
// This file generated by rdl 1.5.2. Do not modify!
//

package com.yahoo.athenz.zms;
import com.yahoo.rdl.*;

//
// AssertionConditionOperator - Allowed operators for assertion conditions
//
public enum AssertionConditionOperator {
    EQUALS;

    public static AssertionConditionOperator fromString(String v) {
        for (AssertionConditionOperator e : values()) {
            if (e.toString().equals(v)) {
                return e;
            }
        }
        throw new IllegalArgumentException("Invalid string representation for AssertionConditionOperator: " + v);
    }
}
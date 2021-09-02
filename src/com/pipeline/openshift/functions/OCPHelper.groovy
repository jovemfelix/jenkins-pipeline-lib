#!/usr/bin/env groovy
package com.pipeline.openshift.functions

class OCPHelper implements Serializable {
    static boolean isEmptyOrNull(String s) {
        return s == null || s.trim().length() == 0
    }

    static boolean isNotEmptyOrNull(String s) {
        return !isEmptyOrNull(s)
    }
}
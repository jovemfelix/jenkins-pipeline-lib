#!/usr/bin/env groovy

def errors(def s ){
    def stage = "[${env.STAGE_NAME}]"
    if (stage == "[null]") {
        stage = ""
    }
    error "${stage}${s}"
}

def call(def s) {
    return errors(s)
}
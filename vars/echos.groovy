#!/usr/bin/env groovy

def echos(def s ){
    def stage = "[${env.STAGE_NAME}]"
    if (stage == "[null]") {
        stage = ""
    } 
    println "${stage}${s}"
}

def call(def s) {
    return echos(s)
}
#!/usr/bin/env groovy

def setObjectLabels(Map obj) {
    openshift.withCluster() {
        openshift.withProject("${obj.namespace}") {
            openshift.raw("label", "${obj.type}", "${obj.name}", "app=${obj.name}", "--overwrite")
            openshift.raw("label", "${obj.type}", "${obj.name}", "imagem=${obj.imagem}", "--overwrite")
            openshift.raw("label", "${obj.type}", "${obj.name}", "ambiente=${obj.ambiente}", "--overwrite")
        }
    }
}

def call(Map obj) {
    setObjectLabels(obj)
}

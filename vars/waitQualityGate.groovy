#!/usr/bin/env groovy

def waitQualityGate() {
    def sonar = getSonarQubeURL()
    def api = ""
    echos "[waitQualityGate] IMAGEM=${IMAGEM}"
    if (env.IMAGEM == "angular" || env.IMAGEM == "php") {
        api = "${sonar}/api/qualitygates/project_status?projectKey=${env.APP_NAME}"
    } else {
        def groupId = getGroupIdFromPom()
        def artifactId = getArtifactIdFromPom()
        api = "${sonar}/api/qualitygates/project_status?projectKey=${groupId}:${artifactId}"
    }
    echos "[waitQualityGate] api=${api}"
    def url = new URL(api)
    def result = new groovy.json.JsonSlurper().parse(url.newReader())
    result.projectStatus
}

def call() {
    waitQualityGate()
}


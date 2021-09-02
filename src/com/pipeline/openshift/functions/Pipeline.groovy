#!/usr/bin/env groovy
package com.pipeline.openshift.functions

class Pipeline implements Serializable {
    /**
     * URL of build-pipeline yaml file
     */
    final String PIPELINE_BUILD_CONFIG_URL = 'https://www.fnde.gov.br/repositorio/arquitetura/openshift/jenkins-default-pipeline/raw/master/build-configs/build-pipeline.yml'
    final String PIPELINE_BUILD_CONFIG_FILENAME = 'build-pipeline.yml'
    final String PIPELINE_OCP_CONSOLE_URL = 'https://ocp.fnde.gov.br/console'
    private final steps

    Pipeline(steps) { this.steps = steps }

    def printEnv(build) {
        this.steps.echos "${build}"
    }


    /**
     *
     * @param args.appBaseProjectName - Identification of projeto to be used
     * @param args.appGitBranchName - Git name of branchName to be used
     * @param args.appGitUrl - https Git URL or project
     * @param args.appName - Name of the Application
     * @param args.appImagem - Tipo de Imagem que será criada
     * @param args.pipelineGitToken - Authentication Token of Repository
     * @return
     */
    def createBuildJob(Map args) {
        this.steps.echos "[create][args]: ${args}"

        this.steps.retry(3) {
            this.steps.echos "[create][downloading] build-pipeline.yml"
            this.steps.sh "curl -H \"PRIVATE-TOKEN: ${args.pipelineGitToken}\" -k ${PIPELINE_BUILD_CONFIG_URL} > " + PIPELINE_BUILD_CONFIG_FILENAME
        }
        def params = defineEnviromentByType(args.appType)

        this.steps.echos "[create][processing] build pipeline file"
        def buildConfig = this.steps.openshift.process("-f=${PIPELINE_BUILD_CONFIG_FILENAME}",
                "-p=APP_GIT_BRANCH=${args.appGitBranchName}",
                "-p=APP_GIT_URL=${args.appGitUrl}",
                "-p=APP_NAME=${args.appName}",
                "-p=APP_NAMESPACE_BASE=${args.appBaseProjectName}",
                "-p=APP_TYPE=${args.appType}",
                "-p=PIPELINE_JENKINS_FILE=${params.PIPELINE_JENKINS_FILE}"
        )

        this.steps.echos "[create][using project] ${this.steps.openshift.project()}"
        this.steps.echos "[create][apply] ${buildConfig}"
        this.steps.openshift.apply(buildConfig)
    }

    def defineEnviromentByType(String appType) {
        def env = [:]
        switch (appType) {
            case "eap":
                env.IMAGE_STREAM = 'jboss-eap72-openshift:latest'
                env.TEMPLATE = 'eap-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-java"
                env.ARTIFACT_TYPE = 'war'
                break
            case "openjdk":
                env.IMAGE_STREAM = 'redhat-openjdk18-openshift:latest'
                env.TEMPLATE = 'openjdk-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-java"
                env.ARTIFACT_TYPE = 'jar'
                break
            case "php71":
                env.IMAGE_STREAM = 'php:7.1'
                env.TEMPLATE = 'httpd-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-httpd"
                env.ARTIFACT_TYPE = 'php'
                break
            case "angular":
                env.IMAGE_STREAM = 'httpd:latest'
                env.TEMPLATE = 'httpd-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-httpd"
                env.ARTIFACT_TYPE = 'angular'
                break
            default:
                break
        }
        env.TEMPLATE_URL = "https://www.fnde.gov.br/repositorio/arquitetura/openshift/jenkins-default-pipeline/raw/master/templates/${env.TEMPLATE}.yml"

        return env
    }

    /**
     * @param args.BRANCH_NAME
     * @param args.NAMESPACE_BASE
     * @return
     */
    def defineEnviromentWithBranchNameAndBaseNamespace(Map args) {
        this.steps.echos "[defineEnviromentWithBranchNameAndBaseNamespace][init] args: ${args}"
        def env = [:]
        // used to ensure that JOB_NAME is related to branch (and can be used with security)
        def jobName = this.steps.JOB_NAME.toLowerCase()
        this.steps.echos "[defineEnviromentWithBranchNameAndBaseNamespace][jobName] ${jobName}"

        if (args.BRANCH_NAME == 'master') {
            env.JDP_ENVIRONMENT = "prod"
//            env.JDP_REQUIRES_BUILD = 'N'
            env.JDP_REQUIRES_BUILD = 'Y'
            env.JDP_REQUIRES_DEPLOYMENT = 'Y'
            env.JDP_REQUIRES_APPROVAL = 'Y'
            env.JDP_REQUIRES_PROMOTION = 'N'
            assert jobName.contains('-master')
        } else if (args.BRANCH_NAME.matches('^release.+$')) {
            env.JDP_ENVIRONMENT = "hmg"
            env.JDP_REQUIRES_BUILD = 'Y'
            env.JDP_REQUIRES_DEPLOYMENT = 'Y'
            env.JDP_REQUIRES_APPROVAL = 'N'
            env.JDP_REQUIRES_PROMOTION = 'Y'
            assert jobName.contains('-release')
        } else if (args.BRANCH_NAME.matches('^hotfix.+$')) {
            env.JDP_ENVIRONMENT = "hmg"
            env.JDP_REQUIRES_BUILD = 'Y'
            env.JDP_REQUIRES_DEPLOYMENT = 'Y'
            env.JDP_REQUIRES_APPROVAL = 'N'
            env.JDP_REQUIRES_PROMOTION = 'Y'
            assert jobName.contains('-hotfix')
        } else if (args.BRANCH_NAME == 'develop') {
            env.JDP_ENVIRONMENT = "dev"
            env.JDP_REQUIRES_BUILD = 'Y'
            env.JDP_REQUIRES_DEPLOYMENT = 'Y'
            env.JDP_REQUIRES_APPROVAL = 'N'
            env.JDP_REQUIRES_PROMOTION = 'N'
            assert jobName.contains('-develop')
        } else if (args.BRANCH_NAME.matches('^feature.+$')) {
            env.JDP_ENVIRONMENT = "dev"
            env.JDP_REQUIRES_BUILD = 'Y'
            env.JDP_REQUIRES_DEPLOYMENT = 'N'
            env.JDP_REQUIRES_APPROVAL = 'N'
            env.JDP_REQUIRES_PROMOTION = 'N'
            assert jobName.contains('-feature')
        } else {
            throw new RuntimeException("[VALIDATION_ERROR]: branchName ${args.BRANCH_NAME} is not valid! Use gitflow convetion.")
        }
        env.JDP_NAMESPACE = "${args.NAMESPACE_BASE}-${env.JDP_ENVIRONMENT}"

        this.steps.echos "[defineEnviromentWithBranchNameAndBaseNamespace][end]"

        return env
    }

    static def getEnviromentsWithBranch() {
//        return ['dev': ['develop']]
        return ['dev': ['develop', 'feature'], 'hmg': ['release', 'hotfix'], 'prod': ['master']]
    }

    /**
     * @param args.APP_NAME
     * @param args.APP_TYPE
     * @param args.ENVIRONMENT
     * @param args.NAMESPACE
     * @return
     */
    def newBuild(Map args) {
        this.steps.echos "[newBuild][init] args: ${args}"
        this.steps.openshift.withCluster() {
            this.steps.openshift.withProject("${args.NAMESPACE}") {
                this.steps.echos '[newBuild] starting...'
                def params = defineEnviromentByType(args.APP_TYPE)

                if (!this.steps.openshift.selector("bc", "${args.APP_NAME}").exists()) {
                    this.steps.echos '[newBuild] new building'
                    this.steps.openshift.newBuild("--name=${args.APP_NAME}", "--image-stream=${params.IMAGE_STREAM}", "--binary")
                } else {
                    this.steps.echos '[newBuild] patch build'
                    this.steps.openshift.patch("bc/${args.APP_NAME}", "'{ \"spec\": { \"strategy\": { \"sourceStrategy\": { \"from\": { \"name\": \"${params.IMAGE_STREAM}\" }}}}}'")
                }
                args.OCP_KIND = 'bc'
                setObjectLabels(args)
            }
        }
        this.steps.echos "[newBuild][end]"
    }

    /**
     * @param args.APP_NAME
     * @param args.APP_TYPE
     * @param args.ARTIFACT_STASH_NAME
     * @param args.NAMESPACE
     * @return
     */
    def startBuild(Map args) {
        this.steps.echos "[startBuild][init] args: ${args}"
        this.steps.unstash args.ARTIFACT_STASH_NAME
        def dirName = "./"
        if (args.APP_TYPE == "angular") {
            dirName = "dist/${args.APP_NAME}/"
        }
        this.steps.openshift.withCluster() {
            this.steps.openshift.withProject("${args.NAMESPACE}") {
                this.steps.openshift.selector("bc", "${args.APP_NAME}").startBuild("--from-dir=${dirName}", "--wait=true")
            }
        }
        this.steps.echos "[startBuild][end]"
    }

    /**
     * @param args.APP_NAME
     * @param args.NAMESPACE
     * @param args.APP_TYPE
     * @param args.ENVIRONMENT
     * @return
     */
    def tag(Map args) {
        this.steps.echos "[tag][init] args: ${args}"
        this.steps.openshift.withCluster() {
            this.steps.openshift.withProject("${args.NAMESPACE}") {
                this.steps.echos "[tag] tagging"
                this.steps.openshift.tag("${args.APP_NAME}:latest", "${args.APP_NAME}:${args.ENVIRONMENT}")
                args.OCP_KIND = 'is'
                setObjectLabels(args)
            }
        }
        this.steps.echos "[tag][end]"
    }

    /**
     *
     * @param args.JENKINS_SONAR_NAME
     * @param args.APP_TYPE
     * @param args.APP_NAME
     * @param args.ENVIRONMENT
     * @return
     */
    def doCodeAnalisesWithSonar(Map args) {
        this.steps.echos "[doCodeAnalisesWithSonar][init] args: ${args}"
//        def scannerHome = this.steps.tool args.JENKINS_SONAR_NAME
//        def dirName = (args.APP_TYPE == "angular" ? "src" : ".")
//
//        this.steps.withSonarQubeEnv(args.JENKINS_SONAR_NAME) {
//            this.steps.sh 'printenv | sort'
//
//            this.steps.sh """${scannerHome}/bin/sonar-scanner \
//                  -Dsonar.projectKey=${args.APP_NAME} \
//                  -Dsonar.projectName=${args.APP_NAME} \
//                  -Dsonar.projectVersion=${args.ENVIRONMENT} \
//                  -Dsonar.sources=${dirName} \
//                  -Dsonar.sourceEncoding=UTF-8"""
//
//            args.SONAR_AUTH_TOKEN = this.steps.env.SONAR_AUTH_TOKEN
//            args.SONAR_HOST_URL = this.steps.env.SONAR_HOST_URL
//            waitQualityGate(args)
//        }
        this.steps.echos "[doCodeAnalisesWithSonar][end]"
    }

    /**
     *
     * @param args.APP_NAME
     * @param args.APP_TYPE
     * @param args.SONAR_AUTH_TOKEN
     * @param args.SONAR_HOST_URL
     * @return
     */
    def waitQualityGate(Map args) {
        this.steps.echos "[waitQualityGate] ${args}"
        def api
        if (args.APP_TYPE == "angular" || args.APP_TYPE == "php71") {
            api = "${args.SONAR_HOST_URL}/api/qualitygates/project_status?projectKey=${args.APP_NAME}"
        } else {
            def groupId = getGroupIdFromPom()
            def artifactId = getArtifactIdFromPom()
            api = "${args.SONAR_HOST_URL}/api/qualitygates/project_status?projectKey=${groupId}:${artifactId}"
        }
        this.steps.echos "[waitQualityGate] api=${api}"

        String response = this.steps.sh(script: "curl -k -u ${args.SONAR_AUTH_TOKEN} ${api}", returnStdout: true)
        this.steps.echos "[waitQualityGate] response=${response}"
        def result = toJson(response)
        this.steps.echos "[waitQualityGate] result=${result}"

//        def url = new URL(api)
//        def result = new groovy.json.JsonSlurper().parse(url.newReader())
        result['projectStatus']
    }

    def getGroupIdFromPom() {
        if (this.steps.readMavenPom().getGroupId() == null) {
            this.steps.readMavenPom().getParent().getGroupId()
        } else {
            this.steps.readMavenPom().getGroupId()
        }
    }

    def getArtifactIdFromPom() {
        this.steps.readMavenPom().getArtifactId()
    }

    private String toJson(String responseText) {
        def data = this.steps.readJSON text: responseText
        return data
    }

    /**
     * @param args.APP_NAME
     * @param args.APP_TYPE
     * @param args.ENVIRONMENT
     * @param args.OCP_KIND
     */
    private void setObjectLabels(Map args) {
        this.steps.echos "[setObjectLabels][init] args: ${args}"
        this.steps.openshift.raw("label", "${args.OCP_KIND}", "${args.APP_NAME}", "app=${args.APP_NAME}", "--overwrite")
        this.steps.openshift.raw("label", "${args.OCP_KIND}", "${args.APP_NAME}", "imagem=${args.APP_TYPE}", "--overwrite")
        this.steps.openshift.raw("label", "${args.OCP_KIND}", "${args.APP_NAME}", "ambiente=${args.ENVIRONMENT}", "--overwrite")
        this.steps.echos "[setObjectLabels][end]"
    }


    /**
     * @param args.APP_GIT_URL
     * @param args.APP_NAME
     * @param args.APP_TYPE
     * @param args.ENVIRONMENT
     * @param args.NAMESPACE
     * @param args.TEMPLATE
     *
     * @return
     */
    def deploy(Map args) {
        this.steps.echos "[deploy][init] args: ${args}"
        this.steps.openshift.withCluster() {
            this.steps.openshift.withProject("${args.NAMESPACE}") {

                // remove o autoscale anterior para evitar 'efeito corrida' no deploy
                def hpa = this.steps.openshift.selector("hpa", "${args.APP_NAME}")
                this.steps.echos "Existe AutoScale configurado: ${hpa.exists()}"
                if (hpa.exists()) {
                    this.steps.echos "Removendo AutoScale ${args.APP_NAME}"
                    hpa.delete()
                }

                this.steps.echos "Obtendo template ${args.TEMPLATE}"
                this.steps.withCredentials([this.steps.usernamePassword(
                        credentialsId: "gitlab-fnde-secret",
                        passwordVariable: 'GIT_PASSWORD',
                        usernameVariable: 'GIT_USERNAME')]) {

                    def params = defineEnviromentByType(args.APP_TYPE)

                    final String PIPELINE_TEMPLATE_URL = "https://www.fnde.gov.br/repositorio/arquitetura/openshift/jenkins-default-pipeline/raw/master/templates/${params.TEMPLATE}.yml"
                    this.steps.echos "[deploy][PIPELINE_TEMPLATE_URL] ${PIPELINE_TEMPLATE_URL}"
                    this.steps.retry(3) {
                        this.steps.sh "curl -H \"PRIVATE-TOKEN: ${this.steps.GIT_PASSWORD}\" -k ${PIPELINE_TEMPLATE_URL} > ${this.steps.WORKSPACE}/template.yml"
                    }
                    this.steps.echos "[deploy][PIPELINE_TEMPLATE_URL] downloaded"
                }

                this.steps.echos "[deploy] verify initial content of downloaded template"
                this.steps.sh "head ${this.steps.WORKSPACE}/template.yml"

                this.steps.echos "[deploy] processing template"
                def result = this.steps.openshift.process("-f=${this.steps.WORKSPACE}/template.yml"
                        , "-p=APP_GIT_URL=${args.APP_GIT_URL}"
                        , "-p=APP_NAME=${args.APP_NAME}"
                        , "-p=ENVIRONMENT=${args.ENVIRONMENT}"
                        , "-p=IMAGEM=${args.APP_TYPE}"
                        , "-p=NAMESPACE=${args.NAMESPACE}"
                        , "-p=SERVICE_ACCOUNT=${args.APP_NAME}"
                        , "-p=TAG_NAME=${args.ENVIRONMENT}"
                )
//                                    "-p=CPU_LIMIT=${JDP_CPU_LIMIT}", "-p=CPU_REQUEST=${JDP_CPU_REQUEST}",
//                                    "-p=MEMORY_LIMIT=${JDP_MEMORY_LIMIT}", "-p=MEMORY_REQUEST=${MEMORY_REQUEST}",


                this.steps.echos "[deploy] TEMPLATE >>> ${result}"

                def dc = this.steps.openshift.apply(result).narrow("dc")

                try {
                    this.steps.timeout(time: 10, unit: 'MINUTES') {
                        this.steps.echos "[start verification of deploy] ${args.APP_NAME} ${dc}"

                        def latestDeploymentVersion
                        def pod

                        def podReady = false
                        def tentativas = 1
                        while (podReady == false) {
                            this.steps.sh "sleep 30s"
                            try {
                                latestDeploymentVersion = dc.object().status.latestVersion
                                this.steps.echos "latestDeploymentVersion: ${latestDeploymentVersion}"
                                pod = this.steps.openshift.selector('pods', [deployment: "${args.APP_NAME}-${latestDeploymentVersion}"])
                                this.steps.echos "Tentativa: ${tentativas} - Deploy: ${latestDeploymentVersion} - Pod: ${pod.object().metadata.name}\n" +
                                        "LOG: ${PIPELINE_OCP_CONSOLE_URL}/project/${args.NAMESPACE}/browse/pods/${pod.object().metadata.name}?tab=logs"
                                podReady = pod.object().status.containerStatuses[0].ready
                            } catch (exception) {
                                this.steps.echos "[SKIP_ERROR] ${exception}"
                                podReady = false
                            } finally {
                                tentativas++
                            }
                        }
                    }
                } catch (all) {
                    this.steps.error "[deploy] O deploy não pode ser realizado. Verifique se existe healthcheck e veja os logs do pod."
                }
            }//withProject
        }//withCluster
        this.steps.echos '[deploy][end]'
    }

    /**
     * @param args.APP_NAME
     * @param args.APP_TYPE
     * @param args.ENVIRONMENT
     * @param args.NAMESPACE
     *
     * @return
     */
    def createRoute(Map args) {
        this.steps.echos "[createRoute][init] args: ${args}"
        this.steps.openshift.withCluster() {
            this.steps.openshift.withProject("${args.NAMESPACE}") {
                if (!this.steps.openshift.selector('route', "${args.APP_NAME}").exists()) {
                    this.steps.openshift.create("route edge ${args.APP_NAME}", "--service=${args.APP_NAME}", '--port=8080', '--insecure-policy=Redirect')
                } else {
                    this.steps.echos "[createRoute] patch na rota"
                    this.steps.openshift.patch("route/${args.APP_NAME}", "'{ \"spec\": { \"tls\": { \"termination\": \"edge\", \"insecureEdgeTerminationPolicy\": \"Redirect\" }}}}'")
                }
                this.steps.openshift.raw("label", "route", "${args.APP_NAME}", "router=interno", "--overwrite")
                args.OCP_KIND = 'route'
                setObjectLabels(args)
            }
        }
        this.steps.echos '[createRoute][end]'
    }

    /**
     * @param args.APP_NAME
     * @param args.ENVIRONMENT
     * @param args.NAMESPACE_BASE
     *
     * @return
     */
    def promoteImage(Map args) {
        this.steps.echos "[promoteImage][init] args: ${args}"
        /*
             * @param args.ENVIRONMENT_FROM
     * @param args.ENVIRONMENT_TARGET
         */
        switch (args.ENVIRONMENT) {
            case "eap":
                env.IMAGE_STREAM = 'eap-custom-introscope:1.1'
                env.TEMPLATE = 'eap-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-java"
                env.ARTIFACT_TYPE = 'war'
                break
            case "openjdk":
                env.IMAGE_STREAM = 'openjdk-custom-introscope:1.0'
                env.TEMPLATE = 'openjdk-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-java"
                env.ARTIFACT_TYPE = 'jar'
                break
            case "php71":
                env.IMAGE_STREAM = 'php:7.1'
                env.TEMPLATE = 'httpd-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-httpd"
                env.ARTIFACT_TYPE = 'php'
                break
            case "angular":
                env.IMAGE_STREAM = 'httpd-24-rhel7:latest'
                env.TEMPLATE = 'httpd-app-template'
                env.PIPELINE_JENKINS_FILE = "pipelines/Jenkinsfile-Build-httpd"
                env.ARTIFACT_TYPE = 'angular'
                break
            default:
                break
        }


        def project_target = "${args.NAMESPACE_BASE}-${args.ENVIRONMENT_TARGET}"
        def project_from = "${args.NAMESPACE_BASE}-${args.ENVIRONMENT_FROM}"

        this.steps.openshift.withProject("${project_target}") {
            this.steps.echos "Using project: ${openshift.project()}"
            if (this.steps.openshift.raw("get", "is/${args.APP_NAME}", "--ignore-not-found=true", "-o=jsonpath=\'{.spec.tags[?(@.name==\"${args.ENVIRONMENT_TARGET}\")].name}'").out.trim() != "${args.ENVIRONMENT_TARGET}") {
                this.steps.echos "[Promote to TARGET] First time creation..."
                this.steps.openshift.tag("${project_from}/${args.APP_NAME}:latest", "${project_target}/${args.APP_NAME}:${args.ENVIRONMENT_TARGET}")
                this.steps.openshift.tag("${project_target}/${args.APP_NAME}:${args.ENVIRONMENT_TARGET}", "${project_target}/${args.APP_NAME}:${args.ENVIRONMENT_TARGET}-previous")
            } else {
                this.steps.echos "[Promote to TARGET] Updating tags..."
                this.steps.openshift.tag("${project_target}/${args.APP_NAME}:${args.ENVIRONMENT_TARGET}", "${project_target}/${args.APP_NAME}:${args.ENVIRONMENT_TARGET}-previous")
                this.steps.openshift.tag("${project_from}/${args.APP_NAME}:latest", "${project_target}/${args.APP_NAME}:${args.ENVIRONMENT_TARGET}")
            }
            this.steps.openshift.tag("${project_from}/${args.APP_NAME}:latest", "${project_target}/${args.APP_NAME}:latest")

//            this.steps.retry(3) {
//                this.steps.openshift.raw("import-image", "${namespaceTarget}/${args.APP_NAME}:${args.APP_VERSION}",
//                        "--from=\'docker-registry.default.svc:5000/${args.NAMESPACE_FROM}/${args.APP_NAME}:${args.APP_VERSION}\'", "--confirm")
//
//                def tag = getTag("${args.APP_VERSION}", "${args.APP_NAME}")
//
//                if (tag == null) {
//                    this.steps.error "Reason tag ${args.APP_VERSION} not found in the target project '${namespaceTarget}'"
//                } else if (tag.items == null) {
//                    this.steps.error "Reason ${tag.conditions[0].message} in the target project '${namespaceTarget}'"
//                } else {
//                    this.steps.echos "Tag '${args.APP_VERSION}' importada com sucesso em '${tag.items[0].created}'"
//                }
//
//                this.steps.echos "Selecionando build config ${args.APP_NAME}-master"
//
//                def build = this.steps.openshift.selector("bc", "${args.APP_NAME}-master")
//
//                if (build.exists()) {
//                    this.steps.echos "Starting build config ${args.APP_NAME}..."
//                    build.startBuild("-e", "BRANCH=master", "-e", "VERSION=${args.APP_VERSION}")
//                }
//            }
        }

        this.steps.echos '[promoteImage][end]'
    }

    private def getTag = { tagName, appName ->
        def result = this.steps.openshift.selector("is/${appName}").object()
        return result['status'].tags.find { it.tag == tagName }
    }

}
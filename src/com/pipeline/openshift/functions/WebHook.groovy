#!/usr/bin/env groovy
package com.pipeline.openshift.functions

class WebHook implements Serializable {

    private final steps

    WebHook(steps) { this.steps = steps }

    /**
     *
     * @param args.gitBaseUrl - Base Url of Git Project - ex: https://www.fnde.gov.br/repositorio
     * @param args.gitProjectId - Encoded Project ID of Git - its calculated considering project URL and base URL
     * @param args.gitProjectUrl - URL of Git Project
     * @param args.gitToken - Token used to Authenticate with GitLab
     * @param args.jenkinsUrl - Jenkins Url to  be called when trigger occurs
     * @param args.webhookToken - Secret Shared betweeen Jenkins and GitLab
     * @return
     */
    def configure(Map args) {
        if (args.size() == 0) {
            throw new IllegalArgumentException('All required params must be specified!')
        }

        args.gitProjectId = configureProjectId(args)
        args.webhookUrl = genericWebhookTriggerUrl(args.jenkinsUrl, args.webhookToken)

        this.steps.echo "[configure] args: ${args}"

        def list = list(args)
        this.steps.echo "[configure] list: ${list}"

        if (list.size() == 0) {
            return create(args)
        } else {
            boolean found = false
            list.each {
                this.steps.echo "item = ${it}"
                if (it['url'] == args.webhookUrl) {
                    found = true
                    this.steps.echo "[list][FOUND]"
                    return
                }
            }

            if (!found) {
                return create(args)
            }
        }
    }

    def startBuild(Map args) {
        this.steps.echo "[startBuild][ini][${args.namespace} - ${args.bcName}]"
        String COMMAND = "oc -n '${args.namespace}' start-build bc/${args.bcName} -e JDP_PIPELINE_CREATE_WEBHOOK=true"
        this.steps.echo "[startBuild][start]---------------------\nCOMMAND=\n${COMMAND}\n---------------------"
        def out = this.steps.sh(script: "${COMMAND}", returnStdout: true)
        this.steps.echo "[startBuild][out][${out}]"
    }

    def list(Map args) {
        this.steps.echos "[list]${args}"
        def result = this.steps.sh(returnStdout: true, script: "curl -XGET -H \"Content-Type: application/json\""
                + "   -H \"PRIVATE-TOKEN: ${args.gitToken}\" "
                + "   ${args.gitBaseUrl}/api/v4/projects/${args.gitProjectId}/hooks").trim()
        this.steps.echos "[list][result]${result}"

        return toJson(result)
    }

    private def create(Map args) {
        String data = """
  {
    "url": \"${args.webhookUrl}\",
    "push_events": "true",
    "enable_ssl_verification": "false"
  }
  """
        def result = this.steps.sh(returnStdout: true,
                script: "curl -XPOST -H \"Content-Type: application/json\""
                        + "   -H \"PRIVATE-TOKEN: ${args.gitToken}\" "
                        + "   ${args.gitBaseUrl}/api/v4/projects/${args.gitProjectId}/hooks"
                        + "   -d '${data}' "
        )
        this.steps.echo "[create][result]${result}"
        return toJson(result)
    }

    private String configureProjectId(Map args) {
        this.steps.echos "[configureProjectId] ${args}"
        String projectId = getPathWithNamespace(args.gitProjectUrl, args.gitBaseUrl)

        projectId = encode(projectId)

        this.steps.echo 'using projectId=' + projectId
        projectId
    }

    private String encode(String s) {
        URLEncoder.encode(s, 'UTF-8')
    }

    private String genericWebhookTriggerUrl(String jenkinsUrl, String webhookToken) {
        this.steps.echos "[genericWebhookTriggerUrl] jenkinsUrl=${jenkinsUrl} webhookToken=${webhookToken}"
        return "${jenkinsUrl}generic-webhook-trigger/invoke?token=${webhookToken}"
    }

    private String toJson(String responseText) {
        def data = this.steps.readJSON text: responseText
        return data
    }

    private List<String> getGitFlowBranchNames() {
        return ['master', 'develop', 'feature', 'release', 'hotfix']
    }

//    private String getRegexpFilterExpression(String gitPathWithNamespace) {
//
//        String s = ''
//        getGitFlowBranchNames().each {
//            s += '|' + gitPathWithNamespace + '/refs/heads/' + it
//        }
//
//        // remove first caracter
//        s = s.substring(1, s.length())
//
//        return '^(' + s + ')$'
//    }

    // regexpFilterExpression   : getBranchNameUnlessNotMaster(GIT_PATH_WITH_NAMESPACE, args.APP_GIT_BRANCH)

//    private String getBranchNameUnlessNotMaster(String gitPathWithNamespace, String gitBranchName) {
//        assert OCPHelper.isNotEmptyOrNull(gitPathWithNamespace)
//        assert OCPHelper.isNotEmptyOrNull(gitBranchName)
//
//        String branch = 'IGNORAR'
//        gitBranchName = gitBranchName.toLowerCase()
//
//        if (gitBranchName.contains('develop')) {
//            branch = 'develop'
//        } else if (gitBranchName.contains('hotfix')) {
//            branch = 'hotfix/.+'
//        } else if (gitBranchName.contains('release')) {
//            branch = 'release/.+'
//        }
//        def s = gitPathWithNamespace + '/refs/heads/' + branch
//        return '^(' + s + ')$'
//    }

    private String getBranchName(String gitPathWithNamespace, String jobName) {
        assert OCPHelper.isNotEmptyOrNull(gitPathWithNamespace)
        assert OCPHelper.isNotEmptyOrNull(jobName)

        String branch = 'IGNORAR'
        jobName = jobName.toLowerCase()
        if (jobName.contains('-master')) {
            branch = 'master'
        } else if (jobName.contains('-develop')) {
            branch = 'develop'
        } else if (jobName.contains('-feature')) {
            branch = 'feature.+'
        } else if (jobName.contains('-hotfix')) {
            branch = 'hotfix.+'
        } else if (jobName.contains('-release')) {
            branch = 'release.+'
        }
        def s = gitPathWithNamespace + '/refs/heads/' + branch
        return '^(' + s + ')$'
    }

    private String getPathWithNamespace(String gitProjectUrl, String gitBaseUrl) {
        println "[getPathWithNamespace] gitProjectUrl=${gitProjectUrl} gitBaseUrl=${gitBaseUrl}"
        int of = gitProjectUrl.indexOf(gitBaseUrl)
        if (of < 0) {
            throw new IllegalArgumentException('Base Url must be part of gitProject Url')
        }
        // remove gitBaseUrl from URL
        String gitProjectName = gitProjectUrl.substring(of + gitBaseUrl.length() + 1)

        // remove .git from URL
        if (gitProjectName.endsWith(".git")) {
            gitProjectName = gitProjectName.substring(0, gitProjectName.length() - 4)
        }

        return gitProjectName
    }

    /**
     * @param args.GIT_BASE_URL
     * @param args.GIT_WEBHOOK_CREDENTIAL_ID_NAME
     * @param args.APP_GIT_BRANCH
     * @param args.APP_GIT_URL
     */
    void configureCurrentJobWithWebHook(Map args) {
        this.steps.println "[configureCurrentJobWithWebHook] inicio - configurando Generic Webhook Trigger - ${this.steps.JOB_NAME}"
        def GIT_PATH_WITH_NAMESPACE = getPathWithNamespace(args.APP_GIT_URL, args.GIT_BASE_URL)
        this.steps.withCredentials([this.steps.string(credentialsId: args.GIT_WEBHOOK_CREDENTIAL_ID_NAME, variable: 'GIT_WEBHOOK_CREDENTIAL_ID_VALUE')]) {
            this.steps.properties([
                    this.steps.pipelineTriggers([
                            [$class                   : 'GenericTrigger',
                             genericVariables         : [
                                     [key: 'GITWH_PATH_WITH_NAMESPACE', value: '$.project.path_with_namespace'],
                                     [key: 'GITWH_BRANCH', value: '$.ref']
                             ],
                             causeString              : 'Triggered on $GITWH_BRANCH',
                             token                    : this.steps.env.GIT_WEBHOOK_CREDENTIAL_ID_VALUE,
                             printContributedVariables: true,
                             printPostContent         : true,
                             silentResponse           : false,
                             regexpFilterText         : '$GITWH_PATH_WITH_NAMESPACE/$GITWH_BRANCH',
                             regexpFilterExpression   : getBranchName(GIT_PATH_WITH_NAMESPACE, this.steps.JOB_NAME)
                            ]
                    ])
            ])
        }
        this.steps.println "[configureCurrentJobWithWebHook] fim - configurando Generic Webhook Trigger - ${this.steps.JOB_NAME}"
//        this.steps.error '[configureCurrentJobWithWebHook] Abortando o pipeline... Webhook Criado com sucesso!'
    }
}
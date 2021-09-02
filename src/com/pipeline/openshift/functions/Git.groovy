#!/usr/bin/env groovy
package com.pipeline.openshift.functions

class Git implements Serializable {

	private steps

	Git(steps) {this.steps = steps}

    /**
     * @param  args.URL
     * @param  args.BRANCH_NAME
     * @param  args.CREDENTIALS_ID
     * @return
     */
    def checkout(Map args) {
        def info = [:]
        this.steps.git url: "${args.URL}", branch: "${args.BRANCH_NAME}", credentialsId: args.CREDENTIALS_ID
        info.COMMIT_HASH_SHORT = this.steps.sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
//        info.COMMIT_HASH = this.steps.sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%H'").trim()
//        info.COMMITTER_NAME = this.steps.sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%cn'").trim()
//        info.COMMIT_MESSAGE = this.steps.sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%s'").trim()
//        info.COMMIT_DATE = this.steps.sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%ad'").trim()
        return info
    }
}
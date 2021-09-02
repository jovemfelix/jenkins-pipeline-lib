#!/usr/bin/env groovy

def showEnv() {
    sh 'printenv | sort'
}

def call() {
    showEnv()
}


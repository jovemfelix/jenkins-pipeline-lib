#!/usr/bin/env groovy

def getSonarQubeURL() {
  echos "[getSonarQubeURL] ${SONAR_HOST_URL}"
  return "${SONAR_HOST_URL}"
}

def call() {
  return getSonarQubeURL()
}


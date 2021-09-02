#!/usr/bin/env groovy

def call() {
  readMavenPom().getArtifactId()
}

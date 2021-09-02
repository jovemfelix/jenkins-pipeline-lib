#!/usr/bin/env groovy

def call() {
  readMavenPom().getVersion()
}

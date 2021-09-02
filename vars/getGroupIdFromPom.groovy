#!/usr/bin/env groovy

def call() {
  if (readMavenPom().getGroupId() == null) {
    readMavenPom().getParent().getGroupId()
  } else {
    readMavenPom().getGroupId()
  }
}

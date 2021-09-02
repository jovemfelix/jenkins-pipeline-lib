
## Como fazer uso das funções auxiliares

No início do arquivo do pipeline deve-se declarar que será feito uso de bibliotecas auxiliares da seguinte forma:

````groovy
#!groovy

@Library('funcoes-auxiliares') _

node('master') {

  stage('Checkout') {
    git 'https://my-git-url.git'
  }

  stage('Variaveis') {
    def groupId    = getGroupIdFromPom()
    def artifactId = getArtifactIdFromPom()
    def version    = getVersionFromPom()

    echo "Artifact ID: ${artifactId} - Group ID: ${groupId} - Version: ${version}"
  }
}
````



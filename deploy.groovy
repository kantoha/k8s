import org.apache.commons.lang.RandomStringUtils

def workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
def branch = 'master'
def credentialsId = 'git-sshkey'
def gitHost = 'github.com'
def gitProjectName = 'k8s'
def gitGroupName = 'kantoha'
def cloneUrl = "git@${gitHost}:${gitGroupName}/${gitProjectName}.git"
def appName = 'spring'
def namespace = params.ENV
pipeline {
    agent { label 'master' }
    stages {
        stage ('Checkout') {
            steps {
                script {
                    sh "rm -rf ${workDir}*"
                    dir("${workDir}") {
                        checkout([$class                           : 'GitSCM', branches: [[name: "${branch}"]],
                                  doGenerateSubmoduleConfigurations: false, extensions: [],
                                  submoduleCfg                     : [],
                                  userRemoteConfigs                : [[credentialsId: "${credentialsId}",
                                                                       url          : "${cloneUrl}"]]])
                    }
                }
            }
        }
        stage('Deploy application') {
            steps {
                script {
                    dir("${workDir}") {
                        deployHelm(namespace, appName)
                    }
                }
            }
        }
    }
}

def deployHelm(namespace, appName) {
    list = sh(script: "helm list", returnStdout: true).trim()
    if (list.contains("${namespace}-${appName}")) {
        sh """
           helm upgrade --force ${namespace}-${appName}  \
                        --wait --timeout=300 \
                        --namespace ${namespace} \
                        --set name=${appName} \
                        --set namespace=${namespace} \
                        --set springboot.secretName=spring-${namespace} \
                        deploy-templates
           """
    } else {
        sh """
           helm install --name=${namespace}-${appName}  \
                        --wait --timeout=300 \
                        --namespace ${namespace} \
                        --set name=${appName} \
                        --set namespace=${namespace} \
                        --set springboot.secretName=spring-${namespace} \
                        deploy-templates
           """
    }
}

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
  agent {
      kubernetes {
          label "maven"
          yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: jnlp
    image: kanton10062006/maven:helm
    args: ['\$(JENKINS_SECRET)', '\$(JENKINS_NAME)']
    tty: true
    securityContext:
      runAsUser: 0
    volumeMounts:
    - name: docker-sock
      mountPath: /var/run/docker.sock
    resources:
      limits:
        cpu: "512m"
        memory: 512Mi
      requests:
        cpu: 256m
        memory: 512Mi
  volumes:
  - name: docker-sock
    hostPath:
      path: /var/run/docker.sock
"""
        }
    }
    options { skipDefaultCheckout true }
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
    list = sh(script: "helm list -n ${namespace}", returnStdout: true).trim()
    if (list.contains("${namespace}-${appName}")) {
        sh """
           helm upgrade --force ${namespace}-${appName}  \
                        --namespace ${namespace} \
                        --set name=${appName} \
                        --set namespace=${namespace} \
                        --set springboot.secretName=spring-${namespace} \
                        deploy-templates
           """
    } else {
        sh """
           helm install ${namespace}-${appName}  \
                        --namespace ${namespace} \
                        --set name=${appName} \
                        --set namespace=${namespace} \
                        --set springboot.secretName=spring-${namespace} \
                        deploy-templates
           """
    }
}

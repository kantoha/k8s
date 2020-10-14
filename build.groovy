import org.apache.commons.lang.RandomStringUtils
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import groovy.json.JsonBuilder

def workDir = new File("/tmp/${RandomStringUtils.random(10, true, true)}")
def branch = 'master'
def credentialsId = 'git-sshkey'
def gitHost = 'github.com'
def gitProjectName = 'k8s'
def gitGroupName = 'kantoha'
def cloneUrl = "git@${gitHost}:${gitGroupName}/${gitProjectName}.git"
def ciNamespace = 'cicd'
def kanikoPodName = 'kaniko'

def getRunningInitKanikoPods(podName, namespace) {
    status = sh(script: "kubectl get pod -n ${namespace} ${podName} -o json",
            returnStdout: true
    ).trim()
    jsonParserOutput = new JsonSlurperClassic().parseText(status)
    initPodStatus = jsonParserOutput.status.initContainerStatuses[0].state.toString()
    return initPodStatus
}

def getRunningKanikoPods(podName, namespace) {
    status = sh(script: "kubectl get pod -n ${namespace} ${podName} -o json",
            returnStdout: true
    ).trim()
    jsonParserOutput = new JsonSlurperClassic().parseText(status)
    podStatus = jsonParserOutput.status.phase.toString()
    return podStatus
}

pipeline {
  agent {
      kubernetes {
          label "maven"
          defaultContainer "maven"
          yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven
    image: kanton10062006/maven:jdk8
    command: ["cat"]
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
                    sh("rm -rf ${workDir}*")
                    dir("${workDir}") {
                        git url: "${cloneUrl}", branch: "${branch}", credentialsId: "${credentialsId}"
                    }
                }
            }
        }
        stage ('Compile') {
            steps {
                script {
                    sh("mkdir -p ${workDir} && cd ${workDir}")
                    sh("mvn compile")
                }
            }
        }
        stage ('Build') {
            steps {
                script {
                    sh("mkdir -p ${workDir} && cd ${workDir}")
                    sh("mvn clean package -B -DskipTests=true")
                }
            }
        }
        stage('Build Kaniko Image') {
            steps {
                script {
                    dir("${workDir}") {
                        sh("kubectl get cm kaniko-template -o 'jsonpath={.data.kaniko\\.json}' -n ${ciNamespace} > kaniko.json")
                        sh """
                         kubectl patch -f kaniko.json --local=true --type json -p='[{"op": "replace", "path": "/spec/containers/0/args/0", "value": "--destination=kanton10062006/k8s:build-${env.BUILD_ID}" }]' -o json  > kaniko-container.json
                        """
                        sh """
                         kubectl patch -f kaniko-container.json --local=true --type json -p='[{"op": "add", "path": "/spec/containers/0/args/1", "value": "--destination=kanton10062006/k8s:latest" }]' -o json  > kaniko-patched-container.json
                        """
                        sh "cat kaniko-patched-container.json"
                        sh "kubectl apply -f kaniko-patched-container.json -n ${ciNamespace}"

                        while (!getRunningInitKanikoPods(kanikoPodName, ciNamespace).contains("running")) {
                            println("[JENKINS][DEBUG] Waiting for init container in Kaniko is started")
                            sleep(5)
                        }

                        sh "kubectl cp target/demo-0.0.1-SNAPSHOT.jar ${kanikoPodName}:/tmp/workspace -c init-kaniko"
                        sh "kubectl cp Dockerfile ${kanikoPodName}:/tmp/workspace -c init-kaniko"

                        while (!getRunningKanikoPods(kanikoPodName, ciNamespace).contains("Succeeded")) {
                            println("[JENKINS][DEBUG] Waiting for container in Kaniko is finished.")
                            sleep(5)
                        }

                        println("[JENKINS][DEBUG] Delete ${kanikoPodName} in namespace ${ciNamespace}")
                        sh "kubectl delete pod ${kanikoPodName} -n ${ciNamespace}"
                    }
                }
            }
        }
    }
}

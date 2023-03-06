def call(Map pipelineParams){
pipeline {
    environment {
        scmUrl = "${pipelineParams.scmUrl}"
   //     awsCred = "${pipelineParams.awscred}"
        region  = "${pipelineParams.region}"
        clusterName = "${pipelineParams.clusterName}"
        gitCred = "${pipelineParams.gitCred}"
        ecRegistry = "${pipelineParams.ecRegistry}"
        channel = "${pipelineParams.channel}" //Slack Chanel Name
        namespace = "${pipelineParams.namespace}"
        dpName = "${pipelineParams.dpName}" //in k8s deployment name
        podName = "${pipelineParams.podName}" //in k8s podname name
        accountId="${pipelineParams.accountId}" //Aws Account Id
        projectName="${pipelineParams.projectName}"
    } // End of Environment

    agent any
    options {
        skipStagesAfterUnstable()
    }

    stages {
        stage('Clean WorkSpace') {
            steps {
                cleanWs()
            } // End of Steps
        } // End of Stage Clean WorkSpace

        stage('SCM') {
            steps {
                script {
                    dir('./') {
                    git url: "${scmUrl}",branch: env.BRANCH_NAME, credentialsId: "${gitCred}"
                    commitId = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    echo "${commitId}"
                    echo "This is your branch ${env.BRANCH_NAME} and commit id is ${commitId}."
                    }
                } // End of Script
            } // End of Steps
        } //End of Stage SCM
        stage('Prune dangling resources before build'){
              steps {
                  sh " docker system prune -a -f || true"
              }// End of Steps
              }// end of stages
       stage('Building Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: "ecr:${region}:${accountId}", url: "https://${accountId}.dkr.ecr.${region}.amazonaws.com") {
                        //def commitId = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        //docker.withRegistry('', registryCredential) {
                        dockerImage = docker.build(ecRegistry + ":" + "${env.BRANCH_NAME}" + "-" + commitId, ".")
                    }
                } // End of Script
            } // End of Steps
        } //End of Stage Building Image

        stage('Push Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: "ecr:${region}:${accountId}", url: "https://${accountId}.dkr.ecr.${region}.amazonaws.com") {
                     dockerImage.push()
                    }
                } // End of Script
            } // End of Steps
        } // End stage Push Image
        stage('Prune dangling resources after build'){
              steps {
                  sh " docker system prune -a -f  || true"
              }// End of Steps
              }// end of stages
        stage('Deploy App in to Dev') {
            steps {
                script {
                    withAWS(credentials: "${accountId}", region: "${region}") {
                     sh """
                     aws eks --region ${region} update-kubeconfig --name ${clusterName}
                     kubectl get node
                     kubectl set image deployment/${dpName} ${podName}=${ecRegistry}:${env.BRANCH_NAME}-${commitId} -n ${projectName}-${env.BRANCH_NAME}
                     kubectl rollout status deployment/${dpName} -n ${projectName}-${env.BRANCH_NAME}
                     """
                    }
                } // End of Script
            } // End of Steps
        } // End of stage Deploy App in to Dev

    } // End of Stages
    post {
        always {
            cleanWs()
            sh """
            docker system prune -a -f || true
            """
        }
        // only triggered when blue or green sign
    } // End of Post
} // End of Pipeline
}

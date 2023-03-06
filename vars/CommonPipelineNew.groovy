def call(Map pipelineParams){
pipeline {
    environment {
        scmUrl = "${pipelineParams.scmUrl}"
        clusterName = "${pipelineParams.clusterName}"
        gitCred = "${pipelineParams.gitCred}"
        dockerRegistry = "${pipelineParams.dockerRegistry}"
        channel = "${pipelineParams.channel}" //Slack Chanel Name
        namespace = "${pipelineParams.namespace}"
        dpName = "${pipelineParams.dpName}" //in k8s deployment name
        podName = "${pipelineParams.podName}" //in k8s podname name
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
           //     slackSend channel: "#${channel}", color: 'warning', iconEmoji:":exclamation:", message: "Build Started - Job_Name:-${env.JOB_NAME}        //BuildNo:-${env.BUILD_NUMBER}  Branch:-${env.BRANCH_NAME} (<${env.BUILD_URL}|Open>)"
                script {
                    dir('./') {
                    git url: "${scmUrl}",branch: "${env.BRANCH_NAME}", credentialsId: "${gitCred}"
                    commitId = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                    echo "${commitId}"
                    echo "This is your branch ${env.BRANCH_NAME} and commit id is ${commitId}."
                    }
                } // End of Script
            } // End of Steps
        } //End of Stage SCM

        stage('Building Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: "bafb2122-8644-4b46-8f0e-3f507bcef8c3", url: '') {
                        //def commitId = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        //docker.withRegistry('', registryCredential) {
                        dockerImage = docker.build(dockerRegistry + ":" + "${env.BRANCH_NAME}" + "-" + commitId, ".")
                    }
                } // End of Script
            } // End of Steps
        } //End of Stage Building Image

        stage('Push Image') {
            steps {
                script {
                    withDockerRegistry(credentialsId: "bafb2122-8644-4b46-8f0e-3f507bcef8c3", url: '') {
                     dockerImage.push()
                    }
                } // End of Script
            } // End of Steps
        } // End stage Push Image

        stage('Deploy App in to Dev') {
            when {
                anyOf { branch 'dev'; branch 'qa'; branch 'uat' }
            }
            steps {
                script {
                    withKubeConfig(caCertificate: '', clusterName: 'nonprod-rapidinnovation', contextName: '', credentialsId:   'nonprod-kubernetes-cluster', namespace: '', serverUrl: 'https://7b5b06da-1bb2-43f1-b3e8-2c8ae98f10d2.ap-west-1.linodelke.net:443') {
                        // some block
                    sh """
                        kubectl get node
                        kubectl set image deployment/${dpName} ${podName}=${dockerRegistry}:${env.BRANCH_NAME}-${commitId} -n ${projectName}-${env.BRANCH_NAME}
                        kubectl rollout status deployment/${dpName} -n ${projectName}-${env.BRANCH_NAME}
                    """
                    }
                } // End of Script
            } // End of Steps
        } // End of stage Deploy App in to Dev

        stage('Deploy App in to Prod') {
            when {
                branch 'master'
            }
            steps {
                script {
                    withKubeConfig(caCertificate: '', clusterName: 'prod-rapidinnovation', contextName: '', credentialsId:   'prod-rapidinnovation', namespace: '', serverUrl: 'https://30f4ded3-0ac9-450b-b6ce-0c76eaf1709e.ap-west-1.linodelke.net:443') {
                        // some block
                    sh """
                        kubectl get node
                        kubectl set image deployment/${dpName} ${podName}=${dockerRegistry}:${env.BRANCH_NAME}-${commitId} -n ${projectName}-master
                        kubectl rollout status deployment/${dpName} -n ${projectName}-master
                    """
                    }
                } // End of Script
            } // End of Steps
        } // End of stage Deploy App in to master
        
        //stage('Prune dangling resources after build'){
        //      steps {
        //          sh " docker system prune -a -f  || true"
        //      }// End of Steps
        //}// end of stage
        
        

    } // End of Stages

    post {
        always {
            cleanWs()
        }
        success {
            echo 'This will run only if successful'
        }
        failure {
            echo 'This will run only if failed'
        }
    }// End of Post

} // End of Pipeline
} // End of def

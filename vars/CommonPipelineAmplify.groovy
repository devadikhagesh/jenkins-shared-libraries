def call(Map pipelineParams){
pipeline {
    environment {
        scmUrl = "${pipelineParams.scmUrl}"
        gitCred = "${pipelineParams.gitCred}"
        s3Url = "${pipelineParams.s3Url}"
        region = "${pipelineParams.region}"
        amplifyId = "${pipelineParams.amplifyId}"
        accountId = "${pipelineParams.accountId}"
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
                    //echo "${commitId}"
                    //echo "This is your branch ${env.BRANCH_NAME} and commit id is ${commitId}."
                    }
                } // End of Script
            } // End of Steps
        } //End of Stage SCM

        //stage('Prune dangling resources before build'){
        //      steps {
        //          sh " docker system prune -a -f || true"
        //    }// End of Steps
        //}// end of stages

        stage('Building project') {
            steps {
                script {
                    withAWS(credentials: "${accountId}", region: "${region}") {
                    sh """
                     envsubst < .env.tmp > .env || true
                     export NODE_OPTIONS=--max-old-space-size=8192
                     /root/.yarn/bin/yarn install
                     /root/.yarn/bin/yarn build
                     cp .env webapp || true
                     ls -a
                     cd webapp
                     pwd
                     ls -a
                     zip build.zip ./*
                     /usr/local/bin/aws s3 cp build.zip ${s3Url}/${env.BRANCH_NAME}/build.zip --region ${region}
                     """
                    }
                } // End of Script
            } // End of Steps
        } //End of Stage Building Image

        stage('Deploy to Amplify') {
            steps {
                script {
                    withAWS(credentials: "${accountId}", region: "${region}") {
                    sh """
                     /usr/local/bin/aws amplify start-deployment --app-id ${amplifyId} --branch-name ${env.BRANCH_NAME} --source-url ${s3Url}/${env.BRANCH_NAME}/build.zip --region ${region}
                     """
                    }
                } // End of Script
            } // End of Steps
        } //End of Stage Building Image
        

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
    }
} // End of Pipeline
} // End of def

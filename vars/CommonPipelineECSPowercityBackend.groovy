def call(Map pipelineParams){
pipeline {

    environment {
        scmUrl = "${pipelineParams.scmUrl}"
        gitCred = "${pipelineParams.gitCred}"                
        region  = "${pipelineParams.region}"
        ecsServiceEth = "${pipelineParams.ecsServiceEth}"
        ecsServicePulse = "${pipelineParams.ecsServicePulse}"
        ecsTaskDefinitionEth = "${pipelineParams.ecsTaskDefinitionEth}"
        ecsTaskDefinitionPulse = "${pipelineParams.ecsTaskDefinitionPulse}"
        ecRegistry = "${pipelineParams.ecRegistry}"
        accountId="${pipelineParams.accountId}"
        ecsCluster = "${pipelineParams.ecsCluster}"
    }

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
              //  slackSend channel: "#${channel}", color: 'warning', iconEmoji:":exclamation:", message: "Build Started - Job_Name:-${env.JOB_NAME}        //BuildNo:-${env.BUILD_NUMBER}  Branch:-${env.BRANCH_NAME} (<${env.BUILD_URL}|Open>)"
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
        stage('Deploy App in to ECS Eth') {
            steps {
                    withAWS(credentials: "${accountId}", region: "${region}") {
                      script{
                        commit_id = "${commitId}"
                        ecr_registry = "${ecRegistry}"
                        NEWIMAGE = "${ecRegistry}:${env.BRANCH_NAME}-${commitId}"
                        NEW_IMAGE = "${NEWIMAGE}"
                        ecs_TaskDefinitionEth= "${ecsTaskDefinitionEth}"
                        reGion="${region}"
                     sh """#!/bin/bash
                      export NEW_TASK=`aws ecs describe-task-definition --task-definition "${ecs_TaskDefinitionEth}" --region "${reGion}" | jq '.taskDefinition | .containerDefinitions[0].image = "${NEW_IMAGE}" | del(.taskDefinitionArn) | del(.revision) | del(.status) | del(.requiresAttributes) | del(.compatibilities) | del(.registeredAt) | del(.registeredBy)'`
                     export NEW_TASK_DEFINITION=`aws ecs register-task-definition --region "${reGion}" --cli-input-json "\${NEW_TASK}"`
                    export NEW_REVISION=`echo "\$NEW_TASK_DEFINITION" | jq '.taskDefinition.revision'`
                    aws ecs update-service --cluster ${ecsCluster} --service "\${ecsServiceEth}"  --task-definition ${ecs_TaskDefinitionEth}:"\${NEW_REVISION}"
                    """
                    }
                } // End of Script
            } // End of Steps
        } // End of stage Deploy App in to ECS
        stage('Deploy App in to ECS Pulse') {
            steps {
                    withAWS(credentials: "${accountId}", region: "${region}") {
                      script{
                        commit_id = "${commitId}"
                        ecr_registry = "${ecRegistry}"
                        NEWIMAGE = "${ecRegistry}:${env.BRANCH_NAME}-${commitId}"
                        NEW_IMAGE = "${NEWIMAGE}"
                        ecs_TaskDefinitionPulse= "${ecsTaskDefinitionPulse}"
                        reGion="${region}"
                     sh """#!/bin/bash
                      export NEW_TASK=`aws ecs describe-task-definition --task-definition "${ecs_TaskDefinitionPulse}" --region "${reGion}" | jq '.taskDefinition | .containerDefinitions[0].image = "${NEW_IMAGE}" | del(.taskDefinitionArn) | del(.revision) | del(.status) | del(.requiresAttributes) | del(.compatibilities) | del(.registeredAt) | del(.registeredBy)'`
                     export NEW_TASK_DEFINITION=`aws ecs register-task-definition --region "${reGion}" --cli-input-json "\${NEW_TASK}"`
                    export NEW_REVISION=`echo "\$NEW_TASK_DEFINITION" | jq '.taskDefinition.revision'`
                    aws ecs update-service --cluster ${ecsCluster} --service "\${ecsServicePulse}"  --task-definition ${ecs_TaskDefinitionPulse}:"\${NEW_REVISION}"
                    """
                    }
                } // End of Script
            } // End of Steps
        } // End of stage Deploy App in to ECS
   }//End of stages
} // End of Pipeline
} //End of def

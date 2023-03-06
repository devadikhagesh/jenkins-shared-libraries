def call(Map pipelineParams){
    pipeline {
    environment {
        scmUrl = "${pipelineParams.scmUrl}"
        awscred = "${pipelineParams.awscred}"
        region  = "${pipelineParams.region}"
        clustername = "${pipelineParams.clustername}"
        gitcred = "${pipelineParams.gitcred}"
        ecregistry = "${pipelineParams.ecregistry}"
        channel = "${pipelineParams.channel}" //Slack Chanel Name
        namespace = "${pipelineParams.namespace}"
        dpname = "${pipelineParams.dpname}" //in k8s deployment name
        podname = "${pipelineParams.podname}" //in k8s podname name
    }
    
    options {
        ansiColor('xterm')
    }

    agent {
        kubernetes {
            yaml """
    kind: Pod
    metadata:
    name: kaniko
    namespace: jenkins
    spec:
    serviceAccountName: jenkins
    containers:
    - name: kubectl
        image: 920048411410.dkr.ecr.us-east-1.amazonaws.com/jenkins:aws-kubectl
        command:
        - /bin/cat
        tty: true
    - name: git
        image: alpine/git
        command:
        - /bin/cat
        tty: true     
    - name: kaniko
        image: gcr.io/kaniko-project/executor:debug
        command:
        - sleep
        args:
        - 9999999
        tty: true
        env:
        - name: AWS_EC2_METADATA_DISABLED
        value: true
        - name: AWS_SDK_LOAD_CONFIG
        value: true
    """
        }
    }

    stages {
        stage('checkout git') {
        steps {
            slackSend channel: "#${channel}", color: 'warning', iconEmoji:":exclamation:", message: "Build Started - Job_Name:-${env.JOB_NAME} BuildNo:-${env.BUILD_NUMBER}  Branch:-${env.BRANCH_NAME} (<${env.BUILD_URL}|Open>)"
            cleanWs()
            container('git') {
            script {
                dir('./') {
                git url: scmUrl, branch: env.BRANCH_NAME, credentialsId: ${gitcred}
                }
                commitId = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                echo "${commitId}"
                echo "This is your branch ${env.BRANCH_NAME} and commit id is ${commitId}."
            }
            }
        }
        }

        stage('Build & Push Image') {
        steps {
            container(name: 'kaniko', shell: '/busybox/sh') {
            script {
                sh '''#!/busybox/sh
                dockerConfig=\${DOCKER_CONFIG:-/kaniko/.docker}
                [ -d \${dockerConfig} ] && echo "Docker directory Exists" || mkdir -p \${dockerConfig}
                echo '{"credsStore":"ecr-login"}' > \${dockerConfig}/config.json 
            '''
            
                sh """ #!/busybox/sh
                /kaniko/executor --dockerfile `pwd`/Dockerfile \
                                --context `pwd` \
                                --destination=${ecregistry}:${commitId}
                """
            }
            }
        }
        }
        
        stage('Deploy App to Dev Env') {
            when {
                    branch 'dev'
                }         
        steps {
            container('kubectl') {
                withAWS(credentials: "${awscred}", region: "${region}") {
                sh """
                aws eks --region ${region} update-kubeconfig --name ${pipelineParams.clustername}
                /usr/local/bin/kubectl get node
                /usr/local/bin/kubectl set image deployment/${dpname} ${podname}=${ecregistry}:${commitId} -n ${namespace}
                /usr/local/bin/kubectl rollout status deployment/${dpname} -n ${namespace}
                """
            }
            }
        }
        }


        stage('Deploy App to UAT Env') {
            when {
                    branch 'uat'
                }         
        steps {
            container('kubectl') {
                withAWS(credentials: '920048411410', region: 'us-east-1') {
                sh """
                aws eks --region us-east-1 update-kubeconfig --name nonprod-r-innovation
                /usr/local/bin/kubectl get node
                /usr/local/bin/kubectl set image deployment/${dpname} ${podname}=${ecregistry}:${commitId} -n valhalla-uat
                /usr/local/bin/kubectl rollout status deployment/${dpname} -n valhalla-uat
                """
            }
            }
        }
        }

    } //End Stage.

    post {
        // only triggered when blue or green sign
        success {
        slackSend channel: "#${channel}", color: 'good', iconEmoji: ":)", message: "Build deployed successfully - Job_Name:-${env.JOB_NAME} BuildNo:-${env.BUILD_NUMBER} Branch:-${env.BRANCH_NAME}  (<${env.BUILD_URL}|Open>)"
        }
        // triggered when red sign
        failure {
        slackSend failOnError:true, channel: "#${channel}", color: 'danger', iconEmoji: ':)', message:"Build failed  - Job_Name:-${env.JOB_NAME} BuildNo:-${env.BUILD_NUMBER}  Branch:-${env.BRANCH_NAME} (<${env.BUILD_URL}|Open>)"
        }
    }

    } //End Pipeline.
}
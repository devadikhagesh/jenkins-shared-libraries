def call(Map pipelineParams){
    pipeline {
        agent any

        stages {
            stage('Hello') {
                steps {
                    echo "Hello World ${pipelineParams.name}"
                }
            }
        }
    }
}

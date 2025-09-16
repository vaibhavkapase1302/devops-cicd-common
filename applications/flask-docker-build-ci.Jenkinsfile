// ci-pipeline.Jenkinsfile
@Library('devops-cicd-common') _

pipeline {
    agent any
    
    parameters {
        string(name: 'APP_NAME', defaultValue: 'flask-app', description: 'Application name')
        string(name: 'RELEASE_VERSION', defaultValue: 'v1.0.0-dev', description: 'Release version')
        string(name: 'APP_BRANCH_NAME', defaultValue: 'main', description: 'Application branch')
        choice(name: 'TAG_SOURCE', choices: ['true', 'false'], description: 'Tag source code')
    }
    
    stages {
        stage('CI Build') {
            steps {
                script {
                    dockerBuildCI([
                        APP_NAME: params.APP_NAME,
                        RELEASE_VERSION: params.RELEASE_VERSION,
                        APP_BRANCH_NAME: params.APP_BRANCH_NAME,
                        TAG_SOURCE: params.TAG_SOURCE,
                        CONTAINER_REGISTRY_URL: 'registry.digitalocean.com/flask-app-dev-registry',
                        CREDENTIALS_ID: 'vk-github-creds',
                        REGISTRY_CRED_ID: 'registry-flask-app-dev-registry',
                        REGISTRY_USERNAME: 'vaibhavkapase132@gmail.com'
                    ])
                }
            }
        }
    }
}

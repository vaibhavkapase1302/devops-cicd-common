// cd-pipeline.Jenkinsfile
@Library('devops-cicd-common') _

pipeline {
    agent any
    
    parameters {
        string(name: 'APP_NAME', defaultValue: 'flask-app', description: 'Application name')
        string(name: 'RELEASE_VERSION', defaultValue: 'v1.0.0-dev', description: 'Release version')
        string(name: 'ACCOUNT_NAME', defaultValue: 'dev', description: 'Account/Environment name')
        string(name: 'REPLICA_COUNT', defaultValue: '1', description: 'Number of replicas')
    }
    
    stages {
        stage('CD Deploy') {
            steps {
                script {
                    dockerDeployCD([
                        APP_NAME: params.APP_NAME,
                        RELEASE_VERSION: params.RELEASE_VERSION,
                        ACCOUNT_NAME: params.ACCOUNT_NAME,
                        REPLICA_COUNT: params.REPLICA_COUNT,
                        MANIFEST_BRANCH: 'main',
                        CREDENTIALS_ID: 'vk-github-creds',
                        MANIFEST_REPO_URL: 'https://github.com/vaibhavkapase1302/devops-cicd-common.git',
                        DO_TOKEN_CRED_ID: 'do-token'
                    ])
                }
            }
        }
    }
}

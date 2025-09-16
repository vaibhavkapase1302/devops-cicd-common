//Pipeline for plan terraform.

def modules = [:]

pipeline {
    agent any
    environment {
        accountId = ''
        TF_SCRIPT_REPO = 'https://github.com/vaibhavkapase1302/infra-terraform.git'
    }
    stages {
        stage('Setup') {
            steps {
                script {
                    // Set the Build Display name
                    currentBuild.displayName = "${ACCOUNT_NAME}-${BUILD_ID}"
                    
                    // Load the utilities
                    def rootDir = pwd()
                    modules.AccountLookup = load "${rootDir}/jenkins/utils/AccountLookup.Groovy"

                    // Get all the mapped values
                    def accountDetails = modules.AccountLookup.getAccountDetails(ACCOUNT_NAME)
                    if(!accountDetails) {
                        println "ERROR!!! cannot find env details for ${ACCOUNT_NAME}"
                        error("cannot find env details for ${ACCOUNT_NAME}")
                    }

                    // Set all the mapped values
                    accountId = accountDetails.accountId
                }
            }
        }
        stage('Checkout Terraform Script') {
            steps {
                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: SCRIPT_BRANCH_NAME]], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'tf-scripts']], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [[url: TF_SCRIPT_REPO, credentialsId: 'vk-github-creds']]
                ])
            }
        }
        stage('TF Plan') {
            steps {
                dir('tf-scripts') {
                    withCredentials([string(credentialsId: 'do-token', variable: 'DO_TOKEN')]) {
                        sh '''
                            terraform init -reconfigure

                            terraform plan -var-file=envs/dev/infra.tfvars
                        '''
                    }
                }
            }
        }
    }

    post {
        // Clean after build
        always {
            cleanWs cleanWhenAborted: false, cleanWhenFailure: false, cleanWhenNotBuilt: false, cleanWhenUnstable: false
        }
    }

}
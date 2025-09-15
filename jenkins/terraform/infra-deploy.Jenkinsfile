//Pipeline for deploying terraform.
// The following parameters should be defined in the pipeline:
// 1. ACCOUNT_NAME - String
// 2. TF_SCRIPT_REPO - String
// 3. CONFIG_BRANCH_NAME - String
// 4. SCRIPT_BRANCH_NAME - String

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
                    withAWS(credentials: 'aws-dev', region: 'ap-south-1') {
                        // sh "terraform init -reconfigure -backend-config=\"key=dev/terraform.tfstate\" -backend-config=\"region=ap-south-1\""
                        // sh "terraform plan -var-file=envs/dev/infra.tfvars"
                        sh '''
                            terraform init -reconfigure \
                                -backend-config="key=dev/terraform.tfstate" \
                                -backend-config="region=ap-south-1"

                            terraform plan -var-file=envs/dev/infra.tfvars
                        '''
                    }
                }
            }
        }
        stage('Approval for Apply') {
            steps {
                script {
                    // Add a timeout to the input step (e.g., 30 minutes = 1800 seconds)
                    timeout(time: 30, unit: 'MINUTES') {
                        input message: 'Want to deploy the infra? (Click "Proceed" to continue)', 
                              submitter: 'admin'
                    }
                }
            }
        }
        stage('TF apply') {
            steps {
                dir('tf-scripts') {
                    withAWS(credentials: 'aws-dev', region: 'ap-south-1') {
                        sh "terraform apply -var-file=envs/dev/infra.tfvars -auto-approve"
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
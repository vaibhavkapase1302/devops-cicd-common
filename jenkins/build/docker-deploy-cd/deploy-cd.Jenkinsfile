
def modules = [:]

pipeline {
    agent any
    parameters {
        string(name: 'REPLICA_COUNT', defaultValue: '1', description: 'Number of replicas to deploy')
    }
    environment {
        accountId = ''
        region = ''
        environment = ''
        namespace=''
        manifestBranch = 'main'
        ecr_repo_name=''

        ECR_REGION = 'ap-south-1'
        container_registry_id = '381305464391'
        container_registry_url = '381305464391.dkr.ecr.ap-south-1.amazonaws.com'
        IMAGE_TAG = "${params.RELEASE_VERSION}" // This assumes RELEASE_VERSION is defined as a string parameter in Jenkins.
        REPLICA_COUNT = "${params.REPLICA_COUNT}" // Using the REPLICA_COUNT parameter
    }
    
    stages {
        stage('Setup') {
            steps {
                script {
                    // Set the Build Display name
                    currentBuild.displayName = "${RELEASE_VERSION}-${BUILD_ID}"
                    
                    // Load the utilities
                    modules.AccountLookup = load "${WORKSPACE}/jenkins/utils/AccountLookup.Groovy"
                    modules.ApplicationInfoLookup = load "${WORKSPACE}/jenkins/utils/ApplicationInfoLookup.Groovy"
                    
                    // Get all the mapped values for Application Info details
                    def appinfoDetails = modules.ApplicationInfoLookup.getapplicationInfo(APP_NAME)
                    if(!appinfoDetails) {
                        println "ERROR!!! cannot find app details for ${APP_NAME}"
                        error("cannot find app details for ${APP_NAME}")
                    }
    

                    // Get all the mapped values for Account details
                    def accountDetails = modules.AccountLookup.getAccountDetails(ACCOUNT_NAME)
                    if (!accountDetails) {
                        echo "ERROR!!! cannot find env details for ${ACCOUNT_NAME}"
                        error "cannot find env details for ${ACCOUNT_NAME}"
                    }

                    // Set all the mapped values
                    accountId = accountDetails.accountId
                    region = accountDetails.region  
                    environment = accountDetails.env
                    application_name = appinfoDetails.application_name
                    namespace = appinfoDetails.namespace
                    ecr_repo_name = appinfoDetails.ecr_repo_name
                    
                    
                    println "accountId: ${accountId}, region: ${region}, environment: ${environment}, application_name: ${application_name}, namespace: ${namespace}, ecr_repo_name: ${ecr_repo_name}, IMAGE_TAG: ${IMAGE_TAG}, container_registry_id: ${container_registry_id}, CDmanifestBranch: ${manifestBranch}"
                }
            }
        }

        stage('Check Image Tag in ECR') {
            steps {
                script {
                    withAWS(credentials: 'aws-dev', region: 'ap-south-1') {
                        sh "aws ecr get-login-password --region ${ECR_REGION} | docker login --username AWS --password-stdin ${container_registry_url}"
                            def cmd = """
                                aws ecr describe-images --repository-name ${ecr_repo_name} --image-ids imageTag=${IMAGE_TAG} --registry-id ${container_registry_id} --region ${ECR_REGION} || echo 'ERROR'
                            """
                            def imageExists = sh(script: cmd, returnStdout: true).trim()
                            
                            if (!imageExists.contains('ERROR')) {
                                echo "Image with tag ${IMAGE_TAG} exists in ECR. Proceeding with deployment."
                            } else {
                                error "Image with tag ${IMAGE_TAG} does not exist in ECR. Deployment aborted."
                            }
                    }
                }
            }
        }

        stage('checkout TO manifest code') {
            when {
                expression {
                    // Only run this stage if the previous stage succeeded
                    currentBuild.result == null || currentBuild.result == 'SUCCESS'
                }
            }
            steps {
                checkout([
                    $class: 'GitSCM', 
                    branches: [[name: manifestBranch]], 
                    doGenerateSubmoduleConfigurations: false, 
                    extensions: [[$class: 'CleanCheckout']], 
                    submoduleCfg: [], 
                    userRemoteConfigs: [[credentialsId: 'vk-github-creds', url: 'https://github.com/vaibhavkapase1302/devops-cicd-common.git']]
                ])
            }
        }
    
        // Image Building
        stage('Applying env and Version') {
            steps {
                script {
                    try {
                        def deploymentPath = "kubernetes/manifests/${namespace}/${APP_NAME}"

                            // For applications
                            def deploymentFile = "${deploymentPath}/deployment.yml"
                            if (fileExists(deploymentFile)) {
                                // Modify deployment.yml with RELEASE_VERSION
                                println "Applying RELEASE_VERSION: ${RELEASE_VERSION} to deployment.yml"
                                sh "sed -i -e 's/RELEASE_VERSION/${RELEASE_VERSION}/g' ${deploymentFile}"
                                sh "sed -i -e 's/REPLICA_COUNT/${REPLICA_COUNT}/g' ${deploymentFile}"
                                sh "cat ${deploymentFile}"
                            } else {
                                echo "Deployment file not found for ${APP_NAME}"
                            }

                            // Check if external-secret.yml exists for the application
                            def secretFile = "${deploymentPath}/external-secret.yml"
                            if (fileExists(secretFile)) {
                                // Modify external-secret.yml with ENVIRONMENT
                                sh "sed -i -e 's/ENVIRONMENT/${environment}/g' ${secretFile}"
                                sh "cat ${secretFile}"
                            } else {
                                echo "External secret file not found for ${APP_NAME}"
                            }

                            // Check if service-account.yml exists for the backend
                            def serviceAccountFile = "kubernetes/manifests/${namespace}/${APP_NAME}/service-account.yml"
                            if (fileExists(serviceAccountFile)) {
                                // Modify service-account.yml with AccountId
                                sh "sed -i -e 's/ACCOUNT_ID/${accountId}/g' ${serviceAccountFile}"
                            } else {
                                echo "Service Account file not found for ${APP_NAME}"
                            }

                    } catch (Exception e) {
                        // Catch any exceptions and handle them (e.g., print an error message)
                        echo "An error occurred: ${e.message}"
                    }
                }
            }
        }
        stage('Integrate Jenkins with EKS Cluster and Deploy App') {
            steps {
                    script {
                        try {         
                            // Use resolved ACCOUNT_ID and REGION for deployment
                            withAWS(credentials: 'aws-dev', region: 'ap-south-1') {
                                // Modify Kubeconfig and apply manifests
                                sh "aws eks --region ${region} update-kubeconfig --name myapp-${environment}-cluster"
                                // Check if namespace exists and create if needed
                                def namespaceExists = sh(script: "kubectl get namespace ${namespace}", returnStatus: true) == 0
                                if (!namespaceExists) {
                                    sh "kubectl create namespace ${namespace}"
                                } else {
                                    echo "Namespace ${namespace} already exists. Skipping creation."
                                }
                                sh "kubectl apply -f kubernetes/manifests/${namespace}/${APP_NAME}/ --namespace=${namespace}"
                            }
                        } catch (Exception e) {
                            // Handle any exceptions
                            echo "An error occurred during deployment: ${e.message}"
                            currentBuild.result = 'FAILURE'
                        }
                }
            }
        }
    }
}


def modules = [:]

pipeline {
    agent any
    parameters {
        string(name: 'REPLICA_COUNT', defaultValue: '1', description: 'Number of replicas to deploy')
    }
    environment {
        doTokenCredentialId = ''
        clusterName = ''
        environment = ''
        namespace=''
        manifestBranch = 'main'
        ecr_repo_name=''

        // DigitalOcean related
        DO_TOKEN = credentials('do-token') // DigitalOcean Personal Access Token from Jenkins secrets
        DO_REGISTRY = 'registry.digitalocean.com'  // Default registry url prefix
        DO_REGISTRY_NAME = 'flask-app-dev-registry'  // Your registry name from secrets
        // ECR_clusterName = 'ap-south-1'
        // container_registry_id = '381305464391'
        // container_registry_url = '381305464391.dkr.ecr.ap-south-1.amazonaws.com'
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
                    doTokenCredentialId = accountDetails.doTokenCredentialId
                    clusterName = accountDetails.clusterName  
                    environment = accountDetails.env
                    application_name = appinfoDetails.application_name
                    namespace = appinfoDetails.namespace
                    ecr_repo_name = appinfoDetails.ecr_repo_name
                    
                    
                    println "doTokenCredentialId: ${doTokenCredentialId}, clusterName: ${clusterName}, environment: ${environment}, application_name: ${application_name}, namespace: ${namespace}, ecr_repo_name: ${ecr_repo_name}, IMAGE_TAG: ${IMAGE_TAG}, container_registry_id: ${container_registry_id}, CDmanifestBranch: ${manifestBranch}"
                }
            }
        }

        stage('Check Image Tag in DO Registry') {
            steps {
                script {
                    def checkCmd = """
                    doctl registry repository list-tags ${DO_REGISTRY_NAME}/${params.APP_NAME} --format Tag --no-header | grep -w ${IMAGE_TAG} || echo 'ERROR'
                    """
                    def imageExists = sh(script: checkCmd, returnStdout: true).trim()
                    if(imageExists == 'ERROR' || imageExists == '') {
                        error "Image tag ${IMAGE_TAG} does not exist in DigitalOcean registry."
                    } else {
                        echo "Image with tag ${IMAGE_TAG} found in DigitalOcean registry."
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

                            def serviceFile = "${deploymentPath}/service.yml"
                            sh "cat ${serviceFile}"

                    } catch (Exception e) {
                        // Catch any exceptions and handle them (e.g., print an error message)
                        echo "An error occurred: ${e.message}"
                    }
                }
            }
        }

        stage('Integrate Jenkins with DO Kubernetes Cluster and Deploy App') {
            steps {
                script {
                    try {
                        withCredentials([string(credentialsId: 'do-token', variable: 'DO_TOKEN')]) {
                            sh """
                                # Authenticate doctl with DigitalOcean token
                                doctl auth init -t ${DO_TOKEN}

                                # Download and save kubeconfig for your DO Kubernetes cluster
                                doctl kubernetes cluster kubeconfig save myapp-${environment}-cluster

                                # Check if namespace exists; create if not
                                if ! kubectl get namespace ${namespace}; then
                                    kubectl create namespace ${namespace}
                                else
                                    echo "Namespace ${namespace} already exists. Skipping creation."
                                fi

                                # Apply Kubernetes manifests to the target namespace
                                kubectl apply -f kubernetes/manifests/${namespace}/${APP_NAME}/ --namespace=${namespace}
                            """
                        }
                    } catch (Exception e) {
                        echo "An error occurred during deployment: ${e.message}"
                        currentBuild.result = 'FAILURE'
                        error(e.message)
                    }
                }
            }
        }
    }
}

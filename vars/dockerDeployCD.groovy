#!/usr/bin/env groovy

def call(Map config) {
    // Validate required parameters
    if (!config.APP_NAME) {
        error("APP_NAME is required")
    }
    if (!config.RELEASE_VERSION) {
        error("RELEASE_VERSION is required")
    }
    if (!config.ACCOUNT_NAME) {
        error("ACCOUNT_NAME is required")
    }
    
    // Set default values
    def replicaCount = config.REPLICA_COUNT ?: '1'
    def manifestBranch = config.MANIFEST_BRANCH ?: 'main'
    def credentialsId = config.CREDENTIALS_ID ?: 'vk-github-creds'
    def manifestRepoUrl = config.MANIFEST_REPO_URL ?: 'https://github.com/vaibhavkapase1302/devops-cicd-common.git'
    def doTokenCredId = config.DO_TOKEN_CRED_ID ?: 'do-token'
    
    def modules = [:]
    def doTokenCredentialId = ''
    def clusterName = ''
    def environment = ''
    def namespace = ''
    def applicationName = ''
    def ecrRepoName = ''

    pipeline {
        agent any
        
        parameters {
            string(name: 'REPLICA_COUNT', defaultValue: replicaCount, description: 'Number of replicas to deploy')
        }
        
        environment {
            doTokenCredentialId = ''
            clusterName = ''
            environment = ''
            namespace = ''
            manifestBranch = "${manifestBranch}"
            ecr_repo_name = ''
            // DigitalOcean related
            DO_TOKEN = credentials(doTokenCredId)
            DO_REGISTRY = 'registry.digitalocean.com'
            DO_REGISTRY_NAME = 'flask-app-dev-registry'
            IMAGE_TAG = "${config.RELEASE_VERSION}"
            REPLICA_COUNT = "${params.REPLICA_COUNT}"
        }
        
        stages {
            stage('Setup') {
                steps {
                    script {
                        // Set the Build Display name
                        currentBuild.displayName = "${config.RELEASE_VERSION}-${BUILD_ID}"
                        
                        // Load the utilities
                        modules.AccountLookup = load "${WORKSPACE}/jenkins/utils/AccountLookup.Groovy"
                        modules.ApplicationInfoLookup = load "${WORKSPACE}/jenkins/utils/ApplicationInfoLookup.Groovy"
                        
                        // Get all the mapped values for Application Info details
                        def appinfoDetails = modules.ApplicationInfoLookup.getapplicationInfo(config.APP_NAME)
                        if(!appinfoDetails) {
                            println "ERROR!!! cannot find app details for ${config.APP_NAME}"
                            error("cannot find app details for ${config.APP_NAME}")
                        }
                        
                        // Get all the mapped values for Account details
                        def accountDetails = modules.AccountLookup.getAccountDetails(config.ACCOUNT_NAME)
                        if (!accountDetails) {
                            echo "ERROR!!! cannot find env details for ${config.ACCOUNT_NAME}"
                            error "cannot find env details for ${config.ACCOUNT_NAME}"
                        }
                        
                        // Set all the mapped values
                        doTokenCredentialId = accountDetails.doTokenCredentialId
                        clusterName = accountDetails.clusterName
                        environment = accountDetails.env
                        applicationName = appinfoDetails.application_name
                        namespace = appinfoDetails.namespace
                        ecrRepoName = appinfoDetails.ecr_repo_name
                        
                        println "doTokenCredentialId: ${doTokenCredentialId}, clusterName: ${clusterName}, environment: ${environment}, application_name: ${applicationName}, namespace: ${namespace}, ecr_repo_name: ${ecrRepoName}, IMAGE_TAG: ${config.RELEASE_VERSION}, CDmanifestBranch: ${manifestBranch}"
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
                        userRemoteConfigs: [[credentialsId: credentialsId, url: manifestRepoUrl]]
                    ])
                }
            }
            
            stage('Applying env and Version') {
                steps {
                    script {
                        try {
                            def deploymentPath = "kubernetes/manifests/${namespace}/${config.APP_NAME}"
                            
                            // For applications
                            def deploymentFile = "${deploymentPath}/deployment.yml"
                            if (fileExists(deploymentFile)) {
                                // Modify deployment.yml with RELEASE_VERSION
                                println "Applying RELEASE_VERSION: ${config.RELEASE_VERSION} to deployment.yml"
                                sh "sed -i -e 's/RELEASE_VERSION/${config.RELEASE_VERSION}/g' ${deploymentFile}"
                                sh "sed -i -e 's/REPLICA_COUNT/${params.REPLICA_COUNT}/g' ${deploymentFile}"
                                sh "cat ${deploymentFile}"
                            } else {
                                echo "Deployment file not found for ${config.APP_NAME}"
                            }
                            
                            def serviceFile = "${deploymentPath}/service.yml"
                            sh "cat ${serviceFile}"
                        } catch (Exception e) {
                            echo "An error occurred: ${e.message}"
                        }
                    }
                }
            }
            
            stage('Integrate Jenkins with DO Kubernetes Cluster and Deploy App') {
                steps {
                    script {
                        try {
                            withCredentials([string(credentialsId: doTokenCredId, variable: 'DO_TOKEN')]) {
                                sh """
                                    # Authenticate doctl with DigitalOcean token
                                    doctl auth init -t ${DO_TOKEN}
                                    
                                    # Download and save kubeconfig for your DO Kubernetes cluster
                                    doctl kubernetes cluster kubeconfig save ${config.APP_NAME}-${environment}-cluster
                                    
                                    # Check if namespace exists; create if not
                                    if ! kubectl get namespace ${namespace}; then
                                        kubectl create namespace ${namespace}
                                    else
                                        echo "Namespace ${namespace} already exists. Skipping creation."
                                    fi
                                    
                                    # Apply Kubernetes manifests to the target namespace
                                    kubectl apply -f kubernetes/manifests/${namespace}/${config.APP_NAME}/ --namespace=${namespace}
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
}
def isValidVersionForEnvironment(version) {
    // Allow only [vX.Y.Z-dev] example- v1.0.1-dev format in dev environment
    if (version =~ /^v\d+\.\d+\.\d+-dev$/) {
        return true
    } else {
        return false
    }
}

def modules = [:]
def scmVars

pipeline {
    agent any
    environment {
        APP_GIT_COMMIT = ''
        app_repo_url = ''
        // ecr_repo_name = ''
        // REGION = 'ap-south-1'
        // container_registry_url = '381305464391.dkr.ecr.ap-south-1.amazonaws.com'
        container_registry_url = 'registry.digitalocean.com/flask-app-dev-registry'
        ecr_repo_name = 'flask-app'  // your app image name here
    }

    stages {
        stage('Setup') {
            steps {
                echo "PIPELINE_COMMIT_ID: ${GIT_COMMIT[0..7]}"
                script {
                    if (!isValidVersionForEnvironment(RELEASE_VERSION)) {
                        error "Invalid version can't be deployed in dev."
                    }

                    // Set the Build Display name
                    currentBuild.displayName = "${RELEASE_VERSION}-${BUILD_ID}"

                    // Load the utilities
                    modules.ApplicationInfoLookup = load "${WORKSPACE}/jenkins/utils/ApplicationInfoLookup.Groovy"
                    
                    // Get all the mapped values for Application Info details
                    def appinfoDetails = modules.ApplicationInfoLookup.getapplicationInfo(APP_NAME)
                    if(!appinfoDetails) {
                        println "ERROR!!! cannot find app details for ${APP_NAME}"
                        error("cannot find app details for ${APP_NAME}")
                    }

                    // Set all the mapped values
                    application_name = appinfoDetails.application_name
                    app_repo_url = appinfoDetails.app_repo_url
                    ecr_repo_name = appinfoDetails.ecr_repo_name
                    
                    println "application_name: ${application_name}, app_repo_url: ${app_repo_url}, ecr_repo_name:${ecr_repo_name}"
                }
            }
        }

        stage('Clean workspace') {
            steps {
                cleanWs deleteDirs: true
            }
        }

        stage('Checkout application code') {
            steps {
                dir("app-code") {
                    script {
                        scmVars = checkout([
                            $class: 'GitSCM', 
                            branches: [[name: "${APP_BRANCH_NAME}"]], 
                            doGenerateSubmoduleConfigurations: false, 
                            extensions: [[$class: 'CleanBeforeCheckout']], 
                            submoduleCfg: [], 
                            userRemoteConfigs: [[
                                credentialsId: 'vk-github-creds',  // Use the correct credential ID
                                url: "${app_repo_url}"
                            ]]
                        ])
                        
                        APP_GIT_COMMIT = "${scmVars.GIT_COMMIT[0..7]}"
                        echo "APP_GIT_COMMIT: ${APP_GIT_COMMIT}"
                    }
                }
            }
        }

        stage('Check Tag Version') {
            when {
                expression { params.TAG_SOURCE != 'false' }
            }
            steps {
                dir("app-code") {
                    script {
                        // Use the credentials directly in the Git operations
                        sh(returnStdout: true, script: """#!/bin/bash
                            if [ \$(git tag -l ${RELEASE_VERSION}) ]; then
                                echo "tag already exists"
                                exit 1
                            else
                                echo "this is new tag"
                                exit 0
                            fi
                        """)
                    }
                }
            }
        }

        stage('Building Image') {
            agent any
            steps {
                dir("app-code") {
                    script {
                        // Direct checkout without 'withCredentials'
                        checkout([
                            $class: 'GitSCM', 
                            branches: [[name: "${APP_BRANCH_NAME}"]], 
                            doGenerateSubmoduleConfigurations: false, 
                            extensions: [[$class: 'CleanBeforeCheckout']], 
                            submoduleCfg: [], 
                            userRemoteConfigs: [[
                                credentialsId: 'vk-github-creds',  // Use the correct credential ID
                                url: "${app_repo_url}"
                            ]]
                        ])

                        // Login to DigitalOcean registry using Jenkins secret token
                        withCredentials([string(credentialsId: 'registry-flask-app-dev-registry', variable: 'DO_REGISTRY_TOKEN')]) {
                            sh """
                                echo \$DO_REGISTRY_TOKEN | docker login registry.digitalocean.com --username vaibhavkapase132@gmail.com --password-stdin
                            """
                            
                            // Build the Docker image with your DO registry URL
                            sh """
                                docker buildx build -t ${container_registry_url}/${ecr_repo_name}:${RELEASE_VERSION} . \
                                --build-arg BUILD_VERSION=${RELEASE_VERSION} \
                                --build-arg GIT_COMMIT=${scmVars.GIT_COMMIT[0..7]}
                            """
                        }
                    }
                }
            }
        }

        stage('Build Manifest and Push') {
            agent any
            steps {
                script {
                    // Use `withAWS` to authenticate with the correct credentials
                    withCredentials([string(credentialsId: 'registry-flask-app-dev-registry', variable: 'DO_REGISTRY_TOKEN')]) {
                        sh """
                            echo \$DO_REGISTRY_TOKEN | docker login registry.digitalocean.com --username vaibhavkapase132@gmail.com --password-stdin
                            sh "docker push ${container_registry_url}/${ecr_repo_name}:${RELEASE_VERSION}"
                        """
                        // Remove the latest image if it exists
                        sh "docker rmi ${container_registry_url}/${ecr_repo_name}:latest || true"
                    }
                }
            }
        }

        stage('Tag Source') {
            when {
                expression { params.TAG_SOURCE != 'false' }
            }
            steps {
                dir("app-code") {
                    script {
                        // Use the 'vk-github-creds' credentials for Git operations
                        withCredentials([gitUsernamePassword(credentialsId: 'vk-github-creds',
                            gitToolName: 'git-tool')]) {
                            sh """  
                                git tag -a ${RELEASE_VERSION} ${APP_GIT_COMMIT} -m "Version ${RELEASE_VERSION}"
                                git push origin tag ${RELEASE_VERSION}
                            """
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs cleanWhenAborted: false, cleanWhenFailure: false, cleanWhenNotBuilt: false, cleanWhenUnstable: false
        }
    }
}
#!/usr/bin/env groovy

def call(Map config) {
    // Validate required parameters
    if (!config.APP_NAME) {
        error("APP_NAME is required")
    }
    if (!config.RELEASE_VERSION) {
        error("RELEASE_VERSION is required")
    }
    if (!config.APP_BRANCH_NAME) {
        error("APP_BRANCH_NAME is required")
    }
    
    // Set default values
    def containerRegistryUrl = config.CONTAINER_REGISTRY_URL ?: 'registry.digitalocean.com/flask-app-dev-registry'
    def tagSource = config.TAG_SOURCE ?: 'true'
    def credentialsId = config.CREDENTIALS_ID ?: 'vk-github-creds'
    def registryCredId = config.REGISTRY_CRED_ID ?: 'registry-flask-app-dev-registry'
    def registryUsername = config.REGISTRY_USERNAME ?: 'vaibhavkapase132@gmail.com'
    
    def modules = [:]
    def scmVars
    def appGitCommit = ''
    def applicationName = ''
    def appRepoUrl = ''
    def ecrRepoName = ''

    pipeline {
        agent any
        
        environment {
            APP_GIT_COMMIT = ''
            app_repo_url = ''
            ecr_repo_name = ''
            container_registry_url = "${containerRegistryUrl}"
        }
        
        stages {
            stage('Setup') {
                steps {
                    echo "PIPELINE_COMMIT_ID: ${GIT_COMMIT[0..7]}"
                    script {
                        if (!isValidVersionForEnvironment(config.RELEASE_VERSION)) {
                            error "Invalid version can't be deployed in dev."
                        }
                        
                        // Set the Build Display name
                        currentBuild.displayName = "${config.RELEASE_VERSION}-${BUILD_ID}"
                        
                        // Load the utilities
                        modules.ApplicationInfoLookup = load "${WORKSPACE}/jenkins/utils/ApplicationInfoLookup.Groovy"
                        
                        // Get all the mapped values for Application Info details
                        def appinfoDetails = modules.ApplicationInfoLookup.getapplicationInfo(config.APP_NAME)
                        if(!appinfoDetails) {
                            println "ERROR!!! cannot find app details for ${config.APP_NAME}"
                            error("cannot find app details for ${config.APP_NAME}")
                        }
                        
                        // Set all the mapped values
                        applicationName = appinfoDetails.application_name
                        appRepoUrl = appinfoDetails.app_repo_url
                        ecrRepoName = appinfoDetails.ecr_repo_name
                        
                        println "application_name: ${applicationName}, app_repo_url: ${appRepoUrl}, ecr_repo_name: ${ecrRepoName}"
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
                                branches: [[name: "${config.APP_BRANCH_NAME}"]],
                                doGenerateSubmoduleConfigurations: false,
                                extensions: [[$class: 'CleanBeforeCheckout']],
                                submoduleCfg: [],
                                userRemoteConfigs: [[
                                    credentialsId: credentialsId,
                                    url: "${appRepoUrl}"
                                ]]
                            ])
                            appGitCommit = "${scmVars.GIT_COMMIT[0..7]}"
                            echo "APP_GIT_COMMIT: ${appGitCommit}"
                        }
                    }
                }
            }
            
            stage('Check Tag Version') {
                when {
                    expression { tagSource != 'false' }
                }
                steps {
                    dir("app-code") {
                        script {
                            sh(returnStdout: true, script: """#!/bin/bash
                                if [ \$(git tag -l ${config.RELEASE_VERSION}) ]; then
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
                steps {
                    dir("app-code") {
                        script {
                            checkout([
                                $class: 'GitSCM',
                                branches: [[name: "${config.APP_BRANCH_NAME}"]],
                                doGenerateSubmoduleConfigurations: false,
                                extensions: [[$class: 'CleanBeforeCheckout']],
                                submoduleCfg: [],
                                userRemoteConfigs: [[
                                    credentialsId: credentialsId,
                                    url: "${appRepoUrl}"
                                ]]
                            ])
                            
                            // Login to DigitalOcean registry using Jenkins secret token
                            withCredentials([string(credentialsId: registryCredId, variable: 'DO_REGISTRY_TOKEN')]) {
                                sh """
                                    echo \$DO_REGISTRY_TOKEN | docker login registry.digitalocean.com --username ${registryUsername} --password-stdin
                                """
                                
                                // Build the Docker image with your DO registry URL
                                sh """
                                    docker buildx build -t ${containerRegistryUrl}/${ecrRepoName}:${config.RELEASE_VERSION} . \
                                        --build-arg BUILD_VERSION=${config.RELEASE_VERSION} \
                                        --build-arg GIT_COMMIT=${scmVars.GIT_COMMIT[0..7]}
                                """
                            }
                        }
                    }
                }
            }
            
            stage('Build Manifest and Push') {
                steps {
                    script {
                        withCredentials([string(credentialsId: registryCredId, variable: 'DO_REGISTRY_TOKEN')]) {
                            sh """
                                echo \$DO_REGISTRY_TOKEN | docker login registry.digitalocean.com --username ${registryUsername} --password-stdin
                                docker push ${containerRegistryUrl}/${ecrRepoName}:${config.RELEASE_VERSION}
                            """
                            
                            // Remove the latest image if it exists
                            sh "docker rmi ${containerRegistryUrl}/${ecrRepoName}:latest || true"
                        }
                    }
                }
            }
            
            stage('Tag Source') {
                when {
                    expression { tagSource != 'false' }
                }
                steps {
                    dir("app-code") {
                        script {
                            withCredentials([gitUsernamePassword(credentialsId: credentialsId, gitToolName: 'git-tool')]) {
                                sh """
                                    git tag -a ${config.RELEASE_VERSION} ${appGitCommit} -m "Version ${config.RELEASE_VERSION}"
                                    git push origin tag ${config.RELEASE_VERSION}
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
}

def isValidVersionForEnvironment(version) {
    // Allow only [vX.Y.Z-dev] example- v1.0.1-dev format in dev environment
    if (version =~ /^v\d+\.\d+\.\d+-dev$/) {
        return true
    } else {
        return false
    }
}
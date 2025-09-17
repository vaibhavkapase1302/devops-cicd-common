def call() {

def modules = [:]
def scmVars

    pipeline {
    agent any

    environment {
        
        APP_GIT_COMMIT = ''
        app_repo_url = ''
        
        container_registry_url = 'registry.digitalocean.com'
        registry_name = 'flask-app-dev-registry'
        repo_name = 'flask-app'      // image/repo name inside registry
    }

    stages {
        stage('Setup') {
            steps {
                echo "PIPELINE_COMMIT_ID: ${GIT_COMMIT[0..7]}"
                script {
                    // if (!isValidVersionForEnvironment(RELEASE_VERSION)) {
                    //     error "Invalid version can't be deployed in dev."
                    // }

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

        stage('SonarQube Analysis') {
            environment {
                scannerHome = tool 'sonar'
            }
            steps {
                dir("app-code") {
                    withSonarQubeEnv('sonar') {
                        sh "${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=${APP_NAME} \
                            -Dsonar.sources=. \
                            -Dsonar.exclusions=tests/**/* \
                            -Dsonar.java.binaries=. \
                            -Dsonar.sourceEncoding=UTF-8" \
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                dir("app-code") {
                    timeout(time: 10, unit: 'MINUTES') {
                        sleep 60
                        waitForQualityGate abortPipeline: true
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
                                docker buildx build -t ${container_registry_url}/${registry_name}/${repo_name}:${RELEASE_VERSION} . \
                                --build-arg BUILD_VERSION=${RELEASE_VERSION} \
                                --build-arg GIT_COMMIT=${scmVars.GIT_COMMIT[0..7]}
                            """
                        }
                    }
                }
            }
        }

        stage('Scan Image for Vulnerabilities') {
            agent any
            steps {
                script {
                    echo "🔍 Running vulnerability scan with Trivy..."
                    def imageFullName = "${container_registry_url}/${registry_name}/${repo_name}:${RELEASE_VERSION}"
                    def scanStatus = sh (
                        script: "trivy image --severity CRITICAL --exit-code 1 ${imageFullName}",
                        returnStatus: true
                    )
                    if (scanStatus != 0) {
                        error """
                            ❌ Vulnerabilities detected in image '${imageFullName}'.
                            Aborting pipeline to prevent deployment of a vulnerable image.
                            ➡️ Please fix the vulnerabilities and try again.
                        """
                    }
                    echo "✅ No critical vulnerabilities found. Proceeding to push the image."
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
                            docker push ${container_registry_url}/${registry_name}/${repo_name}:${RELEASE_VERSION}
                        """
                    }
                }
            }
        }
    }

        post {
            success {
                mail to: 'bose@mitaoe.ac.in',
                    subject: "SUCCESS: Job '${env.JOB_NAME} #${env.BUILD_NUMBER}'",
                    body: "Good news! The build succeeded: ${env.BUILD_URL}"
            }
            failure {
                mail to: 'bose@mitaoe.ac.in',
                    subject: "FAILURE: Job '${env.JOB_NAME} #${env.BUILD_NUMBER}'",
                    body: "The build failed: ${env.BUILD_URL}"
            }
        }
    }
}
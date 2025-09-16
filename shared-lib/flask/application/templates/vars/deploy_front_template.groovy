//
def call() {
pipeline{
    agent any
    stages{
        stage('workpace clean'){
            steps{
                cleanWs deleteDirs: true
            }

        }

        stage('checkout TO manifest code') {
            steps{
                dir("manifest-code") {
                git branch: '$MANIFEST_BRANCH_NAME', credentialsId: 'facctum_github_access', url: '$MANIFEST_REPO_NAME'
                }
            }
        }
         
         
        stage('Applying env and Version'){
            steps{
                dir("manifest-code") {
                    sh 'sed -i -e "s/Release_Version/${Release_Version}/g" ${Deployment_Path}/frontend_deployment.yml'
                }
            }
        }
        
        stage('Integrate Jenkins with EKS Cluster and Deploy App'){
            steps {
                dir("manifest-code"){
                    script{
                        ACCOUNT_ID = variable.getAccount(ENVIRONMENT)
                        REGION = variable.getRegion()
                    } 
                    withAWS(roleAccount: "${ACCOUNT_ID}", role: "terraform_role") {
                        script {
                            sh "aws eks --region ${REGION} update-kubeconfig --name ${tenant}-${ENVIRONMENT}-eks-cluster"
                            def namespaceExists = sh(script: "kubectl get namespace ${NameSpace}", returnStatus: true) == 0
                            if (!namespaceExists) {
                                // Namespace doesn't exist, so create it
                                sh "kubectl create namespace ${NameSpace}"
                            } else {
                                echo "Namespace ${NameSpace} already exists. Skipping creation."
                            }
                            sh "kubectl apply -f ${Deployment_Path}/ --namespace=${NameSpace}"
                        }
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
}

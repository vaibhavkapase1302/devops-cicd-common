## DevOps CI/CD Common

This repository provides reusable CI/CD assets for containerized applications and infrastructure:
- **Jenkins pipelines** for build, deploy, and Terraform workflows
- **Jenkins Shared Library** templates and helpers
- **Kubernetes manifests** for a sample Flask application

Use this repo as a central source of truth to standardize delivery pipelines across projects.

### What’s Included

- End-to-end CI (build and push container images)
- CD to Kubernetes (deploy containerized apps)
- Terraform workflows (plan/apply for infrastructure)
- Jenkins Shared Library steps for reuse across projects

### Repository Structure

```
jenkins/
  build/
    docker-build-ci/
      build-ci.Jenkinsfile        # CI: Build and push container images
    docker-deploy-cd/
      deploy-cd.Jenkinsfile       # CD: Deploy an image to environments
  terraform/
    infra-plan.Jenkinsfile        # Terraform plan
    infra-deploy.Jenkinsfile      # Terraform apply/deploy
  utils/
    AccountLookup.Groovy          # Utility: resolve account/env/credentials
    ApplicationInfoLookup.Groovy  # Utility: resolve app-specific metadata

kubernetes/
  manifests/
    flask/
      flask-app/
        deployment.yml            # K8s Deployment for Flask app
        service.yml               # K8s Service for Flask app

shared-lib/
  application/
    flask-app/
      build-flask-app.jenkinsfile   # Library pipeline for building Flask app
      deploy-flask-app.jenkinsfile  # Library pipeline for deploying Flask app

vars/
  build_flask_template.groovy     # Shared Library step for building Flask app
  deploy_flask_template.groovy    # Shared Library step for deploying Flask app
```

### Jenkins Requirements

- Jenkins with Pipeline (Declarative) and Shared Library support
- Credentials configured for:
  - **Git** read access to this repository
  - **Container registry** (e.g., Docker Hub, ECR, GCR)
  - **Kubernetes** access (kubeconfig or in-cluster)
  - **Terraform** backend/provider authentication
- Recommended plugins: Pipeline, Git, Credentials Binding, Kubernetes, Docker, Terraform (as applicable)

### Using the Jenkinsfiles (Direct)

You can reference the provided Jenkinsfiles directly in a multibranch or pipeline job.

Example: Build image CI using `jenkins/build/docker-build-ci/build-ci.Jenkinsfile`:

```groovy
@Library('devops-cicd-common') _

pipeline {
  agent any
  stages {
    stage('Build & Push') {
      steps {
        // Example parameters
        build_flask_template(
          appName: 'flask-app',
          dockerContext: '.',
          dockerfile: 'Dockerfile',
          imageRegistry: 'registry.example.com',
          imageRepo: 'team/flask-app',
          imageTag: env.BRANCH_NAME
        )
      }
    }
  }
}
```

For CD using `jenkins/build/docker-deploy-cd/deploy-cd.Jenkinsfile`, leverage `deploy_flask_template` with environment-specific parameters (namespace, replicas, image tag, etc.).

### Usage: Shared Library Steps

- `build_flask_template` builds and pushes a Docker image for a Flask app.
- `deploy_flask_template` deploys the image to a Kubernetes namespace with the desired replica count.

Common parameters:
- build: `appName`, `dockerContext`, `dockerfile`, `imageRegistry`, `imageRepo`, `imageTag`
- deploy: `appName`, `namespace`, `replicas`, `image`, `imageTag`

### Jenkins Shared Library Usage

Declare this repo as a Global Pipeline Library in Jenkins:
- Manage Jenkins → System → Global Pipeline Libraries
- Name: `devops-cicd-common`
- Default version: a branch or tag (e.g., `main`)
- Retrieval: Modern SCM → Git → Repository URL of this repo

Then, in your application pipelines, load and use the templates:

```groovy
@Library('devops-cicd-common') _

pipeline {
  agent any
  stages {
    stage('Build') {
      steps {
        build_flask_template(
          appName: 'flask-app',
          dockerContext: '.',
          dockerfile: 'Dockerfile',
          imageRegistry: 'registry.example.com',
          imageRepo: 'team/flask-app',
          imageTag: env.GIT_COMMIT.take(7)
        )
      }
    }
    stage('Deploy') {
      steps {
        deploy_flask_template(
          appName: 'flask-app',
          namespace: 'staging',
          replicas: 2,
          image: 'registry.example.com/team/flask-app',
          imageTag: env.GIT_COMMIT.take(7)
        )
      }
    }
  }
}
```

Parameters supported by the templates typically include:
- **build_flask_template**: `appName`, `dockerContext`, `dockerfile`, `imageRegistry`, `imageRepo`, `imageTag`
- **deploy_flask_template**: `appName`, `namespace`, `replicas`, `image`, `imageTag`, optional `values` or overlays

Refer to `vars/build_flask_template.groovy` and `vars/deploy_flask_template.groovy` for authoritative parameters.

### Kubernetes Manifests

The sample Flask app manifests are located under `kubernetes/manifests/flask/flask-app/`.
- `deployment.yml`: Deployment with container image reference and pod spec
- `service.yml`: Service exposing the Flask app within the cluster

You can kubectl apply them directly or integrate into your CD pipeline via `deploy_flask_template`.

### Terraform Pipelines

Jenkinsfiles under `jenkins/terraform/` provide standard plan/apply workflows:
- `infra-plan.Jenkinsfile`: Executes `terraform init` + `terraform plan`
- `infra-deploy.Jenkinsfile`: Executes `terraform apply` with appropriate approvals/guards

Provide the necessary backend configuration and provider credentials via Jenkins credentials and environment variables. Keep state backends (e.g., S3 + DynamoDB, GCS) secured and locked across pipelines.

### Utilities

The `jenkins/utils/` Groovy utilities can be imported in shared library steps or Jenkinsfiles to resolve application and account metadata consistently across pipelines.

### Quick Start

1) Configure this repo as a Global Library in Jenkins (`devops-cicd-common`).
2) Create an app pipeline that imports the library and calls `build_flask_template` and `deploy_flask_template`.
3) Ensure required credentials exist in Jenkins and your agents can access Docker/Kubernetes/Terraform.
4) Optionally, reference provided Jenkinsfiles under `jenkins/` directly for opinionated defaults.

### Prerequisites

- Jenkins server with Pipeline support and access to this repository
- Docker-capable agents and access to a container registry
- Kubernetes cluster access (kubeconfig or in-cluster)
- Terraform installed on agents and provider/backend credentials

### Setup

1) Configure this repository as a Global Pipeline Library in Jenkins:
   - Manage Jenkins → System → Global Pipeline Libraries
   - Name: `devops-cicd-common`
   - Default version: `main` (or a tag)
   - SCM: Git → set repository URL
2) Ensure credentials exist in Jenkins for:
   - Git read access
   - Container registry push/pull
   - Kubernetes context (if needed)
   - Terraform backend/provider

### Jenkins Access

- URL: `https://jenkins.kubetux.com/`
- Credentials: obtain username and password from the Jenkins admin. Do not store credentials in this repository or README.

Steps:
1) Navigate to `https://jenkins.kubetux.com/` and log in.
2) Configure Global Library (Manage Jenkins → System → Global Pipeline Libraries):
   - Name: `devops-cicd-common`
   - Default version: `main`
   - Retrieval method: Modern SCM → Git → set this repo URL
3) Add required credentials (Manage Jenkins → Credentials):
   - Git read token (if private)
   - Container registry credentials
   - Kubernetes kubeconfig or service account (if needed)
   - Terraform backend/provider credentials
4) Create a Pipeline or Multibranch Pipeline job for your application repository.
5) Use the examples in this README to call `build_flask_template` and `deploy_flask_template`.

### Contributing

Contributions are welcome. Please open an issue describing the change, then submit a PR with:
- Clear description and motivation
- Updated examples and documentation
- Backwards compatibility where possible

### License

Add your preferred license here (e.g., Apache-2.0 or MIT) and include the license file at the repo root.

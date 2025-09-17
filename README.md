# DevOps CI/CD Common assets repo:

This repository provides reusable CI/CD assets, Jenkins shared libraries, generic jenkins CI/CD pipelines and Kubernetes manifests templates to standardize and accelerate delivery pipelines for containerized applications and infrastructure.

---

## Features

- **Jenkins Pipelines:** Prebuilt Jenkinsfiles for CI (build), CD (deploy), and Terraform workflows.
- **Jenkins Shared Library:** Reusable Groovy steps for building and deploying Flask apps.
- **Kubernetes Manifests:** Sample manifests for deploying a Flask application.
- **Terraform Pipelines:** Jenkinsfile for infrastructure provisioning and management terraform init to apply.
- **Utilities:** Centralized Groovy lookup files for centralized account and application details.

---

## Repository Structure

```
devops-cicd-common/
│
├── jenkins/
│   ├── build/
│   │   └── docker-build-ci/
│   ├── terraform/
│   │   ├── infra-plan.Jenkinsfile
│   │   └── infra-deploy.Jenkinsfile
│   └── utils/
│       ├── AccountLookup.Groovy
│       └── ApplicationInfoLookup.Groovy
│
├── kubernetes/
│   └── manifests/
│       └── flask/
│           ├── deployment.yml
│           └── service.yml
│
├── shared-lib/
│   └── application/
│       └── flask-app/
│
└── vars/
    ├── build_flask_template.groovy
    └── deploy_flask_template.groovy
```

## Jenkins Access

- **Jenkins URL:** [https://jenkins.kubetux.com](https://jenkins.kubetux.com)
- **Note:** For username and password, please contact admin.

<img width="3382" height="840" alt="image" src="https://github.com/user-attachments/assets/ba73d00f-e8e9-492a-b3b0-cec76e417895" />

## Jenkins Practical Overview

Your Jenkins instance is organized into folders for different pipeline types:

- **ApplicationCICD:** Application build and deployment pipelines.
- **ApplicationCICD Shared-Lib:** Shared library jobs for reusable pipeline steps.
- **Infra:** Infrastructure provisioning and management (e.g., Terraform like tf plan, apply).
- **Testing:** Dedicated pipelines for running tests.

**Jenkins Dashboard Example:**

```
┌─────────────────────────────┐
│         Jenkins            │
│  https://jenkins.kubetux.com│
└─────────────┬───────────────┘
              │
      ┌───────┴────────┬─────────────┬─────────────┐
      │                │             │             │
      ▼                ▼             ▼             ▼
┌────────────┐  ┌────────────────┐ ┌────────────┐ ┌────────────┐
│Application │  │ApplicationCICD │ │   Infra    │ │  Testing   │
│   CICD     │  │ Shared-Lib     │ │   TODO     │ │            │
└────────────┘  └────────────────┘ └────────────┘ └────────────┘
```

---

## Prerequisites

- **Jenkins** with Pipeline and Shared Library support
- **Docker**-capable Jenkins agents
- **Kubernetes** cluster access (kubeconfig or in-cluster)
- **Terraform** installed on Jenkins agents
- **Credentials** in Jenkins for:
  - Git read access
  - Container registry (Docker Hub, ECR, GCR, etc.)
  - Kubernetes access
  - Terraform backend/provider

---

## Setup

### 1. Configure as a Jenkins Global Pipeline Library

1. Go to **Manage Jenkins → System → Global Pipeline Libraries**.
2. Add a new library:
   - **Name:** `flask-application-library`
   - **Default version:** (e.g., `main`)
   - **Retrieval method:** Modern SCM → Git
   - **Repository URL:** (URL of this repo)

### 2. Add Required Credentials

- **Git:** For private repos, add a Git read token. e.g. vk-github-creds
- **Container Registry:** Credentials for Docker Hub, ECR, GCR, etc. e.g. registry-flask-app-dev-registry
- **Kubernetes:** Kubeconfig or in-cluster credentials. e.g. do-token
- **Terraform:** Backend/provider credentials. e.g. AWS_ACCESS_KEY_ID & AWS_SECRET_ACCESS_KEY for DO similarr to AWS.

### 3. Reference Jenkinsfile

Use the provided Jenkinsfiles for opinionated CI/CD and Terraform workflows.

---

## Usage

### 1. Using Shared Library Steps in Jenkinsfile

To use the shared library for a Flask app, add the following at the top of your Jenkinsfile:

```groovy
@Library('flask-application-library')_
```

#### CI Example

```groovy
@Library('flask-application-library')_

stage('applicationCICD') {
    build_flask_template()
}
```

#### CD Example

```groovy
@Library('flask-application-library')_

stage('applicationCICD') {
    deploy_flask_template()
}
```

**Parameters:**

- **build_flask_template:** `APP_NAME`, `RELEASE_VERSION`, `APP_BRANCH_NAME`
- **deploy_flask_template:** `APP_NAME`, `REPLICA_COUNT`, `ACCOUNT_NAME`, `RELEASE_VERSION`

See [`vars/build_flask_template.groovy`](vars/build_flask_template.groovy) and [`vars/deploy_flask_template.groovy`](vars/deploy_flask_template.groovy) for details.

---

### 2. Using Provided Jenkinsfiles

- **CI:** `jenkins/build/docker-build-ci/build-ci.Jenkinsfile`
- **CD:** `jenkins/build/docker-deploy-cd/deploy-cd.Jenkinsfile`
- **Terraform Plan:** `jenkins/terraform/infra-plan.Jenkinsfile`
- **Terraform Deploy:** `jenkins/terraform/infra-deploy.Jenkinsfile`

For shared lib: 

**CI**

`shared-lib/application/flask-app/build-flask-app.jenkinsfile`

**CD**

`shared-lib/application/flask-app/deploy-flask-app.jenkinsfile`

Reference these Jenkinsfiles in your pipeline jobs as needed.

---

### 3. Kubernetes Manifests

Sample manifests for a Flask app are in `kubernetes/manifests/flask/`:

- `deployment.yml`: Deployment spec
- `service.yml`: Service spec

Apply directly with `kubectl` or via your CD pipeline.

---

### 4. Utilities

Import and use Groovy utilities from `jenkins/utils/` in your shared library steps or Jenkinsfiles for centralized account and application details.

---

## 5. Email Notifications

The CI/CD pipelines are configured to send email notifications automatically when a job completes:

* **On Success:** Team members receive a notification indicating the job succeeded, along with the build/deployment URL and version details.
* **On Failure:** Team members are alerted immediately about the failure to enable quick investigation.

### How to Configure

* Emails are sent via Jenkins' built-in mail step in the pipeline post conditions (`success`, `failure`).
* Update the recipient email addresses directly in the pipeline Jenkinsfiles or shared library scripts.
* Ensure Jenkins SMTP/email server settings are properly configured by your Jenkins administrator.
  - Typically, SMTP servers use port **465** for secure email sending over SSL.

---

## Quick Start

1. Access Jenkins at [https://jenkins.kubetux.com](https://jenkins.kubetux.com) (get credentials from admin).
2. Configure this repo as a Global Library in Jenkins.
3. Create a pipeline that imports the library and calls `build_flask_template` and `deploy_flask_template`.
4. Ensure all required credentials are set up in Jenkins.
5. Optionally, use the provided Jenkinsfiles for standard workflows.

---

applicationInfo = [

    // algo-test-api
    [application_name: "algo-test-api", namespace: "algo-test", app_repo_url: "https://github.com/vaibhavkapase1302/algo-test-api", ecr_repo_name: "algo-test/backend"],

    // algo-test-web
    [application_name: "algo-test-web", namespace: "algo-test", app_repo_url: "https://github.com/vaibhavkapase1302/algo-test-web", ecr_repo_name: "algo-test/frontend"],

    // flask-app
    [application_name: "flask-app",     namespace: "flask",     app_repo_url: "https://github.com/vaibhavkapase1302/DevOpsHiring",  ecr_repo_name: "flask-app-dev-registry"]

]

def getapplicationInfo(String NAME) {
    def appInfo = null
    applicationInfo.eachWithIndex{ it, i->
    //   println " item ${it.name}"
      if(it.application_name == NAME) {
          appInfo = it
      }
    }
    return appInfo 
}

return this

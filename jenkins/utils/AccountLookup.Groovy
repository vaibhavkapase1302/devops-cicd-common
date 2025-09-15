awsEnvironments = [
    // dev account
    [name: "dev",  doTokenCredentialId: "do-token",  clusterName: "myapp-dev-cluster",  env: "dev"]
]

def getAccountDetails(String NAME) {
    def awsEnv = null
    awsEnvironments.eachWithIndex{ it, i->
    //   println " item ${it.name}"
      if(it.name == NAME) {
          awsEnv = it
      }
    }
    return awsEnv
}

return this

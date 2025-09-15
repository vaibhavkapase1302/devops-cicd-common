awsEnvironments = [
    // dev account
    [name: "dev",  accountId: "381305464391",  region: "ap-south-1",  env: "dev"]
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

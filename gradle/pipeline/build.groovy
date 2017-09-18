node {

    def err = null

    try {
        bitbucketStatusNotify(buildState: 'INPROGRESS')

        stage 'checking out source'

        checkout scm

        def v = version()

        v = "$v.${env.BUILD_NUMBER}"

        echo "version info: $v"

        def deploy_env = 'develop'

        def docker_repo = 'agora.dequecloud.com:1080'

        // use branch name would be better, but can't get the value from jenkins pipeline
        if (env.JOB_NAME.endsWith("-prod")) {
            deploy_env = 'prod'
            docker_repo = 'agora.dequecloud.com:1083'
            env.RELEASE_BUILD=true
        } else if (env.JOB_NAME.endsWith("-qa")) {
            deploy_env = 'qa'
            docker_repo = 'agora.dequecloud.com:1083'
            env.RELEASE_BUILD=true
        }

        stage 'building'
        sh './gradlew clean build'

//        stage 'generating test and code analysis reports'
//
//        step([$class: 'Publisher', failedFails: 1, failedSkips: 5, unstableSkips: 5])
//
//        step([$class: 'CheckStylePublisher', defaultEncoding: '', failedNewAll: '1', failedTotalAll: '1', healthy: '0', pattern: '**/checkstyle/main.xml', unHealthy: '1', unstableNewAll: '1', unstableTotalAll: '1'])
//
//        step([$class: 'FindBugsPublisher', canComputeNew: false, defaultEncoding: '', excludePattern: '', failedTotalAll: '1', healthy: '0', includePattern: '', pattern: '**/findbugs/main.xml', unHealthy: '1', unstableTotalAll: '1'])
//
//        step([$class: 'PmdPublisher', canComputeNew: false, defaultEncoding: '', failedTotalAll: '1', healthy: '0', pattern: '**/pmd/main.xml', unHealthy: '1', unstableTotalAll: '1'])
//
//        step([$class: 'JacocoPublisher'])

        stage "Build docker image"

        // build data-ingest
        sh '''find data-ingest/build/libs -iname "data-ingest-*[0-9].*[0-9].*[0-9].war" -exec cp "{}" data-ingest/src/main/docker \\;'''

        def data_ingest = docker.build("$docker_repo/dashboard-data-ingest:$v", 'data-ingest/src/main/docker')

        data_ingest.push() // record this snapshot (optional)

//        stage "Deploy to integration server"
//        // deploy to kubernetes cluster
//
//        build job: '/Infrastructure/DeployApp', parameters: [
//                string(name: 'DEQUE_APP', value: 'dashboard-data-ingest'),
//                string(name: 'DEPLOY_ENVIRONMENT', value: deploy_env),
//                string(name: 'DEPLOY_OPERATION', value: 'create_all'),
//                string(name: 'MANIFEST_VERSION', value: "$v")
//        ], quietPeriod: 20

//        stage "Integration test"

//        try {
//            retry(2) {
//                sleep 360
//                sh 'newman run postman-scripts/Deque-ECP-Custom-Rules.postman_collection.json -e postman-scripts/environments/Integration-gce-west-it.postman_environment.json -k'
//                sh './gradlew integTest -DENABLE_INTEGRATION_TEST=true'
//            }
//        } finally {
//            build job: '/Infrastructure/DeployApp', parameters: [
//                    string(name: 'DEQUE_APP', value: 'ecp-custom-rules'),
//                    string(name: 'DEPLOY_ENVIRONMENT', value: deploy_env),
//                    string(name: 'DEPLOY_OPERATION', value: 'delete_all'),
//                    string(name: 'MANIFEST_VERSION', value: "$v")
//            ], quietPeriod: 20
//        }

        stage "Publish Jars to Artifactory"

        sh './gradlew artifactoryPublish'

        stage "Deploy to QA server"
        data_ingest.push("latest")

        build job: '/Infrastructure/DeployApp', parameters: [
                string(name: 'DEQUE_APP', value: 'dashboard-data-ingest'),
                string(name: 'DEPLOY_ENVIRONMENT', value: deploy_env),
                string(name: 'DEPLOY_OPERATION', value: 'restart_all'),
                string(name: 'MANIFEST_VERSION', value: "$v")
        ], quietPeriod: 20

        if (env.RELEASE_BUILD) {
            // workaround kubernetes permission only have 444 for secret volume
            sh 'chmod 400 /root/.ssh/bitbucket'

            sh 'git config user.email "jenkins@deque.com"'
            sh 'git config user.name "Jenkins Build"'
            sh "git tag -a $v -m 'Jenkins build tag'"
            sh "git push 'git@bitbucket.org:wenlai/dashboard-data-ingest.git' --tags"
        } else {
            echo "Development branch: No tagging"
        }

        bitbucketStatusNotify(buildState: 'SUCCESSFUL')

        stage "Update Jira with updates"

        step([$class       : 'hudson.plugins.jira.JiraIssueUpdater',
              issueSelector: [$class: 'hudson.plugins.jira.selector.DefaultIssueSelector'],
              scm          : scm,
              comment      : "Jenkins integrated in ${env.JOB_NAME} ${env.BUILD_NUMBER} (${currentBuild.result})."
        ])


    } catch (caughtErr) {
        err = caughtErr
    } finally {
        if (err) {
            currentBuild.result = 'FAILURE'
        } else {
            currentBuild.result = 'SUCCESS'
        }

        if (err != null) {

            echo "build error: $err"

            def to = emailextrecipients([
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'DevelopersRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
            ])

            slackSend channel: 'comply-build', color: 'danger', message: "\'${env.JOB_NAME}\' (${env.BUILD_NUMBER})  has finished with ${currentBuild.result}. Build URL: ${env.BUILD_URL}. Build Error: $err", teamDomain: 'deque', token: 'BhW2Vk2kdmiumfZRfrLMGxon'

            emailext attachLog: true, body: "Build from ${env.BUILD_URL} failed with error $err", mimeType: 'text/html', recipientProviders: [
                    [$class: 'CulpritsRecipientProvider'],
                    [$class: 'RequesterRecipientProvider']
            ], subject: "\'${env.JOB_NAME}\' (${env.BUILD_NUMBER})  has finished with ${currentBuild.result}", to: 'andrew.fang'
        } else {
            slackSend channel: 'comply-build', color: 'good', message: "\'${env.JOB_NAME}\' (${env.BUILD_NUMBER})  has finished with ${currentBuild.result}. Build URL: ${env.BUILD_URL}", teamDomain: 'deque', token: 'BhW2Vk2kdmiumfZRfrLMGxon'
        }
    }
}

def version() {
    def matcher = readFile('gradle.properties') =~ 'dashboardDataIngestVersion=(.+)\n'
    matcher ? matcher[0][1] : null
}

//"Jenkins Pipeline is a suite of plugins which supports implementing and integrating continuous delivery pipelines into Jenkins. Pipeline provides an extensible set of tools for modeling delivery pipelines "as code" via the Pipeline DSL."
//More information can be found on the Jenkins Documentation page https://jenkins.io/doc/
pipeline {
    agent none
    options {
        buildDiscarder(logRotator(numToKeepStr:'25'))
    }
    triggers {
        cron('H H(20-23) * * *')
    }
    environment {
        DOCS = 'distribution/docs'
        ITESTS= 'distribution/test/itests/test-itests-ddf'
    }
    stages {
        stage('Setup') {
            steps{
                slackSend color: 'good', message: "STARTED: ${JOB_NAME} ${BUILD_NUMBER} ${BUILD_URL}"
            }
        }
        stage('Static Analysis') {
            steps {
                parallel(
                        codecov: {
                            node('linux-small') {
                                checkout scm
                                withMaven(maven: 'M3', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings') {
                                    withCredentials([string(credentialsId: 'DDF-Codecov-Token', variable: 'DDF_CODECOV_TOKEN')]) {
                                        sh 'mvn install -Dcheckstyle.skip=true -pl !$DOCS -pl !$ITESTS'
                                        sh 'curl -s https://codecov.io/bash | bash -s - -t ${DDF_CODECOV_TOKEN}'
                                    }
                                }
                            }
                        }
                )
            }
        }
    }
}

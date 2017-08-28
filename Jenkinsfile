//"Jenkins Pipeline is a suite of plugins which supports implementing and integrating continuous delivery pipelines into Jenkins. Pipeline provides an extensible set of tools for modeling delivery pipelines "as code" via the Pipeline DSL."
//More information can be found on the Jenkins Documentation page https://jenkins.io/doc/
pipeline {
    agent none
    environment {
        DOCS = 'distribution/docs'
        ITESTS= 'distribution/test/itests/test-itests-ddf'
    }
    stages {
        stage('Parallel Build') {
            // TODO DDF-2971 refactor this stage from scripted syntax to declarative syntax to match the rest of the stages - https://issues.jenkins-ci.org/browse/JENKINS-41334
            steps{
                parallel(
                    linux: {
                        node('linux-large') {
                            retry(3) {
                                checkout scm
                            }
                            timeout(time: 3, unit: 'HOURS') {
                                withMaven(maven: 'M3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings') {
                                    sh 'mvn clean install -B -T 1C -pl !$ITESTS'
                                    sh 'mvn install -B -Dmaven.test.redirectTestOutputToFile=true -pl $ITESTS -nsu'
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

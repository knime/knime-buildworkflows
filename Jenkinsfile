#!groovy
def BN = (BRANCH_NAME == 'master' || BRANCH_NAME.startsWith('releases/')) ? BRANCH_NAME : 'releases/2023-07'

library "knime-pipeline@$BN"

properties([
    pipelineTriggers([
        upstream("knime-workbench/${env.BRANCH_NAME.replaceAll('/', '%2F')}" +
            ", knime-json/${env.BRANCH_NAME.replaceAll('/', '%2F')}")
    ]),
    parameters(workflowTests.getConfigurationsAsParameters()),
    buildDiscarder(logRotator(numToKeepStr: '5')),
    disableConcurrentBuilds()
])

try {
    knimetools.defaultTychoBuild('org.knime.update.buildworkflows')

    configs = [
        "Workflowtests" : {
            workflowTests.runTests(
                dependencies: [
                    repositories: [
                        "knime-buildworkflows",
                        "knime-com-shared",
                        "knime-datageneration",
                        "knime-distance",
                        "knime-ensembles",
                        "knime-filehandling",
                        "knime-gateway",
                        "knime-javasnippet",
                        "knime-jep",
                        "knime-js-base",
                        "knime-json",
                        "knime-kerberos",
                        "knime-productivity-oss",
                        "knime-reporting",
                        "knime-server-client",
                        "knime-stats",
                        "knime-virtual"
                    ]
                ]
            )
        },
        "Filehandlingtests" : {
            workflowTests.runFilehandlingTests (
                dependencies: [
                    repositories: [
                        "knime-buildworkflows",
                        "knime-com-shared",
                        "knime-distance",
                        "knime-ensembles",
                        "knime-gateway",
                        "knime-javasnippet",
                        "knime-js-base",
                        "knime-productivity-oss",
                        "knime-reporting",
                        "knime-server-client"
                    ]
                ],
            )
        }
    ]

    parallel configs

    stage('Sonarqube analysis') {
        env.lastStage = env.STAGE_NAME
        workflowTests.runSonar()
    }
 } catch (ex) {
     currentBuild.result = 'FAILURE'
     throw ex
 } finally {
     notifications.notifyBuild(currentBuild.result);
 }

/* vim: set ts=4: */

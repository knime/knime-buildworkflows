#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

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
			            "knime-distance",
			            "knime-server-client",
			            "knime-buildworkflows",
			            "knime-com-shared",
			            "knime-datageneration",
			            "knime-ensembles",
			            "knime-filehandling",
			            "knime-kerberos",
			            "knime-javasnippet",
			            "knime-jep",
			            "knime-js-base",
			            "knime-json",
			            "knime-productivity-oss",
			            "knime-reporting",
			            "knime-server-api",
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
						"knime-javasnippet",
						"knime-ensembles",
						"knime-distance",
						"knime-server-client",
						"knime-js-base",
						"knime-com-shared",
			            		"knime-productivity-oss",
						"knime-reporting"
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

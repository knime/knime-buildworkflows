#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
	pipelineTriggers([
		upstream('knime-workbench/' + env.BRANCH_NAME.replaceAll('/', '%2F')),
		upstream('knime-json/' + env.BRANCH_NAME.replaceAll('/', '%2F'))
	]),
	buildDiscarder(logRotator(numToKeepStr: '5')),
	disableConcurrentBuilds()
])

try {
	knimetools.defaultTychoBuild('org.knime.update.buildworkflows')

	workflowTests.runTests(
		dependencies: [
			repositories: ["knime-buildworkflows","knime-json","knime-datageneration","knime-ensembles","knime-distance","knime-javasnippet","knime-filehandling","knime-js-base","knime-jep"]
		]
	)

	stage('Sonarqube analysis') {
		env.lastStage = env.STAGE_NAME
		workflowTests.runSonar()
	}
 } catch (ex) {
	 currentBuild.result = 'FAILED'
	 throw ex
 } finally {
	 notifications.notifyBuild(currentBuild.result);
 }

/* vim: set ts=4: */

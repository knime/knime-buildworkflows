#!groovy
def BN = BRANCH_NAME == "master" || BRANCH_NAME.startsWith("releases/") ? BRANCH_NAME : "master"

library "knime-pipeline@$BN"

properties([
	// provide a list of upstream jobs which should trigger a rebuild of this job
	pipelineTriggers([
		upstream('knime-base/' + env.BRANCH_NAME.replaceAll('/', '%2F')),
		upstream('knime-workbench/' + env.BRANCH_NAME.replaceAll('/', '%2F')),
		upstream('knime-json/' + env.BRANCH_NAME.replaceAll('/', '%2F'))
	]),
	buildDiscarder(logRotator(numToKeepStr: '5')),
	disableConcurrentBuilds()
])

try {
	// provide the name of the update site project
	knimetools.defaultTychoBuild('org.knime.update.buildworkflows')

	workflowTests.runTests(
		"org.knime.features.buildworkflows.testing.feature.group",
		false,
		["knime-buildworkflows","knime-workbench","knime-core","knime-base", "knime-shared", "knime-tp","knime-json","knime-datageneration","knime-filehandling"]
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

#!groovy

/*
 * Copyright (c) 2017 SAP SE or an SAP affiliate company. All rights reserved.
 * This file is licensed under the Apache Software License, v. 2 except as noted
 * otherwise in the LICENSE file at the root of the repository.
 */

def jdksRepository() {
    return "${env.JDKS_REPOSITORY ? env.JDKS_REPOSITORY : 'https://github.infra.hana.ondemand.com/hcpperf/jdks-for-validation.git'}"
}

def jdksRepositoryBranch() {
    return "${env.JDKS_REPOSITORY_BRANCH ? env.JDKS_REPOSITORY_BRANCH : 'linux-x86_64'}"
}

node {

    final gradleHome = tool name: 'default', type: 'hudson.plugins.gradle.GradleInstallation'

    final jdksRepository = jdksRepository()

    final jdksRepositoryBranch = jdksRepositoryBranch()

    stage('Checkout') {
        // clean workspace
        deleteDir()

        // checkout sources with respect to multi-branch support
        checkout scm
    }

    stage('Download JDKs') {

        dir('test-e2e/src/test/resources/jdks') {
            /*
             * Get rid of skeleton folder and .gitkeep file, or git will refuse to clone in there
             */
            deleteDir()
        }

        dir('test-e2e/src/test/resources') {
            sh "git clone ${jdksRepository} jdks --quiet --single-branch --branch ${jdksRepositoryBranch}"
        }
    }

    stage('Build') {
        try {
            sh "${gradleHome}/bin/gradle clean build"
        } catch (Exception e) {
            try {
                // store dump fails from failed e2e tests
                archiveArtifacts artifacts: 'test-e2e/build/oom/*.hprof', fingerprint: false
            } finally {
                // Rethrow with suppression of whatever failure comes out of archiving
                throw e;
            }
        } finally {
            // archive findbugs reports if any
            archiveArtifacts artifacts: '**/build/reports/findbugs/**', fingerprint: false

            // archive checkstyle reports if any
            archiveArtifacts artifacts: '**/build/reports/checkstyle/*', fingerprint: false

            // archive jacoco reports if any
            archiveArtifacts artifacts: '**/build/reports/jacoco/**', fingerprint: false

            // report test results in any
            junit allowEmptyResults: false, testResults: 'agent/build/test-results/test/*.xml'
            junit allowEmptyResults: false, testResults: 'test-api/build/test-results/test/*.xml'
            junit allowEmptyResults: false, testResults: 'test-app/build/test-results/test/*.xml'
            junit allowEmptyResults: true, testResults: 'test-e2e/build/test-results/test/*.xml'
            junit allowEmptyResults: false, testResults: 'test-e2e/build/test-results/itest-*/*.xml'
        }

        // store heap-dump-agent jar artifact as ARTIFACTS
        archiveArtifacts artifacts: 'agent/build/libs/*.jar', fingerprint: true
    }

}

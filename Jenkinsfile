def retryOnAbort(int maxRetries = 1, Closure body) {
    int attempt = 0
    while (true) {
        try {
            body()
            return
        } catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e) {
            attempt++
            if (attempt > maxRetries) {
                throw e
            }
            echo "Stage aborted (attempt ${attempt}/${maxRetries}) – retrying after 5 s"
            sleep time: 5, unit: 'SECONDS'
        }
    }
}

pipeline {
    agent any

    tools {
        jdk 'Java 21'
        maven 'Maven 3.8.1'
    }

    environment {
        JAVA_HOME    = tool 'Java 21'
        M2_HOME      = tool 'Maven 3.8.1'
        SCANNER_HOME = tool 'sonar-scanner'
        PATH         = "${JAVA_HOME}/bin:${M2_HOME}/bin:${SCANNER_HOME}/bin:${env.PATH}"
    }

    triggers { githubPush() }

    stages {

        stage('Code Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/Sai-Roopesh/pipeline-test.git', credentialsId: 'git-cred-test'
            }
        }

        stage('Build Application') {
            steps {
                sh 'mvn clean verify'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        stage('SAST Scanning Pre') {
            steps {
                script {
                    retryOnAbort(2) {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            withSonarQubeEnv('sonar') {
                                sh """
                                    sonar-scanner \
                                      -Dsonar.projectKey=pipeline-test \
                                      -Dsonar.sources=src/main/java \
                                      -Dsonar.login=$SONAR_TOKEN
                                """
                            }
                        }
                    }
                }
            }
        }

        stage('SAST Scanning Post') {
            steps {
                script {
                    retryOnAbort(2) {
                        timeout(time: 30, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: true
                        }
                    }
                }
            }
        }

        stage('Push to Nexus') {
            steps {
                script {
                    retryOnAbort(2) {
                        withMaven(globalMavenSettingsConfig: 'global-settings', jdk: 'Java 21', maven: 'Maven 3.8.1') {
                            sh 'mvn deploy -DskipTests'
                        }
                    }
                }
            }
        }

        stage('Build & Push Container to Image Registry') {
            steps {
                script {
                    retryOnAbort(2) {
                        withCredentials([usernamePassword(credentialsId: 'docker-cred', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                            docker.withRegistry('', 'docker-cred') {
                                def img = docker.build("${DOCKER_USER}/boardgame:${BUILD_NUMBER}")
                                img.push()
                            }
                        }
                    }
                }
            }
        }

        stage('Container Scanning') {
            agent { label 'trivy' }
            steps {
                script {
                    retryOnAbort(1) {
                        withCredentials([usernamePassword(credentialsId: 'docker-cred', usernameVariable: 'DOCKER_USER', passwordVariable: 'IGNORED')]) {
                            sh '''
                              CACHE_DIR="$HOME/.trivy-cache"
                              mkdir -p "$CACHE_DIR"
                              # refresh vulnerability database then scan the image
                              trivy image --download-db-only --cache-dir "$CACHE_DIR"
                              trivy image \
                                --cache-dir "$CACHE_DIR" \
                                --timeout 30m \
                                --exit-code 1 \
                                --severity HIGH,CRITICAL \
                                --format table \
                                -o trivy.txt \
                                ${DOCKER_USER}/boardgame:${BUILD_NUMBER}
                            '''
                        }
                    }
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy.txt', fingerprint: true
                }
            }
        }

        stage('Deployment to k8s') {
            steps {
                script {
                    retryOnAbort(1) {
                        withCredentials([usernamePassword(credentialsId: 'docker-cred', usernameVariable: 'DOCKER_USER', passwordVariable: 'IGNORED')]) {
                            sh '''
                                export IMG_TAG="$DOCKER_USER/boardgame:${BUILD_NUMBER}"
                                envsubst < /var/lib/jenkins/k8s-manifest/deployment.yaml > rendered-deployment.yaml
                            '''
                        }
                        archiveArtifacts artifacts: 'rendered-deployment.yaml', fingerprint: true
                        withKubeConfig(credentialsId: 'k8s-config') {
                            sh '''
                                kubectl apply -f rendered-deployment.yaml --record
                                kubectl rollout status deployment/nginx-deployment --timeout=1200s
                                echo "Verification Time: $(date '+%Y-%m-%d %H:%M:%S')"
                                kubectl get pods -l app=nginx \
                                  -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready,START_TIME:.status.startTime' \
                                  --no-headers
                                kubectl get svc
                                echo "Verification Time: $(date '+%Y-%m-%d %H:%M:%S')"
                            '''
                        }
                    }
                }
            }
        }
    }

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            script {
                def email = sh(script: "git --no-pager show -s --format='%ae'", returnStdout: true).trim()
                mail to: email,
                     subject: "✅ Deployment Successful: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                     body: """
Hello,

Your commit triggered a successful deployment for job '${env.JOB_NAME}' (build #${env.BUILD_NUMBER}).

See details: ${env.BUILD_URL}

Best,
Jenkins CI/CD
"""
            }
        }
    }
}

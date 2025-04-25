//comment-added
pipeline {
    agent any

    tools {
        jdk   'Java 21'
        maven 'Maven 3.8.1'
    }

    environment {
        JAVA_HOME       = tool 'Java 21'
        M2_HOME         = tool 'Maven 3.8.1'
        SCANNER_HOME    = tool 'sonar-scanner'
        PATH            = "${JAVA_HOME}/bin:${M2_HOME}/bin:${SCANNER_HOME}/bin:${env.PATH}"

        /* Trivy */
        TRIVY_CACHE_DIR = "/var/lib/jenkins/trivy-cache"
        TRIVY_TEMPLATE  = "/usr/local/share/trivy/templates/html.tpl"

        /* Docker config lives inside the workspace → wiped after each build   */
        DOCKER_CONFIG   = "${WORKSPACE}/.docker"
    }

    triggers { githubPush() }

    stages {

        /* ---------- source, build, quality ---------- */
        stage('Checkout') {
            steps {
                git branch: 'main',
                    url: 'https://github.com/Sai-Roopesh/pipeline-test.git',
                    credentialsId: 'git-cred-test'
            }
        }

        stage('Build & Test') {
            steps { sh 'mvn clean verify' }
            post {
                success {
                    archiveArtifacts artifacts: 'target/my-app-1.0.1.jar', fingerprint: true
                }
            }
        }

        stage('Smoke Test') {
            steps { sh 'java -jar target/my-app-1.0.1.jar | grep "Hello, Jenkins!"' }
        }

        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('sonar') {
                        sh """
                            sonar-scanner \
                              -Dsonar.projectKey=pipeline-test \
                              -Dsonar.sources=src/main/java \
                              -Dsonar.java.binaries=target/classes \
                              -Dsonar.login=${SONAR_TOKEN}
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        stage('Publish to Nexus') {
            steps {
                withMaven(globalMavenSettingsConfig: 'global-settings',
                          jdk: 'Java 21',
                          maven: 'Maven 3.8.1') {
                    sh 'mvn deploy -DskipTests'
                }
            }
        }

        /* ---------- secure Docker build & push ---------- */
        stage('Build & Push Docker image') {
            environment { DOCKER_CONFIG = "${WORKSPACE}/.docker" }   // shadow global if you prefer
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-cred',
                                                  usernameVariable: 'DOCKER_USER',
                                                  passwordVariable: 'DOCKER_PASS')]) {
                    sh '''
                        set -e
                        mkdir -p "$DOCKER_CONFIG"

                        # safe login (no password on CLI)
                        echo "$DOCKER_PASS" | docker login --username "$DOCKER_USER" --password-stdin

                        # build & push version-tagged image
                        docker build -t "$DOCKER_USER/boardgame:${BUILD_NUMBER}" .
                        docker push      "$DOCKER_USER/boardgame:${BUILD_NUMBER}"

                        # also push :latest for convenience
                        docker tag "$DOCKER_USER/boardgame:${BUILD_NUMBER}" "$DOCKER_USER/boardgame:latest"
                        docker push "$DOCKER_USER/boardgame:latest"
                    '''
                }
            }
            post {
                always {
                    /* extra-tidy: remove any credential artefacts */
                    sh 'rm -rf "$DOCKER_CONFIG"'
                }
            }
        }

        /* ---------- Trivy scan on the freshly-pushed image ---------- */
        stage('Trivy Image Scan') {
    steps {
        /* grab the Docker username so we know which image to scan */
        withCredentials([usernamePassword(credentialsId: 'docker-cred',
                                          usernameVariable: 'DOCKER_USER',
                                          passwordVariable: 'IGNORED')]) {

            /* 1) make sure the fancy HTML template exists */
            sh """
                if [ ! -f "${TRIVY_TEMPLATE}" ]; then
                  sudo mkdir -p \$(dirname "${TRIVY_TEMPLATE}")
                  sudo curl -sSL \
                    https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl \
                    -o "${TRIVY_TEMPLATE}"
                fi
            """

            /* 2) warm the DB */
            sh """
                mkdir -p "${TRIVY_CACHE_DIR}"
                trivy image \
                  --download-db-only \
                  --cache-dir "${TRIVY_CACHE_DIR}" \
                  --quiet
            """

            /* 3) real scan → HTML */
            sh """
                trivy image \\
                  --cache-dir "${TRIVY_CACHE_DIR}" \\
                  --scanners vuln \\
                  --severity HIGH,CRITICAL \\
                  --format template \\
                  --template "@${TRIVY_TEMPLATE}" \\
                  --timeout 15m \\
                  --exit-code 0 \\
                  -o trivy-image-report.html \\
                  "$DOCKER_USER/boardgame:${BUILD_NUMBER}"
            """
        }   /* withCredentials */
    }
    post {
        always {
            archiveArtifacts artifacts: 'trivy-image-report.html', fingerprint: true
            publishHTML target: [
                reportDir: '.',
                reportFiles: 'trivy-image-report.html',
                reportName: 'Trivy Image Scan',
                keepAll: true,
                alwaysLinkToLastBuild: true
            ]
        }
    }
}

        /* ---------- deploy ---------- */
        stage('Deploy to k8s') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh 'kubectl apply -f /var/lib/jenkins/k8s-manifest'
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh 'kubectl get pods -n ci-cd'
                    sh 'kubectl get svc -n ci-cd'
                }
            }
        }
    }

    /* ---------- post-pipeline ---------- */
    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            script {
                def authorEmail = sh(script: "git --no-pager show -s --format='%ae'",
                                     returnStdout: true).trim()
                mail to: authorEmail,
                     subject: "✅ Deployment Successful: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                     body: """
Hello,

Your commit triggered a successful deployment for job '${env.JOB_NAME}'
(build #${env.BUILD_NUMBER}).

You can view it here: ${env.BUILD_URL}

Best, Jenkins CI/CD
"""
            }
        }
    }
}

pipeline {
    agent any

    /* ───── toolchains ───── */
    tools {
        jdk   'Java 21'
        maven 'Maven 3.8.1'
    }

    /* ───── global env ───── */
    environment {
        JAVA_HOME    = tool 'Java 21'
        M2_HOME      = tool 'Maven 3.8.1'
        SCANNER_HOME = tool 'sonar-scanner'
        PATH         = "${JAVA_HOME}/bin:${M2_HOME}/bin:${SCANNER_HOME}/bin:${env.PATH}"
    }

    triggers { githubPush() }

    stages {

        stage('Checkout') {
            steps {
                git branch: 'main',
                    url:           'https://github.com/Sai-Roopesh/pipeline-test.git',
                    credentialsId: 'git-cred-test'
            }
        }

        stage('Build & Test') {
            steps {
                sh 'mvn clean verify'
            }
            post {
                success {
                    archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
                }
            }
        }

        stage('Smoke Test') {
            steps {
                sh '''
                    set -eu
                    PORT=15000
                    JAR=target/my-app-1.0.1.jar

                    if lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1; then
                      echo "Port $PORT busy – killing old process"
                      fuser -k ${PORT}/tcp || true
                      sleep 1
                    fi

                    PORT=$PORT java -jar "$JAR" &
                    PID=$!
                    trap "kill $PID" EXIT

                    for i in {1..15}; do
                      if curl -sf "http://localhost:$PORT" >/dev/null; then break; fi
                      sleep 1
                    done

                    curl -sf "http://localhost:$PORT" | grep "Hello, Jenkins!"
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    withSonarQubeEnv('sonar') {
                        sh """
                            sonar-scanner \
                              -Dsonar.projectKey=pipeline-test \
                              -Dsonar.sources=src/main/java \
                              -Dsonar.tests=src/test/java \
                              -Dsonar.java.binaries=target/classes \
                              -Dsonar.login=\\$SONAR_TOKEN
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

        stage('Build & Push Docker image') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-cred',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    script {
                        docker.withRegistry('', 'docker-cred') {
                            def img = docker.build("${DOCKER_USER}/boardgame:${BUILD_NUMBER}")
                            img.push()
                            img.push('latest')
                        }
                    }
                }
            }
        }

        stage('Trivy Config-Only Scan') {
            options {
                timeout(time: 30, unit: 'MINUTES')
            }
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-cred',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'IGNORED'
                )]) {
                    sh '''
                        trivy image \
                          --scanners misconfig \
                          --format table \
                          --timeout 30m \
                          --exit-code 0 \
                          -o trivy-misconfig-report.html \
                          "$DOCKER_USER/boardgame:${BUILD_NUMBER}"
                    '''
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: 'trivy-misconfig-report.html', fingerprint: true
                    publishHTML target: [
                        reportDir:   '.',
                        reportFiles:'trivy-misconfig-report.html',
                        reportName: 'Trivy Misconfig Scan',
                        keepAll:     true,
                        alwaysLinkToLastBuild: true
                    ]
                }
            }
        }

        stage('Render manifest') {
            steps {
                withCredentials([usernamePassword(
                    credentialsId: 'docker-cred',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'IGNORED'
                )]) {
                    sh '''
                        export IMG_TAG="$DOCKER_USER/boardgame:${BUILD_NUMBER}"
                        envsubst < /var/lib/jenkins/k8s-manifest/deployment.yaml \
                                 > rendered-deployment.yaml
                    '''
                }
                // <<< Moved inside steps so the stage’s braces stay balanced:
                archiveArtifacts artifacts: 'rendered-deployment.yaml', fingerprint: true
            }
        }

        stage('Deploy to k8s') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                        kubectl apply -f rendered-deployment.yaml --record
                        kubectl rollout status deployment/nginx-deployment --timeout=120s
                        kubectl rollout status deployment/nginx-deployment --timeout=1200s
                    '''
                }
            }
        }

        stage('Verify deployment') {
            steps {
                withKubeConfig(credentialsId: 'k8s-config') {
                    sh '''
                        echo "Verification Time: $(date +'%Y-%m-%d %H:%M:%S')"
                        kubectl get pods -l app=nginx \
                          -o custom-columns='NAME:.metadata.name,IMAGE:.spec.containers[*].image,READY:.status.containerStatuses[*].ready,START_TIME:.status.startTime' \
                          --no-headers
                        echo "Verification Time: $(date +'%Y-%m-%d %H:%M:%S')"
                        kubectl get svc nginx-service -o wide || true
                    '''
                }
            }
        }

    } // end stages

    post {
        always {
            junit testResults: 'target/surefire-reports/*.xml', allowEmptyResults: true
        }
        success {
            script {
                def email = sh(
                    script: "git --no-pager show -s --format='%ae'",
                    returnStdout: true
                ).trim()
                mail to:      email,
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
